package com.chinaex123.shipping_box.tooltip;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ExchangeTooltipProvider {

    /**
     * 获取物品的兑换信息
     * @param stack 要检查的物品堆
     * @return TooltipData 包含兑换信息的数据对象
     */
    public static TooltipData getExchangeTooltip(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        List<ExchangeRule> rules = ExchangeRecipeManager.getRules();
        List<Component> exchangeInfo = new ArrayList<>();

        for (ExchangeRule rule : rules) {
            // 检查该物品是否是某个兑换规则的输入物品
            for (ExchangeRule.InputItem input : rule.getInputs()) {
                try {
                    if (input.matches(stack)) {
                        // 构建兑换信息文本
                        Component info = buildExchangeInfo(input, rule.getOutputItem());
                        exchangeInfo.add(info);

                        // 返回第一个匹配的规则信息
                        return new TooltipData(
                                exchangeInfo,
                                stack,
                                rule.getOutputItem().getResultStack(),
                                input.getCount(),
                                rule.getOutputItem().getCount()
                        );
                    }
                } catch (Exception e) {
                    // 静默处理匹配过程中的异常，避免崩溃
                    continue;
                }
            }
        }

        return null;
    }

    /**
     * 构建兑换信息文本
     */
    private static Component buildExchangeInfo(ExchangeRule.InputItem input, ExchangeRule.OutputItem output) {
        try {
            // 获取输入物品的本地化名称
            Component inputName = getLocalizedItemName(input);
            // 获取输出物品的本地化名称
            Component outputName = getLocalizedItemName(output);

            // 构建带方括号的格式："[输入数量 输入物品名称 → 输出数量 输出物品名称]"
            return Component.translatable("tooltip.shipping_box.exchange_format",
                    input.getCount(),
                    inputName,
                    output.getCount(),
                    outputName)
                    .withStyle(ChatFormatting.GOLD);
        } catch (Exception e) {
            // 如果构建失败，返回简单的带方括号文本
            return Component.literal("[" + input.getCount() + " items → " + output.getCount() + " items]").withStyle(ChatFormatting.RED);
        }
    }

    /**
     * 获取输入物品的本地化名称（支持标签和物品ID）
     */
    private static Component getLocalizedItemName(ExchangeRule.InputItem input) {
        try {
            // 如果是标签
            if (input.getTag() != null && !input.getTag().isEmpty()) {
                // 对于标签，显示标签名称（可以进一步优化）
                String tagName = input.getTag();
                if (tagName.startsWith("#")) {
                    tagName = tagName.substring(1);
                }
                return Component.literal("#" + tagName);
            }
            // 如果是物品ID
            else if (input.getItem() != null && !input.getItem().isEmpty()) {
                return getLocalizedItemName(input.getItem());
            }
        } catch (Exception e) {
            // 异常处理
        }
        return Component.translatable("tooltip.shipping_box.unknown_item").withStyle(ChatFormatting.RED);
    }

    /**
     * 获取输出物品的本地化名称
     */
    private static Component getLocalizedItemName(ExchangeRule.OutputItem output) {
        try {
            if (output.getItem() != null && !output.getItem().isEmpty()) {
                return getLocalizedItemName(output.getItem());
            }
        } catch (Exception e) {
            // 异常处理
        }
        return Component.translatable("tooltip.shipping_box.unknown_item").withStyle(ChatFormatting.RED);
    }

    /**
     * 根据物品ID获取本地化名称
     */
    private static Component getLocalizedItemName(String itemIdentifier) {
        try {
            net.minecraft.resources.ResourceLocation itemId = net.minecraft.resources.ResourceLocation.parse(itemIdentifier);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);

            if (item != null) {
                return item.getDescription();
            } else {
                return Component.literal(itemIdentifier);
            }
        } catch (Exception e) {
            return Component.literal(itemIdentifier);
        }
    }
}
