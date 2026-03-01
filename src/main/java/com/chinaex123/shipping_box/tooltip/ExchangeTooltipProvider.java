package com.chinaex123.shipping_box.tooltip;

import com.chinaex123.shipping_box.event.DynamicPricingManager;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import com.chinaex123.shipping_box.modCompat.ViScriptShop.ViScriptShopUtil;
import com.chinaex123.shipping_box.network.ClientSoldCountCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

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
        List<Component> additionalLines = new ArrayList<>();

        for (ExchangeRule rule : rules) {
            for (ExchangeRule.InputItem input : rule.getInputs()) {
                try {
                    if (input.matches(stack)) {
                        ExchangeRule.OutputItem output = rule.getOutputItem();
                        if (output.isCoin() && !ViScriptShopUtil.isAvailable()) {
                            continue;
                        }

                        // 构建主要的兑换信息
                        Component mainInfo = buildMainExchangeInfo(input, output, rule);
                        exchangeInfo.add(mainInfo);

                        // 为动态定价模式构建额外的重置信息行
                        if ("dynamic_pricing".equals(output.getType()) && output.getDynamicProperties() != null) {
                            Component resetInfo = buildResetInfoLine(output, rule);
                            if (resetInfo != null) {
                                additionalLines.add(resetInfo);
                            }
                        }

                        return new TooltipData(
                                exchangeInfo,
                                stack,
                                output.getResultStack(),
                                input.getCount(),
                                output.getCount(),
                                additionalLines
                        );
                    }
                } catch (Exception e) {
                    // 静默处理异常
                }
            }
        }

        return null;
    }

    /**
     * 构建主要的兑换信息显示组件
     * 根据不同的兑换模式（动态定价+虚拟货币、普通虚拟货币、普通物品）生成相应的tooltip显示
     *
     * @param input 输入物品信息
     * @param output 输出物品信息
     * @param rule 兑换规则对象
     * @return 格式化的Component显示组件，包含完整的兑换信息
     */
    private static Component buildMainExchangeInfo(ExchangeRule.InputItem input, ExchangeRule.OutputItem output, ExchangeRule rule) {
        try {
            Component inputName = getLocalizedItemName(input);

            // 动态定价+虚拟货币模式
            if ("dynamic_pricing".equals(output.getType()) && output.isCoin()) {
                String itemIdentifier = rule.getInputs().getFirst().getItem();
                int soldCount = getLatestSoldCount(itemIdentifier);
                int dynamicValue = output.getDynamicCount(soldCount);

                // 参数顺序：输入数量、输入物品名、动态值、虚拟货币文本
                return Component.translatable("tooltip.shipping_box.exchange.format.coin_dynamic",
                                Component.literal(String.valueOf(input.getCount())).withStyle(ChatFormatting.YELLOW),
                                inputName.copy().withStyle(ChatFormatting.GOLD),
                                Component.literal(String.valueOf(dynamicValue)).withStyle(ChatFormatting.AQUA),
                                Component.translatable("tooltip.shipping_box.virtual_currency_info").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .withStyle(ChatFormatting.WHITE);
            }
            // 普通虚拟货币模式
            else if (output.isCoin()) {
                return Component.translatable("tooltip.shipping_box.exchange.format.coin_simple",
                                Component.literal(String.valueOf(input.getCount())).withStyle(ChatFormatting.YELLOW),
                                inputName.copy().withStyle(ChatFormatting.GOLD),
                                Component.literal(String.valueOf(output.getCount())).withStyle(ChatFormatting.AQUA),
                                Component.translatable("tooltip.shipping_box.virtual_currency_unit").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .withStyle(ChatFormatting.WHITE);
            }
            // 普通物品模式
            else {
                Component outputInfo = getSimpleOutputName(output, rule);
                return Component.translatable("tooltip.shipping_box.exchange_format_colored",
                                Component.literal(String.valueOf(input.getCount())).withStyle(ChatFormatting.YELLOW),
                                inputName.copy().withStyle(ChatFormatting.GOLD),
                                Component.literal(String.valueOf(output.getCount())).withStyle(ChatFormatting.AQUA),
                                outputInfo.copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                        .withStyle(ChatFormatting.WHITE);
            }
        } catch (Exception e) {
            return Component.literal("[" + input.getCount() + " items → ?]").withStyle(ChatFormatting.RED);
        }
    }

    /**
     * 构建动态定价模式的重置信息行组件
     * 根据不同的重置模式和销售状态生成相应的重置周期显示信息
     *
     * @param output 输出物品信息，包含动态定价属性
     * @param rule 兑换规则对象，用于获取输入物品标识符
     * @return 格式化的Component显示组件，包含重置周期信息；如果发生异常则返回null
     */
    private static Component buildResetInfoLine(ExchangeRule.OutputItem output, ExchangeRule rule) {
        try {
            String itemIdentifier = rule.getInputs().getFirst().getItem();
            int soldCount = getLatestSoldCount(itemIdentifier);
            int resetDay = output.getDynamicProperties().getDay();
            int daysSinceLastSale = DynamicPricingManager.getDaysSinceLastSale(itemIdentifier);
            int remainingDays = DynamicPricingManager.getResetRemainingDays(itemIdentifier);

            Component resetPart;
            // 根据重置天数设置不同的显示文本
            if (resetDay == -1) {
                // 永不重置模式
                resetPart = Component.translatable("tooltip.shipping_box.reset_info_never_reset")
                        .withStyle(ChatFormatting.DARK_GREEN);
            } else if (resetDay == 0) {
                // 每日重置模式
                resetPart = Component.translatable("tooltip.shipping_box.reset_info_daily_reset")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                // 按天数重置模式，根据销售状态显示不同信息
                if (daysSinceLastSale == -1) {
                    // 从未销售过
                    resetPart = Component.translatable("tooltip.shipping_box.reset_info_never_sold", resetDay)
                            .withStyle(ChatFormatting.GREEN);
                } else if (remainingDays <= 0) {
                    // 已可以重置
                    resetPart = Component.translatable("tooltip.shipping_box.reset_info_ready", daysSinceLastSale)
                            .withStyle(ChatFormatting.GREEN);
                } else {
                    // 等待重置
                    resetPart = Component.translatable("tooltip.shipping_box.reset_info_waiting",
                                    daysSinceLastSale, remainingDays)
                            .withStyle(ChatFormatting.RED);
                }
            }

            // 用方括号包装重置信息
            return Component.literal("[")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("tooltip.shipping_box.reset_period_prefix"))
                    .append(resetPart)
                    .append(Component.literal("]"))
                    .withStyle(ChatFormatting.GRAY);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取简化的输出物品名称显示组件
     * 根据不同的输出模式（权重、动态定价、虚拟货币等）返回相应的显示文本
     * 不包含复杂的动态定价详细信息，主要用于tooltip的基础显示
     *
     * @param output 输出物品对象，包含类型、数量等信息
     * @param rule 兑换规则对象，用于获取输入物品信息（主要用于虚拟货币模式）
     * @return 格式化的Component显示组件，包含简化后的输出物品信息
     */
    private static Component getSimpleOutputName(ExchangeRule.OutputItem output, ExchangeRule rule) {
        try {
            // 检查是否为权重模式
            if ("weight".equals(output.getType()) && output.getItems() != null && !output.getItems().isEmpty()) {
                // 权重模式：显示"随机物品"或者列出所有可能的物品
                return buildWeightedItemsDisplay(output.getItems());
            }

            // 检查是否为动态定价模式
            if ("dynamic_pricing".equals(output.getType()) && output.getDynamicProperties() != null) {
                // 检查是否为动态定价+虚拟货币模式
                if (output.isCoin()) {
                    // 虚拟货币模式：只显示基础的虚拟货币信息
                    String itemIdentifier = rule.getInputs().getFirst().getItem();
                    int soldCount = getLatestSoldCount(itemIdentifier);
                    int dynamicCount = output.getDynamicCount(soldCount);

                    // 只显示基础信息，不包含重置信息
                    return Component.literal("◎")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.translatable("tooltip.shipping_box.dynamic_pricing_info", dynamicCount, soldCount)
                                    .withStyle(ChatFormatting.YELLOW));
                } else {
                    // 普通动态定价模式：只显示物品名称
                    return getLocalizedItemName(output.getItem());
                }
            }

            // 检查是否为普通虚拟货币模式（非动态定价）
            if (output.isCoin()) {
                // 虚拟货币模式下显示货币数量
                return Component.translatable("tooltip.shipping_box.virtual_currency", output.getCount())
                        .withStyle(ChatFormatting.GOLD);
            }

            if (output.getItem() != null && !output.getItem().isEmpty()) {
                return getLocalizedItemNameWithComponents(output.getItem(), output.getComponents());
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
    private static Component getLocalizedItemName(ExchangeRule.OutputItem output, ExchangeRule rule) {
        try {
            // 检查是否为权重模式
            if ("weight".equals(output.getType()) && output.getItems() != null && !output.getItems().isEmpty()) {
                // 权重模式：显示"随机物品"或者列出所有可能的物品
                return buildWeightedItemsDisplay(output.getItems());
            }

            // 检查是否为动态定价模式
            if ("dynamic_pricing".equals(output.getType()) && output.getDynamicProperties() != null) {
                // 检查是否为动态定价+虚拟货币模式
                if (output.isCoin()) {
                    // 虚拟货币模式：显示虚拟货币信息
                    // 使用输入物品作为标识符，因为虚拟货币模式下output.getItem()可能为null
                    String itemIdentifier = rule.getInputs().getFirst().getItem();

                    // 获取销售相关信息
                    int soldCount = getLatestSoldCount(itemIdentifier);
                    int dynamicCount = output.getDynamicCount(soldCount);
                    int resetDay = output.getDynamicProperties().getDay();
                    int daysSinceLastSale = DynamicPricingManager.getDaysSinceLastSale(itemIdentifier);
                    int remainingDays = DynamicPricingManager.getResetRemainingDays(itemIdentifier);

                    // 构建虚拟货币动态定价信息
                    return buildVirtualCurrencyDynamicPricingInfo(dynamicCount, soldCount, resetDay, daysSinceLastSale, remainingDays);
                } else {
                    // 普通动态定价模式
                    // 动态定价模式：返回完整的动态定价显示信息
                    String itemIdentifier = output.getItem();

                    // 获取销售相关信息
                    int soldCount = getLatestSoldCount(itemIdentifier);
                    int dynamicCount = output.getDynamicCount(soldCount);
                    int resetDay = output.getDynamicProperties().getDay();
                    int daysSinceLastSale = DynamicPricingManager.getDaysSinceLastSale(itemIdentifier);
                    int remainingDays = DynamicPricingManager.getResetRemainingDays(itemIdentifier);

                    // 获取基础物品名称
                    Component baseName = getLocalizedItemName(itemIdentifier);

                    // 构建动态定价信息
                    Component dynamicInfo = buildDynamicPricingInfo(dynamicCount, soldCount,
                            resetDay, daysSinceLastSale, remainingDays);

                    // 组合返回：基础名称 + 动态信息
                    return Component.empty()
                            .append(baseName)
                            .append(dynamicInfo);
                }
            }

            // 检查是否为普通虚拟货币模式（非动态定价）
            if (output.isCoin()) {
                // 虚拟货币模式下显示货币数量
                return Component.translatable("tooltip.shipping_box.virtual_currency", output.getCount())
                        .withStyle(ChatFormatting.GOLD);
            }

            if (output.getItem() != null && !output.getItem().isEmpty()) {
                return getLocalizedItemNameWithComponents(output.getItem(), output.getComponents());
            }
        } catch (Exception e) {
            // 异常处理
        }
        return Component.translatable("tooltip.shipping_box.unknown_item").withStyle(ChatFormatting.RED);
    }

    /**
     * 获取输出物品的本地化显示名称（简化版本）
     * 为不涉及动态定价+虚拟货币复杂逻辑的场景提供便利接口
     * 通过调用双参数版本实现，传入null作为rule参数
     *
     * @param output 输出物品对象
     * @return 物品的本地化名称组件
     */
    private static Component getLocalizedItemName(ExchangeRule.OutputItem output) {
        // 对于不涉及动态定价+虚拟货币的情况，可以传入null作为rule
        return getLocalizedItemName(output, null);
    }

    /**
     * 构建动态定价信息显示
     */
    private static Component buildDynamicPricingInfo(int dynamicCount, int soldCount,
                                                     int resetDay, int daysSinceLastSale, int remainingDays) {
        // 基本信息：数量和已售
        Component baseInfo = Component.translatable("tooltip.shipping_box.dynamic_pricing_info",
                dynamicCount, soldCount).withStyle(ChatFormatting.YELLOW);

        // 根据不同的重置模式显示不同的时间信息
        if (resetDay == -1) {
            // 永不重置模式
            Component neverResetInfo = Component.translatable("tooltip.shipping_box.reset_info_never_reset")
                    .withStyle(ChatFormatting.DARK_GREEN);

            // 返回多个独立的组件，这样tooltip会分行显示
            return Component.empty()
                    .append(baseInfo)
                    .append(Component.literal("\n"))  // 添加换行符
                    .append(neverResetInfo);
        } else if (resetDay == 0) {
            // 每天重置模式
            Component dailyResetInfo = Component.translatable("tooltip.shipping_box.reset_info_daily_reset")
                    .withStyle(ChatFormatting.GREEN);

            return Component.empty()
                    .append(baseInfo)
                    .append(Component.literal("\n"))  // 添加换行符
                    .append(dailyResetInfo);
        } else if (resetDay > 0) {
            // 按天数重置模式
            Component timeInfo;
            if (daysSinceLastSale == -1) {
                // 从未销售过
                timeInfo = Component.translatable("tooltip.shipping_box.reset_info_never_sold",
                        resetDay).withStyle(ChatFormatting.GREEN);
            } else if (remainingDays <= 0) {
                // 已经可以重置
                timeInfo = Component.translatable("tooltip.shipping_box.reset_info_ready",
                        daysSinceLastSale).withStyle(ChatFormatting.GREEN);
            } else {
                // 还需要等待
                timeInfo = Component.translatable("tooltip.shipping_box.reset_info_waiting",
                        daysSinceLastSale, remainingDays).withStyle(ChatFormatting.RED);
            }

            // 返回多个独立的组件
            return Component.empty()
                    .append(baseInfo)
                    .append(Component.literal(" "))
                    .append(timeInfo);
        }

        return baseInfo;
    }

    /**
     * 构建虚拟货币+动态定价信息显示
     */
    private static Component buildVirtualCurrencyDynamicPricingInfo(int dynamicCount, int soldCount,
                                                                    int resetDay, int daysSinceLastSale, int remainingDays) {
        // 构建主文本部分：虚拟货币 (数量:X,已售:Y)
        Component mainPart = Component.literal("◎")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("tooltip.shipping_box.dynamic_pricing_info", dynamicCount, soldCount)
                        .withStyle(ChatFormatting.YELLOW));

        // 构建重置信息部分 - 使用现有的本地化键
        Component resetPart;
        if (resetDay == -1) {
            // 永不重置模式
            resetPart = Component.translatable("tooltip.shipping_box.reset_info_never_reset")
                    .withStyle(ChatFormatting.DARK_GREEN);
        } else if (resetDay == 0) {
            // 每天重置模式
            resetPart = Component.translatable("tooltip.shipping_box.reset_info_daily_reset")
                    .withStyle(ChatFormatting.GREEN);
        } else {
            // 按天数重置模式
            if (daysSinceLastSale == -1) {
                // 从未销售过
                resetPart = Component.translatable("tooltip.shipping_box.reset_info_never_sold", resetDay)
                        .withStyle(ChatFormatting.GREEN);
            } else if (remainingDays <= 0) {
                // 已经可以重置
                resetPart = Component.translatable("tooltip.shipping_box.reset_info_ready", daysSinceLastSale)
                        .withStyle(ChatFormatting.GREEN);
            } else {
                // 还需要等待
                resetPart = Component.translatable("tooltip.shipping_box.reset_info_waiting",
                                daysSinceLastSale, remainingDays)
                        .withStyle(ChatFormatting.RED);
            }
        }

        // 返回多个独立的组件，实现分行显示
        return Component.empty()
                .append(mainPart)
                .append(Component.literal(" "))
                .append(resetPart);
    }

    /**
     * 获取带组件信息的物品名称显示
     *
     * @param itemIdentifier 物品标识符
     * @param components 组件信息
     * @return 带组件提示的物品名称组件
     */
    private static Component getLocalizedItemNameWithComponents(String itemIdentifier, Object components) {
        try {
            Component baseName = getLocalizedItemName(itemIdentifier);

            // 如果有组件信息，添加组件提示
            if (components != null) {
                Component componentIndicator = Component.translatable("tooltip.shipping_box.with_components")
                        .withStyle(ChatFormatting.GRAY);
                return Component.empty()
                        .append(baseName)
                        .append(Component.literal(" "))
                        .append(componentIndicator);
            }

            return baseName;
        } catch (Exception e) {
            return getLocalizedItemName(itemIdentifier);
        }
    }

    /**
     * 构建权重物品的显示文本
     *
     * @param weightedItems 权重物品列表
     * @return 格式化的显示组件
     */
    private static Component buildWeightedItemsDisplay(List<ExchangeRule.WeightedItem> weightedItems) {
        try {
            // 只显示物品名称，不限制数量和权重
            List<Component> itemNames = new ArrayList<>();

            // 最多显示3个物品
            int displayLimit = Math.min(3, weightedItems.size());

            for (int i = 0; i < displayLimit; i++) {
                ExchangeRule.WeightedItem item = weightedItems.get(i);
                Component itemName = getLocalizedItemName(item.getItem());
                itemNames.add(itemName);
            }

            // 如果还有更多物品，添加"以及更多"提示
            if (weightedItems.size() > 3) {
                Component moreText = Component.translatable("tooltip.shipping_box.and_more")
                        .withStyle(ChatFormatting.GRAY);
                itemNames.add(moreText);
            }

            // 用逗号连接所有物品名称
            if (itemNames.isEmpty()) {
                return Component.translatable("tooltip.shipping_box.random_item").withStyle(ChatFormatting.GOLD);
            }

            // 手动拼接组件
            MutableComponent result = Component.empty();
            for (int i = 0; i < itemNames.size(); i++) {
                result.append(itemNames.get(i));
                if (i < itemNames.size() - 1) {
                    result.append(Component.literal("/"));
                }
            }

            return result.withStyle(ChatFormatting.GOLD);

        } catch (Exception e) {
            return Component.translatable("tooltip.shipping_box.random_item").withStyle(ChatFormatting.GOLD);
        }
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
                // 对于标签，显示标签名称
                String tagName = input.getTag();
                if (tagName.startsWith("#")) {
                    tagName = tagName.substring(1);
                }
                return Component.literal("#" + tagName);
            }
            // 如果是物品ID
            else if (input.getItem() != null && !input.getItem().isEmpty()) {
                return getLocalizedItemNameWithComponents(input.getItem(), input.getComponents());
            }
        } catch (Exception e) {
            // 异常处理
        }
        return Component.translatable("tooltip.shipping_box.unknown_item").withStyle(ChatFormatting.RED);
    }


    /**
     * 获取最新的销售计数（优先使用客户端缓存）
     */
    private static int getLatestSoldCount(String itemIdentifier) {
        try {
            // 在客户端环境下优先使用缓存数据
            if (FMLEnvironment.dist == Dist.CLIENT && ClientSoldCountCache.hasCachedData(itemIdentifier)) {
                return ClientSoldCountCache.getCachedSoldCount(itemIdentifier);
            }

            // 回退到服务器数据
            return DynamicPricingManager.getSoldCount(itemIdentifier);
        } catch (Exception e) {
            // 如果都失败，返回0作为默认值
            return 0;
        }
    }
}
