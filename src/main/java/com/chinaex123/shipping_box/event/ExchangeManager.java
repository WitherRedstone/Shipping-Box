package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.attribute.ModAttributes;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
        boolean exchanged = false;

        // 执行兑换逻辑
        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(currentItems);

            if (rule != null) {
                int maxExchanges = getMaxExchanges(rule, currentItems);

                if (maxExchanges > 0) {
                    // 消耗输入物品
                    for (int i = 0; i < maxExchanges; i++) {
                        currentItems = ExchangeRecipeManager.consumeInputs(rule, currentItems);
                    }

                    // 生成输出物品
                    ItemStack output = rule.getOutputItem().getResultStack().copy();
                    int baseCount = rule.getOutputItem().getCount() * maxExchanges;
                    // 应用属性加成
                    int enhancedCount = applySellingPriceBoost(baseCount, level, boundPlayerUUID);
                    output.setCount(enhancedCount);
                    results.add(output);

                    exchanged = true;
                }
            }
        } while (exchanged);

        // 添加剩余物品
        results.addAll(currentItems);

        // 清空并重新填充
        Collections.fill(items, ItemStack.EMPTY);
        int slotIndex = 0;

        for (ItemStack result : results) {
            if (slotIndex >= items.size()) break;

            int maxStackSize = result.getMaxStackSize();
            int remainingCount = result.getCount();

            while (remainingCount > 0 && slotIndex < items.size()) {
                if (items.get(slotIndex).isEmpty()) {
                    int stackSize = Math.min(remainingCount, maxStackSize);
                    ItemStack newStack = result.copy();
                    newStack.setCount(stackSize);
                    items.set(slotIndex, newStack);
                    remainingCount -= stackSize;
                }
                slotIndex++;
            }
        }

        // 播放成功音效
        serverLevel.playSound(null, blockPos,
                SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                SoundSource.BLOCKS,
                0.5F, 1.0F);
    }

    /**
     * 为特定玩家应用出售价格属性加成
     */
    public static int applySellingPriceBoost(int baseCount, Level level, UUID playerUUID) {
        if (level == null || playerUUID == null) {
            return baseCount;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
        if (player == null) {
            return baseCount; // 玩家不在线，返回基础数量
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
