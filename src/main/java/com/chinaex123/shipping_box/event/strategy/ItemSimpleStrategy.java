package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 普通物品 **/
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
