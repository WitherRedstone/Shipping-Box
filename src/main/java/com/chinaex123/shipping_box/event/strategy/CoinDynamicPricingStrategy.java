package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.DynamicPricingManager;
import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 虚拟货币 + 动态定价兑换策略。
 * <p>
 * 适用于输出为虚拟货币且采用动态定价的兑换规则。
 * 虚拟货币模式下每次兑换即一个单位，单价随累计售出数量递增，
 * 本策略会按顺序为每个单位分别计算单价并求和，
 * 再应用玩家的出售价格属性加成后累加到总虚拟货币数量。
 */
public class CoinDynamicPricingStrategy implements ExchangeStrategy {

    /**
     * 执行虚拟货币 + 动态定价兑换。
     * <p>
     * 逐个单位为基准计算动态单价并累加，随后更新累计售出数量，
     * 最后应用出售价格加成并将结果累加到总虚拟货币数量。
     *
     * @param rule                兑换规则
     * @param maxExchanges        最大兑换次数
     * @param level               世界实例
     * @param playerUUID          玩家 UUID
     * @param results             输出结果列表（本策略不直接写入）
     * @param totalVirtualCurrency 累计虚拟货币数量，执行后按加成结果累加
     */
    @Override
    public void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency) {
        String itemIdentifier = rule.getInputs().getFirst().getItem();
        int resetDay = rule.getOutputItem().getDynamicProperties().getDay();
        int currentSoldCount = DynamicPricingManager.getSoldCount(itemIdentifier, resetDay);

        int totalVirtualCurrencyCount = 0;

        for (int i = 0; i < maxExchanges; i++) {
            // 为每个单位单独计算基于当前累计数量的单价
            int dynamicCount = rule.getOutputItem().getDynamicCount(currentSoldCount + i);
            totalVirtualCurrencyCount += dynamicCount;
        }

        // 更新累计售出数量
        DynamicPricingManager.addSoldCount(itemIdentifier, maxExchanges, resetDay);

        // 应用属性加成到总数量
        int enhancedCount = ExchangeManager.applySellingPriceBoost(totalVirtualCurrencyCount, rule, level, playerUUID);
        totalVirtualCurrency.addAndGet(enhancedCount);
    }
}