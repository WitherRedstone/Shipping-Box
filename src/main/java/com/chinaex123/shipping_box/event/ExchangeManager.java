package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.api.ShippingBoxAPI;
import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.config.CommonConfig;
import com.chinaex123.shipping_box.event.strategy.ExchangeStrategy;
import com.chinaex123.shipping_box.event.strategy.ExchangeStrategyFactory;
import com.chinaex123.shipping_box.compat.EclipticSeasons.EclipticSeasonsUtil;
import com.chinaex123.shipping_box.compat.ViScriptShop.ViScriptShopUtil;
import com.chinaex123.shipping_box.network.PacketExchangeEffects;
import com.chinaex123.shipping_box.network.PacketShowSuccessMessage;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 兑换管理器；
 * 执行物品匹配、消耗和生成的核心兑换逻辑
 */
public class ExchangeManager {

    /**
     * 执行物品兑换的核心逻辑
     * @param items 物品存储列表
     * @param level 世界实例
     * @param blockPos 方块位置
     * @param boundPlayerUUID 绑定的玩家 UUID（可为 null）
     */
    public static void performExchange(NonNullList<ItemStack> items, Level level, BlockPos blockPos, UUID boundPlayerUUID) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ItemStack> initialItems = new ArrayList<>();
        NonNullList<ItemStack> initialSnapshot = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        List<ItemStack> currentItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            initialSnapshot.set(i, stack.copy());
            if (!stack.isEmpty()) {
                initialItems.add(stack.copy());
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
        ExchangeRule lastMatchedRule = null;

        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(currentItems);

            if (rule != null) {
                lastMatchedRule = rule;

                if (rule.getOutputItem().isCoin() && !ViScriptShopUtil.isAvailable()) {
                    return;
                }

                if (rule.getOutputItem().getEclipticSeasonsProperties() != null) {
                    var ecsProps = rule.getOutputItem().getEclipticSeasonsProperties();
                    if (ecsProps.isSeasonal_only() &&
                            !EclipticSeasonsUtil.isInSeasons(level, ecsProps.getSeason())) {
                        break;
                    }
                }

                int maxExchanges = getMaxExchanges(rule, currentItems);

                if (maxExchanges > 0) {
                    for (int i = 0; i < maxExchanges; i++) {
                        currentItems = ExchangeRecipeManager.consumeInputs(rule, currentItems);
                    }

                    ExchangeStrategy strategy = ExchangeStrategyFactory.getStrategy(rule);
                    AtomicInteger currencyWrapper = new AtomicInteger(totalVirtualCurrency);
                    strategy.execute(rule, maxExchanges, level, boundPlayerUUID, results, currencyWrapper);
                    totalVirtualCurrency = currencyWrapper.get();

                    exchanged = true;
                    hasValidExchange = true;
                }
            }
        } while (exchanged);

