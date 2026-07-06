package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 普通虚拟货币兑换策略
 * <p>
 * 处理虚拟货币（如苦力怕硬币、点数等）的固定数量兑换逻辑。
 * 使用配置的固定数量乘以兑换次数，并应用属性加成后累加到总虚拟货币中。
 * 适用于无动态定价的简单虚拟货币兑换场景。
 */
public class CoinSimpleStrategy implements ExchangeStrategy {
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        // 普通虚拟货币模式：使用固定数量
        int baseCount = rule.getOutputItem().getCount() * maxExchanges;
        // 应用属性加成（属性 + 节气）
        int enhancedCount = ExchangeManager.applySellingPriceBoost(baseCount, rule, level, playerUUID);
        totalVirtualCurrency.addAndGet(enhancedCount);
    }
}
