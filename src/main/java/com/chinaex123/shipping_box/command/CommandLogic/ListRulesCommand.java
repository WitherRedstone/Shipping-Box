package com.chinaex123.shipping_box.command.CommandLogic;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** 列出兑换规则命令的执行器 **/
public class ListRulesCommand {

    private static final int RULES_PER_PAGE = 5;

    /**
     * 执行列出规则命令
     * @param context 命令上下文
     * @return 命令执行结果（成功返回 1，失败返回 0）
     */
    public static int execute(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();

            // 获取参数：页码（可选，默认第 1 页）
            int page = 1;
            try {
                page = context.getArgument("page", Integer.class);
                if (page < 1) page = 1;
            } catch (IllegalArgumentException e) {
                // 没有提供页码参数，使用默认值
            }

            List<ExchangeRule> rules = ExchangeRecipeManager.getRules();

            if (rules.isEmpty()) {
                source.sendFailure(Component.translatable("command.shipping_box.rules.no_rules_found")
                        //.withStyle(style -> style.withColor(0x55FF55))
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            int totalPages = (int) Math.ceil((double) rules.size() / RULES_PER_PAGE);
            if (page > totalPages) page = totalPages;

            // 发送标题
            int finalPage = page;
            source.sendSuccess(
                    () -> Component.translatable("command.shipping_box.rules.list_title", finalPage, totalPages)
                            .withStyle(ChatFormatting.GOLD),
                    true);

            // 计算当前页的起始和结束索引
            int startIndex = (page - 1) * RULES_PER_PAGE;
            int endIndex = Math.min(startIndex + RULES_PER_PAGE, rules.size());

            // 发送当前页的规则列表
            for (int i = startIndex; i < endIndex; i++) {
                ExchangeRule rule = rules.get(i);
                Component ruleInfo = formatRuleInfo(i + 1, rule);
                source.sendSuccess(() -> ruleInfo, false);
            }

            // 发送分页提示
            if (totalPages > 1) {
                Component pageInfo = Component.translatable("command.shipping_box.rules.page_info",
                                page, totalPages, page > 1 ? "/shipping_box rules list " + (page - 1) : "",
                                page < totalPages ? "/shipping_box rules list " + (page + 1) : "")
                        .withStyle(ChatFormatting.GRAY);
                source.sendSuccess(() -> pageInfo, false);
            }

            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.translatable("command.shipping_box.error.execution", e.getMessage()));
            return 0;
        }
    }