        if (hasValidExchange) {
            List<ItemStack> consumedItems = calculateConsumedItems(initialItems, currentItems);

            if (ShippingBoxAPI.onExchange(
                    null,
                    level,
                    createNonNullList(consumedItems),
                    createNonNullList(results),
                    totalVirtualCurrency,
                    lastMatchedRule)) {
                for (int i = 0; i < items.size(); i++) {
                    items.set(i, initialSnapshot.get(i).copy());
                }
                return;
            }

            if (totalVirtualCurrency > 0 && boundPlayerUUID != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                if (player != null && ViScriptShopUtil.isAvailable()) {
                    int currentBalance = ViScriptShopUtil.getMoney(player);
                    startBalanceAnimation(player, currentBalance, totalVirtualCurrency, 1);
                    ViScriptShopUtil.addMoney(player, totalVirtualCurrency);
                }
            }

            results.addAll(currentItems);

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

            Collections.fill(items, ItemStack.EMPTY);
            int slotIndex = 0;

            for (ItemStack result : stackedResults) {
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

            serverLevel.playSound(null, blockPos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);

            if (boundPlayerUUID != null) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, new PacketShowSuccessMessage());

                    if (CommonConfig.ENABLE_EXCHANGE_EFFECTS.get()) {
                        PacketDistributor.sendToPlayer(player, new PacketExchangeEffects(totalVirtualCurrency));
                    }
                }
            }

            if (CommonConfig.ENABLE_TRANSACTION_LOGGING.get()) {
                String playerName = "Unknown";
                if (boundPlayerUUID != null) {
                    ServerPlayer logPlayer = serverLevel.getServer().getPlayerList().getPlayer(boundPlayerUUID);
                    if (logPlayer != null) {
                        playerName = logPlayer.getName().getString();
                    }
                }
                TransactionLogger.logTransaction(playerName, consumedItems, results, totalVirtualCurrency, level, lastMatchedRule);
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
     * 支持负数属性（减少售价），最少为 0 个物品。
     *
     * @param baseCount 基础物品数量
     * @param level 游戏世界实例，用于获取服务器和玩家信息
     * @param playerUUID 玩家唯一标识符
     * @return 应用属性加成后的最终物品数量（最少为 0）
     */
    public static int applySellingPriceBoost(int baseCount, ExchangeRule rule, Level level, UUID playerUUID) {
        if (level == null || playerUUID == null) {
            return baseCount;
        }

        ServerPlayer player = level.getServer() != null ?
                level.getServer().getPlayerList().getPlayer(playerUUID) : null;
        if (player == null) {
            return baseCount;
        }

        int enhancedCount = baseCount;

        try {
            // 应用玩家属性加成
            double boost = player.getAttributeValue(ModAttributes.SELLING_PRICE_BOOST);

            if (boost != 0.0) {
                // 应用加成：基础数量 × (1 + 加成系数)
                double enhancedAmount = enhancedCount * (1.0 + boost);

                // 处理负数情况：如果结果小于 0，返回 0
                if (enhancedAmount < 0) {
                    enhancedCount = 0;
                } else {
                    // 智能取整：小于等于 5 向下取整，大于 5 向上取整
                    if (enhancedAmount <= 5.0) {
                        enhancedCount = (int) Math.floor(enhancedAmount);
                    } else {
                        enhancedCount = (int) Math.ceil(enhancedAmount);
                    }
                }
            }

            // 如果有兑换规则，检查是否需要应用节气加成/减益
            if (rule != null && rule.getOutputItem() != null &&
                    rule.getOutputItem().getEclipticSeasonsProperties() != null) {

                var ecsProps = rule.getOutputItem().getEclipticSeasonsProperties();

                // 检查节气模组是否可用
                if (EclipticSeasonsUtil.isAvailable()) {
                    // 获取当前季节并检查是否在目标季节列表中
                    boolean isInSeason = EclipticSeasonsUtil.isInSeasons(level, ecsProps.getSeason());

                    if (isInSeason) {
                        // 应季：应用加成（价格更高）
                        int bonus = ecsProps.getAdd_season_bonus();
                        if (bonus > 0) {
                            double seasonEnhancedAmount = enhancedCount * (1.0 + (bonus / 100.0));
                            enhancedCount = (int) Math.ceil(seasonEnhancedAmount);
                        }
                    } else {
                        // 非应季：应用减益（价格更低）
                        int penalty = ecsProps.getReduce_season_bonus();
                        if (penalty > 0) {
                            double reducedAmount = enhancedCount * (1.0 - (penalty / 100.0));
                            enhancedCount = Math.max(0, (int) Math.floor(reducedAmount));
                        }
                    }
                }
            }

            return enhancedCount;
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

    /**
     * 计算实际消耗的物品
     */
    private static List<ItemStack> calculateConsumedItems(List<ItemStack> initialItems, List<ItemStack> remainingItems) {
        List<ItemStack> consumed = new ArrayList<>();

        // 创建剩余物品的深拷贝列表，用于模拟扣除过程
        List<ItemStack> remainingCopy = new ArrayList<>();
        for (ItemStack stack : remainingItems) {
            remainingCopy.add(stack.copy());
        }

        for (ItemStack initStack : initialItems) {
            ItemStack stackToProcess = initStack.copy();
            int originalCount = stackToProcess.getCount();
            int currentRemaining = 0; // 初始为 0，表示还未找到剩余

            // 在剩余物品中寻找匹配项并扣除
            for (ItemStack remStack : remainingCopy) {
                if (ItemStack.isSameItemSameComponents(stackToProcess, remStack)) {
                    // 累加剩余数量
                    currentRemaining += remStack.getCount();

                    // 如果已经找到所有剩余，提前退出
                    if (currentRemaining >= originalCount) break;
                }
            }

            // 消耗量 = 原始数量 - 剩余数量
            int consumedCount = originalCount - currentRemaining;

            if (consumedCount > 0) {
                ItemStack consumedStack = stackToProcess.copy();
                consumedStack.setCount(consumedCount);
                consumed.add(consumedStack);
            }
        }

        // 合并相同的消耗物品
        List<ItemStack> mergedConsumed = new ArrayList<>();
        for (ItemStack stack : consumed) {
            boolean merged = false;
            for (ItemStack existing : mergedConsumed) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                mergedConsumed.add(stack);
            }
        }

        return mergedConsumed;
    }

    /**
     * 辅助方法：将 List 转换为 NonNullList
     */
    private static NonNullList<ItemStack> createNonNullList(List<ItemStack> list) {
        NonNullList<ItemStack> result = NonNullList.create();
        result.addAll(list);
        return result;
    }
}
