package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 普通虚拟货币兑换策略。
 * <p>
 * 适用于输出为虚拟货币且采用固定数量的兑换规则。
 * 以规则中定义的固定数量乘以最大兑换次数得到基础总量，
 * 再应用出售价格加成（属性与节气）后累加到总虚拟货币数量。
 */
public class CoinSimpleStrategy implements ExchangeStrategy {

    /**
     * 执行普通虚拟货币兑换。
     * <p>
     * 使用固定数量计算基础总量，应用出售价格加成后累加结果。
     *
     * @param rule                 兑换规则
     * @param maxExchanges         最大兑换次数
     * @param level                世界实例
     * @param playerUUID           玩家 UUID
     * @param results              输出结果列表（本策略不直接写入）
     * @param totalVirtualCurrency 累计虚拟货币数量，执行后按加成结果累加
     */
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        // 普通虚拟货币模式：使用固定数量
        int baseCount = rule.getOutputItem().getCount() * maxExchanges;
        // 应用属性加成（属性 + 节气）
        int enhancedCount = ExchangeManager.applySellingPriceBoost(baseCount, rule, level, playerUUID);
        totalVirtualCurrency.addAndGet(enhancedCount);
    }
}