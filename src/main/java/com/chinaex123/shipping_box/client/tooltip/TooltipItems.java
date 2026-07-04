package com.chinaex123.shipping_box.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 提示物品基类
 * <p>
 * 为售货箱模组的特殊物品提供自定义 Tooltip 支持。
 * 子类可以通过构造函数传入 Tooltip 内容提供器，
 * 在物品悬停提示中显示额外信息（如硬币价值、使用说明等）。
 * 简化了传统使用 addInformation 事件的实现方式。
 */
public class TooltipItems extends Item {
    private final Supplier<List<Component>> tooltipSupplier;

    /**
     * 构造函数
     *
     * @param properties 物品属性
     * @param tooltipSupplier Tooltip内容提供器
     */
    public TooltipItems(Properties properties, Supplier<List<Component>> tooltipSupplier) {
        super(properties);
        this.tooltipSupplier = tooltipSupplier;
    }

    /**
     * 添加物品Tooltip信息
     *
     * @param stack 物品堆
     * @param context Tooltip上下文
     * @param tooltip Tooltip列表
     * @param flag Tooltip标志
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> adder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, adder, flag);
        if (tooltipSupplier != null) {
            for (Component line : tooltipSupplier.get()) {
                adder.accept(line);
            }
        }
    }
}
