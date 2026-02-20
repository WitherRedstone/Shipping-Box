package com.chinaex123.shipping_box.tooltip;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 兑换工具提示提供器
 * 负责生成物品的兑换信息提示
 */
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
                if (input.matches(stack)) {
                    // 构建兑换信息文本
                    Component info = buildExchangeInfo(input, rule.getOutputItem());
                    exchangeInfo.add(info);

                    // 返回第一个匹配的规则信息
                    return new TooltipData(
                            exchangeInfo,
                            stack,
                            rule.getResultStack(),
                            input.getCount(),
                            rule.getOutputItem().getCount()
                    );
                }
            }
        }

        return null;
    }

    /**
     * 构建兑换信息文本
     * @param input 输入物品信息
     * @param output 输出物品信息
     * @return Component 格式化的兑换信息
     */
    private static Component buildExchangeInfo(ExchangeRule.InputItem input, ExchangeRule.OutputItem output) {
        // 创建绿色样式的文本
        Style greenStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x55FF55));

        // 构建输入部分：数量 + 物品名称
        Component inputPart = buildItemCountPair(input.getItem(), input.getCount());

        // 构建输出部分：数量 + 物品名称
        Component outputPart = buildItemCountPair(output.getItem(), output.getCount());

        // 返回格式化文本："[2个铁锭 → 2个苹果]"
        return Component.literal("[")
                .append(inputPart)
                .append(Component.translatable("tooltip.shipping_box.arrow"))
                .append(outputPart)
                .append(Component.literal("]"))
                .withStyle(greenStyle);
    }

    /**
     * 构建物品和数量的组合显示
     * @param itemIdentifier 物品标识符
     * @param count 数量
     * @return Component 格式化的显示
     */
    private static Component buildItemCountPair(String itemIdentifier, int count) {
        Component itemName = getLocalizedItemName(itemIdentifier);

        if (count == 1) {
            return itemName;
        }

        // 使用本地化的格式字符串
        return Component.translatable("tooltip.shipping_box.format",
                String.valueOf(count),
                itemName.getString());
    }

    /**
     * 获取物品的本地化名称
     * @param itemIdentifier 物品标识符 (namespace:id格式)
     * @return Component 本地化的物品名称
     */
    private static Component getLocalizedItemName(String itemIdentifier) {
        try {
            ResourceLocation itemId = ResourceLocation.parse(itemIdentifier);
            Item item = BuiltInRegistries.ITEM.get(itemId);

            if (item != null) {
                // 使用物品的getDescription方法获取本地化名称
                return item.getDescription();
            } else {
                // 如果物品不存在，返回原始标识符
                return Component.literal(itemIdentifier);
            }
        } catch (Exception e) {
            // 如果解析失败，返回原始标识符
            return Component.literal(itemIdentifier);
        }
    }
}
