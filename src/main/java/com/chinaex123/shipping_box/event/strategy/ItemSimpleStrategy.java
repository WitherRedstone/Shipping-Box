package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 普通物品兑换策略。
 * <p>
 * 适用于输出为普通物品且采用固定数量的兑换规则，
 * 包括输出类型为 null 或 "item" 的情况。
 * 以规则中定义的固定数量乘以最大兑换次数得到基础总量，
 * 再应用出售价格加成后生成最终输出物品。
 */
public class ItemSimpleStrategy implements ExchangeStrategy {

    /**
     * 执行普通物品兑换。
     * <p>
     * 按固定数量计算基础总量，应用出售价格加成后写入输出物品列表。
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
        // 普通物品模式
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