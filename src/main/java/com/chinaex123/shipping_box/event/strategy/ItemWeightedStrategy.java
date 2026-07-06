package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 物品权重随机兑换策略
 * <p>
 * 根据配置的权重随机选择输出物品。每次兑换独立进行随机选择，
 * 权重高的物品被选中的概率更大。支持对选中物品应用属性加成。
 * 适用于抽奖、随机奖励等需要概率机制的兑换场景。
 */
public class ItemWeightedStrategy implements ExchangeStrategy {
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
