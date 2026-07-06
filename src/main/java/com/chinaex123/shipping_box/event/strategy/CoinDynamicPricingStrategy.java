package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.DynamicPricingManager;
import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 虚拟货币 + 动态定价 **/
public class CoinDynamicPricingStrategy implements ExchangeStrategy {
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        String itemIdentifier = rule.getInputs().getFirst().getItem();
        int resetDay = rule.getOutputItem().getDynamicProperties().getDay();
        int currentSoldCount = DynamicPricingManager.getSoldCount(itemIdentifier, resetDay);

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
        int enhancedCount = ExchangeManager.applySellingPriceBoost(totalVirtualCurrencyCount, rule, level, playerUUID);
        totalVirtualCurrency.addAndGet(enhancedCount);
    }
}
