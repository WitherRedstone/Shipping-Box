package com.chinaex123.shipping_box.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * 工具提示事件处理器
 * 监听物品工具提示事件，添加兑换信息
 */
@EventBusSubscriber
public class TooltipEventHandler {

    /**
     * 物品工具提示事件处理器
     * 为支持兑换的物品添加详细的兑换信息到工具提示中
     *
     * @param event 物品工具提示事件
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) {
                return;
            }

            // 获取兑换信息
            TooltipData tooltipData = ExchangeTooltipProvider.getExchangeTooltip(stack);

            if (tooltipData != null && tooltipData.hasExchangeInfo()) {
                List<Component> tooltip = event.getToolTip();

                // 在工具提示中添加分隔线
                tooltip.add(Component.empty());

                // 添加标题
                tooltip.add(Component.translatable("tooltip.shipping_box.title"));

                // 添加具体的兑换信息
                for (Component info : tooltipData.getExchangeInfo()) {
                    tooltip.add(Component.literal("  ").append(info));
                }

                // 添加说明文字
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("tooltip.shipping_box.instruction"));
            }
        } catch (Exception e) {
            // 静默处理异常，避免游戏崩溃
        }
    }
}
