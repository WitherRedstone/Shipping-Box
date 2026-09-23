package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeRule;

/**
 * 兑换策略工厂。
 * <p>
 * 根据兑换规则的输出物品类型，选择并返回对应的兑换策略实现。
 * 先区分虚拟货币与普通物品，再按是否为动态定价、权重随机等模式细分。
 */
public class ExchangeStrategyFactory {

    /**
     * 根据兑换规则选择兑换策略。
     * <p>
     * 判定顺序为：先按是否为虚拟货币分流；
     * 虚拟货币下区分动态定价与普通模式；
     * 普通物品下依次区分动态定价、权重随机与普通模式。
     *
     * @param rule 兑换规则
     * @return 与规则匹配的兑换策略实现
     */
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