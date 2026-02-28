package com.chinaex123.shipping_box.tooltip;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import com.chinaex123.shipping_box.modCompat.ViScriptShop.ViScriptShopUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ExchangeTooltipProvider {

    /**
     * 获取物品的兑换提示信息
     *
     * @param stack 要检查的物品堆
     * @return 兑换提示数据，如果不支持兑换则返回null
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
                        // 检查输出是否为虚拟货币且模组可用
                        ExchangeRule.OutputItem output = rule.getOutputItem();
                        if (output.isCoin() && !ViScriptShopUtil.isAvailable()) {
                            // 虚拟货币模式但模组未加载，跳过此规则
                            continue;
                        }

                        // 构建兑换信息文本
                        Component info = buildExchangeInfo(input, output);
                        exchangeInfo.add(info);

                        // 返回第一个匹配的规则信息
                        return new TooltipData(
                                exchangeInfo,
                                stack,
                                output.getResultStack(),
                                input.getCount(),
                                output.getCount()
                        );
                    }
                } catch (Exception e) {
                    // 静默处理匹配过程中的异常，避免崩溃
                }
            }
        }

        return null;
    }

    /**
     * 构建兑换信息的显示文本
     * 格式："[输入数量 输入物品名称 → 输出数量 输出物品名称]"
     * 虚拟货币格式："[输入数量 输入物品名称 → 输出货币数量]"
     *
     * @param input 输入物品信息
     * @param output 输出物品信息
     * @return 格式化的兑换信息组件
     */
    private static Component buildExchangeInfo(ExchangeRule.InputItem input, ExchangeRule.OutputItem output) {
        try {
            // 获取输入物品的本地化名称
            Component inputName = getLocalizedItemName(input);

            // 检查是否为虚拟货币模式
            if (output.isCoin()) {
                // 虚拟货币使用简化格式："[输入数量 输入物品名称 → 货币数量]"
                Component currencyText = getLocalizedItemName(output);
                return Component.translatable("tooltip.shipping_box.exchange_format_simple",
                                input.getCount(),
                                inputName,
                                currencyText)
                        .withStyle(ChatFormatting.GOLD);
            } else {
                // 普通物品使用原有格式："[输入数量 输入物品名称 → 输出数量 输出物品名称]"
                Component outputName = getLocalizedItemName(output);
                return Component.translatable("tooltip.shipping_box.exchange_format",
                                input.getCount(),
                                inputName,
                                output.getCount(),
                                outputName)
                        .withStyle(ChatFormatting.GOLD);
            }
        } catch (Exception e) {
            // 如果构建失败，返回简单的带方括号文本
            return Component.literal("[" + input.getCount() + " items → " + output.getCount() + " items]").withStyle(ChatFormatting.RED);
        }
    }

    /**
     * 获取输入物品的本地化显示名称
     * 支持标签和物品ID两种类型的名称解析
     *
     * @param input 输入物品对象
     * @return 物品的本地化名称组件
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
     * 获取输出物品的本地化显示名称
     *
     * @param output 输出物品对象
     * @return 物品的本地化名称组件
     */
    private static Component getLocalizedItemName(ExchangeRule.OutputItem output) {
        try {
            // 检查是否为虚拟货币模式
            if (output.isCoin()) {
                // 虚拟货币模式下显示货币数量
                return Component.translatable("tooltip.shipping_box.virtual_currency", output.getCount())
                        .withStyle(ChatFormatting.GOLD);
            }

            if (output.getItem() != null && !output.getItem().isEmpty()) {
                return getLocalizedItemName(output.getItem());
            }
        } catch (Exception e) {
            // 异常处理
        }
        return Component.translatable("tooltip.shipping_box.unknown_item").withStyle(ChatFormatting.RED);
    }

    /**
     * 根据物品标识符获取本地化名称
     *
     * @param itemIdentifier 物品标识符字符串
     * @return 物品的本地化名称组件，如果找不到则返回标识符本身
     */
    private static Component getLocalizedItemName(String itemIdentifier) {
        try {
            ResourceLocation itemId = ResourceLocation.parse(itemIdentifier);
            Item item = BuiltInRegistries.ITEM.get(itemId);

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
