package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeRule;

/**
 * 兑换策略工厂
 * <p>
 * 根据兑换规则的输出配置，动态选择合适的策略实现类。
 * 支持以下策略类型：
 * <ul>
 *   <li>普通物品策略（ItemSimpleStrategy）</li>
 *   <li>物品动态定价策略（ItemDynamicPricingStrategy）</li>
 *   <li>物品权重随机策略（ItemWeightedStrategy）</li>
 *   <li>普通虚拟货币策略（CoinSimpleStrategy）</li>
 *   <li>虚拟货币动态定价策略（CoinDynamicPricingStrategy）</li>
 * </ul>
 * 使用策略模式封装不同的兑换计算逻辑，便于扩展和维护。
 */
public class ExchangeStrategyFactory {
    public static ExchangeStrategy getStrategy(ExchangeRule rule) {
        ExchangeRule.OutputItem output = rule.getOutputItem();
        if (output.isCoin()) {
            if ("dynamic_pricing".equals(output.getType()) && output.getDynamicProperties() != null) {
                return new CoinDynamicPricingStrategy();
            } else {
                return new CoinSimpleStrategy();
            }
        } else {
            if ("dynamic_pricing".equals(output.getType()) && output.getDynamicProperties() != null) {
                return new ItemDynamicPricingStrategy();
            } else if ("weight".equals(output.getType()) && output.getItems() != null && !output.getItems().isEmpty()) {
                return new ItemWeightedStrategy();
            } else {
                return new ItemSimpleStrategy();
            }
        }
    }
}
