package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.modCompat.ViScriptShop.ViScriptShopUtil;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;

public class ExchangeManager {

    /**
     * 执行物品兑换的核心逻辑
     * @param items 物品存储列表
     * @param level 世界实例
     * @param blockPos 方块位置
     * @param boundPlayerUUID 绑定的玩家UUID（可为null）
     */
    public static void performExchange(NonNullList<ItemStack> items, Level level, BlockPos blockPos, UUID boundPlayerUUID) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ItemStack> currentItems = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                currentItems.add(stack.copy());
            }
        }

        if (currentItems.isEmpty()) {
            return;
        }

        List<ItemStack> results = new ArrayList<>();
        boolean exchanged; // 标记是否已发生兑换
        int totalVirtualCurrency = 0; // 记录总的虚拟货币数量
        boolean hasValidExchange = false; // 标记是否有有效的兑换发生

        // 执行兑换逻辑
        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(currentItems);

            if (rule != null) {
                // 检查如果是虚拟货币兑换但模组未加载，则完全跳过处理
                if (rule.getOutputItem().isCoin() && !ViScriptShopUtil.isAvailable()) {
                    return; // 直接返回，不处理任何物品
                }

                int maxExchanges = getMaxExchanges(rule, currentItems);

                if (maxExchanges > 0) {
                    // 消耗输入物品
                    for (int i = 0; i < maxExchanges; i++) {
                        currentItems = ExchangeRecipeManager.consumeInputs(rule, currentItems);
                    }

                    // 处理输出
                    if (rule.getOutputItem().isCoin()) {
                        // 虚拟货币模式：累加货币数量，应用属性加成
                        int baseCount = rule.getOutputItem().getCount() * maxExchanges;
                        // 应用属性加成
                        int enhancedCount = applySellingPriceBoost(baseCount, level, boundPlayerUUID);
                        totalVirtualCurrency += enhancedCount;
                    } else {
                        // 普通物品模式：生成输出物品
                        ItemStack output = rule.getOutputItem().getResultStack().copy();

                        int baseCount = rule.getOutputItem().getCount() * maxExchanges;
                        // 应用属性加成
                        int enhancedCount = applySellingPriceBoost(baseCount, level, boundPlayerUUID);
                        output.setCount(enhancedCount);
                        results.add(output);
                    }

                    exchanged = true;
                    hasValidExchange = true; // 标记发生了有效兑换
                }
            }
        } while (exchanged);

        // 如果有虚拟货币要添加
        if (totalVirtualCurrency > 0 && boundPlayerUUID != null) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
            if (player != null && ViScriptShopUtil.isAvailable()) {
                // 获取玩家当前余额并开始动画
                int currentBalance = ViScriptShopUtil.getMoney(player);
                startBalanceAnimation(player, currentBalance, totalVirtualCurrency, 1);

                ViScriptShopUtil.addMoney(player, totalVirtualCurrency);
            }
        }

        // 只有当确实有有效兑换发生时才处理物品和播放音效
        if (hasValidExchange) {
            // 添加剩余物品（仅限普通物品）
            results.addAll(currentItems);

            // 清空并重新填充
            Collections.fill(items, ItemStack.EMPTY);
            int slotIndex = 0;

            for (ItemStack result : results) {
                if (slotIndex >= items.size()) break;

                int maxStackSize = result.getMaxStackSize();
                int remainingCount = result.getCount();

                // 遍历所有槽位，将物品按最大堆叠数分配到各个槽位中
                while (remainingCount > 0 && slotIndex < items.size()) {
                    // 检查当前槽位是否为空
                    if (items.get(slotIndex).isEmpty()) {
                        // 计算当前槽位应该放置的数量（取剩余数量和最大堆叠数中的较小值）
                        int stackSize = Math.min(remainingCount, maxStackSize);
                        ItemStack newStack = result.copy(); // 创建新的物品堆栈副本
                        newStack.setCount(stackSize); // 设置该堆栈的数量
                        items.set(slotIndex, newStack); // 将物品放入当前槽位
                        remainingCount -= stackSize; // 减去已分配的数量
                    }
                    // 移动到下一个槽位
                    slotIndex++;
                }
            }

            // 播放成功音效
            serverLevel.playSound(null, blockPos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);
        }
    }

    /**
     * 开始余额动画
     */
    private static void startBalanceAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        BalanceAnimationManager.startAnimation(player, startBalance, totalValue, exchangeAmount);
    }

    /**
     * 为特定玩家应用出售价格属性加成
     */
    public static int applySellingPriceBoost(int baseCount, Level level, UUID playerUUID) {
        if (level == null || playerUUID == null) {
            return baseCount;
        }

        ServerPlayer player = level.getServer() != null ?
                level.getServer().getPlayerList().getPlayer(playerUUID) : null;
        if (player == null) {
            return baseCount; // 玩家不在线或服务器不可用，返回基础数量
        }

        try {
            // 获取该玩家的出售价格属性加成
            double boost = player.getAttributeValue(ModAttributes.SELLING_PRICE_BOOST);

            if (boost <= 0.0) {
                return baseCount;
            }

            // 应用加成：基础数量 × (1 + 加成系数)
            double enhancedAmount = baseCount * (1.0 + boost);

            // 智能取整：小于等于5向下取整，大于5向上取整
            if (enhancedAmount <= 5.0) {
                return (int) Math.floor(enhancedAmount);
            } else {
                return (int) Math.ceil(enhancedAmount);
            }
        } catch (Exception e) {
            return baseCount;
        }
    }

    /**
     * 计算指定兑换规则可以执行的最大兑换次数（多物品兑换）
     */
    public static int getMaxExchanges(ExchangeRule rule, List<ItemStack> availableStacks) {
        int maxExchanges = Integer.MAX_VALUE;

        for (ExchangeRule.InputItem required : rule.getInputs()) {
            int totalCount = 0;
            for (ItemStack stack : availableStacks) {
                if (required.matches(stack)) {
                    totalCount += stack.getCount();
                }
            }

            int possibleExchanges = totalCount / required.getCount();
            if (possibleExchanges < maxExchanges) {
                maxExchanges = possibleExchanges;
            }
        }

        return maxExchanges;
    }
}
