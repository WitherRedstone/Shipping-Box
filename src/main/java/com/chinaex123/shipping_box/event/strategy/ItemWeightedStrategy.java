package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 物品 + 权重随机兑换策略。
 * <p>
 * 适用于输出为权重随机物品的兑换规则。
 * 每次兑换独立按权重随机抽取一个物品，
 * 并对抽出的物品应用出售价格加成后写入输出列表。
 */
public class ItemWeightedStrategy implements ExchangeStrategy {

    /**
     * 执行物品 + 权重随机兑换。
     * <p>
     * 按最大兑换次数逐个独立抽取权重物品，
     * 对每个抽出的非空物品应用出售价格加成后加入输出列表。
     *
     * @param rule                 兑换规则
     * @param maxExchanges         最大兑换次数
     * @param level                世界实例
     * @param playerUUID           玩家 UUID
     * @param results              结果物品列表（输出）
     * @param totalVirtualCurrency 总虚拟货币数量（本策略不直接写入）
     */
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        // 权重模式：为每次兑换独立随机选择一个物品
        for (int i = 0; i < maxExchanges; i++) {
            ItemStack weightedOutput = rule.getOutputItem().getRandomWeightedItem();
            if (!weightedOutput.isEmpty()) {
                // 对权重选出的物品也应用属性加成
                int baseCount = weightedOutput.getCount();
                int enhancedCount = ExchangeManager.applySellingPriceBoost(baseCount, rule, level, playerUUID);
                weightedOutput.setCount(enhancedCount);
                results.add(weightedOutput);
            }
        }
    }
}