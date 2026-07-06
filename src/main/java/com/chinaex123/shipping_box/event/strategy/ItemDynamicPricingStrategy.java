package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.DynamicPricingManager;
import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 物品 + 动态定价 **/
public class ItemDynamicPricingStrategy implements ExchangeStrategy {
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
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
            int enhancedCount = ExchangeManager.applySellingPriceBoost(totalOutputCount, rule, level, playerUUID);
            output.setCount(enhancedCount);
            results.add(output);
        }
    }
}
