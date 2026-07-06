package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeRule;

/** 物品 + 动态定价 **/
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
