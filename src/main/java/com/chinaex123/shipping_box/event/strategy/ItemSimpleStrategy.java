package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 普通物品兑换策略
 * <p>
 * 处理最简单的物品兑换逻辑：输入物品按固定比例兑换为输出物品。
 * 使用配置中指定的固定输出数量乘以兑换次数，并应用属性加成后生成结果物品。
 * 适用于无动态定价、无权重的简单物品兑换场景。
 */
public class ItemSimpleStrategy implements ExchangeStrategy {
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        // 普通物品模式 - 处理 type 为 null 或 "item" 的情况
        ItemStack output = rule.getOutputItem().getResultStack().copy();
        if (!output.isEmpty()) {
            int baseCount = rule.getOutputItem().getCount() * maxExchanges;
            // 应用属性加成
            int enhancedCount = ExchangeManager.applySellingPriceBoost(baseCount, rule, level, playerUUID);
            output.setCount(enhancedCount);
            results.add(output);
        }
    }
}