    /**
     * 格式化单条规则的信息
     */
    private static Component formatRuleInfo(int index, ExchangeRule rule) {
        // 创建可变文本组件
        MutableComponent text = Component.literal("");
        
        // 序号 - 灰色
        text.append(Component.literal("[" + index + "] ").withStyle(ChatFormatting.GRAY));
        
        // 输入标签 - 绿色
        text.append(Component.translatable("command.shipping_box.rules.input_label")
                .withStyle(ChatFormatting.GREEN));
        
        var inputs = rule.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            
            // 尝试获取本地化的物品名称
            String itemName = getLocalizedItemName(input.getItem(), input.getTag());
            // 输入物品：金色 - 先创建带颜色的组件
            MutableComponent itemText = Component.literal(itemName).withStyle(ChatFormatting.GOLD);
            text.append(itemText);
            text.append(" x").withStyle(ChatFormatting.GOLD).append(String.valueOf(input.getCount()));

            // 组件信息 - 黄色
            if (input.getComponents() != null) {
                text.append(Component.translatable("command.shipping_box.rules.with_components")
                        .withStyle(ChatFormatting.YELLOW));
            }

            // 分隔符 - 黄色
            if (i < inputs.size() - 1) {
                text.append(Component.translatable("command.shipping_box.rules.separator")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        // 箭头 - 白色
        text.append(Component.translatable("command.shipping_box.rules.arrow")
                .withStyle(ChatFormatting.WHITE));

        // 输出物品
        var output = rule.getOutputItem();
        
        // 检查是否是权重类型（随机物品）
        if ("weight".equals(output.getType())) {
            // 随机物品 - 淡紫色
            text.append(Component.translatable("command.shipping_box.rules.weight_random")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            // 权重后缀 - 青色
            text.append(Component.translatable("command.shipping_box.rules.weight_suffix")
                    .withStyle(ChatFormatting.AQUA));
        } else if (output.isCoin()) {
            // 虚拟货币模式
            if ("dynamic_pricing".equals(output.getType())) {
                // 虚拟货币 - 淡紫色
                text.append(Component.translatable("command.shipping_box.rules.virtual_currency")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                // 动态定价后缀 - 青色
                text.append(Component.translatable("command.shipping_box.rules.dynamic_pricing_suffix")
                        .withStyle(ChatFormatting.AQUA));
            } else {
                // 普通虚拟货币，显示数量 - 淡紫色 + 灰色
                text.append(Component.translatable("command.shipping_box.rules.virtual_currency")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                text.append(" §7x").append(String.valueOf(output.getCount())).append("◎");
            }
        } else if ("ecliptic_seasons".equals(output.getType())) {
            // 节气联动模式
            String itemName = getLocalizedItemName(output.getItem(), null);
            // 输出物品：紫色 - 先创建带颜色的组件
            MutableComponent itemText = Component.literal(itemName).withStyle(ChatFormatting.LIGHT_PURPLE);
            text.append(itemText);
            if (output.getCount() > 1) {
                text.append(" x").withStyle(ChatFormatting.LIGHT_PURPLE).append(String.valueOf(output.getCount()));
            }
            // 节气联动后缀 - 青色
            text.append(Component.translatable("command.shipping_box.rules.ecliptic_seasons_suffix")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            // 普通物品输出
            String itemName = getLocalizedItemName(output.getItem(), null);
            // 输出物品：紫色 - 先创建带颜色的组件
            MutableComponent itemText = Component.literal(itemName).withStyle(ChatFormatting.LIGHT_PURPLE);
            text.append(itemText);
            if (output.getCount() > 1) {
                text.append(" x").withStyle(ChatFormatting.LIGHT_PURPLE).append(String.valueOf(output.getCount()));
            }
            
            // 组件信息 - 黄色
            if (output.getComponents() != null) {
                text.append(Component.translatable("command.shipping_box.rules.with_components")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        return text;
    }

    /**
     * 获取物品的本地化名称
     * @param itemId 物品 ID（如 minecraft:diamond）
     * @param tagId 标签 ID（如 #minecraft:planks）
     * @return 本地化的物品名称
     */
    private static String getLocalizedItemName(String itemId, String tagId) {
        try {
            // 如果是标签
            if (tagId != null && !tagId.isEmpty()) {
                String displayTag = tagId.startsWith("#") ? tagId : "#" + tagId;
                return "§e" + displayTag + "§r";
            }
            
            // 如果是物品 ID
            if (itemId != null && !itemId.isEmpty()) {
                ResourceLocation itemLoc = ResourceLocation.tryParse(itemId);
                if (itemLoc != null) {
                    var item = BuiltInRegistries.ITEM.get(itemLoc);
                    if (item != Items.AIR) {
                        // 创建一个临时的物品堆来获取显示名称
                        ItemStack stack = new ItemStack(item);
                        return stack.getHoverName().getString();
                    } else {
                        // 物品不存在，返回原始 ID（可能是其他模组的物品）
                        String[] parts = itemId.split(":");
                        if (parts.length == 2) {
                            return "§9" + parts[0] + ":" + parts[1] + "§r";
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 如果获取失败，返回原始 ID
        }
        
        // 如果都失败了，返回原始 ID
        return itemId != null ? "§c" + itemId + "§r" : (tagId != null ? "§e" + tagId + "§r" : "Unknown Item");
    }
}
