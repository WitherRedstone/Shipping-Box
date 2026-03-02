package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.modCompat.ViScriptShop.ViScriptShopUtil;
import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

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
        boolean exchanged;
        int totalVirtualCurrency = 0;
        boolean hasValidExchange = false;

        // 执行兑换逻辑
        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(currentItems);

            if (rule != null) {
                // 检查如果是虚拟货币兑换但模组未加载，则完全跳过处理
                if (rule.getOutputItem().isCoin() && !ViScriptShopUtil.isAvailable()) {
                    return;
                }

                int maxExchanges = getMaxExchanges(rule, currentItems);

                if (maxExchanges > 0) {
                    // 消耗输入物品
                    for (int i = 0; i < maxExchanges; i++) {
                        currentItems = ExchangeRecipeManager.consumeInputs(rule, currentItems);
                    }

                    // 处理输出 - 重新排列条件判断顺序
                    if (rule.getOutputItem().isCoin() &&
                            "dynamic_pricing".equals(rule.getOutputItem().getType()) &&
                            rule.getOutputItem().getDynamicProperties() != null) {

                        // 动态定价+虚拟货币模式
                        // 修复：使用输入物品作为标识符
                        String itemIdentifier = rule.getInputs().getFirst().getItem();

                        // 获取重置天数配置
                        int resetDay = rule.getOutputItem().getDynamicProperties().getDay();

                        // 获取当前累计售出数量（使用带重置天数的版本）
                        int currentSoldCount = DynamicPricingManager.getSoldCount(itemIdentifier, resetDay);

                        // 逐个物品计算虚拟货币数量（累计阈值机制）
                        int totalVirtualCurrencyCount = 0;
                        int itemsToProcess = maxExchanges; // 虚拟货币模式下每次兑换就是1个单位

                        for (int i = 0; i < itemsToProcess; i++) {
                            // 为每个单位单独计算基于当前累计数量的单价
                            int dynamicCount = rule.getOutputItem().getDynamicCount(currentSoldCount + i);
                            totalVirtualCurrencyCount += dynamicCount;
                        }

                        // 更新累计售出数量（增加这一批的数量）
                        DynamicPricingManager.addSoldCount(itemIdentifier, itemsToProcess, resetDay);

                        // 应用属性加成到总数量
                        int enhancedCount = applySellingPriceBoost(totalVirtualCurrencyCount, level, boundPlayerUUID);
                        totalVirtualCurrency += enhancedCount;

                    } else if (rule.getOutputItem().isCoin()) {
                        // 普通虚拟货币模式：使用固定数量
                        int baseCount = rule.getOutputItem().getCount() * maxExchanges;
                        // 应用属性加成
                        int enhancedCount = applySellingPriceBoost(baseCount, level, boundPlayerUUID);
                        totalVirtualCurrency += enhancedCount;

                    } else if ("dynamic_pricing".equals(rule.getOutputItem().getType()) &&
                            rule.getOutputItem().getDynamicProperties() != null) {
                        // 动态定价模式处理 - 逐个物品计算以支持跨阈值
                        String itemIdentifier = rule.getOutputItem().getItem();

                        // 获取重置天数配置
                        int resetDay = rule.getOutputItem().getDynamicProperties().getDay();

                        // 获取当前累计售出数量（使用带重置天数的版本）
                        int currentSoldCount = DynamicPricingManager.getSoldCount(itemIdentifier, resetDay);

                        // 逐个物品计算输出数量
                        int totalOutputCount = 0;
                        int itemsToProcess = rule.getOutputItem().getCount() * maxExchanges;

                        for (int i = 0; i < itemsToProcess; i++) {
                            // 为每个物品单独计算基于当前累计数量的单价
                            int dynamicCount = rule.getOutputItem().getDynamicCount(currentSoldCount + i);
                            totalOutputCount += dynamicCount;
                        }

                        // 更新累计售出数量（增加这一批的数量）
                        DynamicPricingManager.addSoldCount(itemIdentifier, itemsToProcess, resetDay);

                        // 生成输出物品
                        ItemStack output = rule.getOutputItem().getResultStack().copy();
                        if (!output.isEmpty()) {
                            // 应用属性加成
                            int enhancedCount = applySellingPriceBoost(totalOutputCount, level, boundPlayerUUID);
                            output.setCount(enhancedCount);
                            results.add(output);
                        }
                    } else if ("weight".equals(rule.getOutputItem().getType()) &&
                            rule.getOutputItem().getItems() != null &&
                            !rule.getOutputItem().getItems().isEmpty()) {
                        // 权重模式：为每次兑换独立随机选择一个物品
                        for (int i = 0; i < maxExchanges; i++) {
                            ItemStack weightedOutput = rule.getOutputItem().getRandomWeightedItem();
                            if (!weightedOutput.isEmpty()) {
                                // 对权重选出的物品也应用属性加成
                                int baseCount = weightedOutput.getCount();
                                int enhancedCount = applySellingPriceBoost(baseCount, level, boundPlayerUUID);
                                weightedOutput.setCount(enhancedCount);
                                results.add(weightedOutput);
                            }
                        }
                    } else {
                        // 普通物品模式 - 处理 type 为 null 或 "item" 的情况
                        ItemStack output = rule.getOutputItem().getResultStack().copy();
                        if (!output.isEmpty()) {
                            int baseCount = rule.getOutputItem().getCount() * maxExchanges;
                            int enhancedCount = applySellingPriceBoost(baseCount, level, boundPlayerUUID);
                            output.setCount(enhancedCount);
                            results.add(output);
                        }
                    }

                    exchanged = true;
                    hasValidExchange = true;
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
            // 添加剩余物品
            results.addAll(currentItems);

            // 统一堆叠处理 - 合并相同物品
            List<ItemStack> stackedResults = new ArrayList<>();
            for (ItemStack stack : results) {
                if (stack.isEmpty()) continue;

                boolean merged = false;
                for (ItemStack existingStack : stackedResults) {
                    if (ItemStack.isSameItemSameComponents(existingStack, stack)) {
                        int maxStackSize = existingStack.getMaxStackSize();
                        int spaceAvailable = maxStackSize - existingStack.getCount();
                        int amountToMerge = Math.min(stack.getCount(), spaceAvailable);

                        if (amountToMerge > 0) {
                            existingStack.grow(amountToMerge);
                            stack.shrink(amountToMerge);
                            merged = true;

                            if (stack.isEmpty()) {
                                break;
                            }
                        }
                    }
                }

                if (!stack.isEmpty()) {
                    stackedResults.add(stack);
                }
            }

            // 清空并重新填充
            Collections.fill(items, ItemStack.EMPTY);
            int slotIndex = 0;

            for (ItemStack result : stackedResults) {
                if (slotIndex >= items.size()) break;

                int maxStackSize = result.getMaxStackSize();
                int remainingCount = result.getCount();

                // 遍历所有槽位，将物品按最大堆叠数分配到各个槽位中
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

            // 发送兑换成功消息到绑定玩家
            if (boundPlayerUUID != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                if (player != null) {
                    // 发送成功提示消息包
                    ShippingBoxNetworking.ShowSuccessMessage successPacket = new ShippingBoxNetworking.ShowSuccessMessage();
                    PacketDistributor.sendToPlayer(player, successPacket);
                }
            }
        }
    }

    /**
     * 开始余额动画
     */
    private static void startBalanceAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        BalanceAnimationManager.startAnimation(player, startBalance, totalValue, exchangeAmount);
    }

    /**
     * 应用玩家出售价格属性加成到基础数量
     * <p>
     * 根据玩家的出售价格属性加成值，计算增强后的物品数量。
     * 采用智能取整策略：小数量向下取整保证平衡，大数量向上取整激励玩家。
     *
     * @param baseCount 基础物品数量
     * @param level 游戏世界实例，用于获取服务器和玩家信息
     * @param playerUUID 玩家唯一标识符
     * @return 应用属性加成后的最终物品数量
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
            int result;
            if (enhancedAmount <= 5.0) {
                result = (int) Math.floor(enhancedAmount);
            } else {
                result = (int) Math.ceil(enhancedAmount);
            }

            return result;
        } catch (Exception e) {
            return baseCount;
        }
    }

    /**
     * 计算指定兑换规则可以执行的最大兑换次数
     * <p>
     * 通过检查每种输入物品的可用数量来确定限制因素，返回能够完成的最多兑换轮数。
     * 算法找出所有必需物品中最紧缺的那种，以其可支持的兑换次数作为整体上限。
     *
     * @param rule 兑换规则，包含所需的输入物品列表及其数量要求
     * @param availableStacks 当前可用的物品堆列表
     * @return 可以执行的最大兑换次数，如果无法兑换则返回0
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
