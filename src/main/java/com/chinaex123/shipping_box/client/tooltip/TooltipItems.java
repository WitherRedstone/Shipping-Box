package com.chinaex123.shipping_box.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Supplier;

/**
 * 提示物品基类。
 * <p>
 * 为售货箱模组的特殊物品提供自定义 Tooltip 支持。
 * 子类可以通过构造函数传入 Tooltip 内容提供器，
 * 在物品悬停提示中显示额外信息（如硬币价值、使用说明等）。
 * 简化了传统使用 addInformation 事件的实现方式。
 */
public class TooltipItems extends Item {

    /** Tooltip 内容提供器，用于动态生成物品的提示信息 */
    private final Supplier<List<Component>> tooltipSupplier;

    /**
     * 构造提示物品。
     *
     * @param properties      物品属性
     * @param tooltipSupplier Tooltip 内容提供器
     */
    public TooltipItems(Properties properties, Supplier<List<Component>> tooltipSupplier) {
        super(properties);
        this.tooltipSupplier = tooltipSupplier;
    }

    /**
     * 获取 Tooltip 内容列表。
     * <p>
     * 26.2 中 appendHoverText 已弃用，改为通过 ItemTooltipEvent 事件调用此方法。
     *
     * @return Tooltip 内容列表，如果提供器为 null 则返回空列表
     */
    public List<Component> getTooltipLines() {
        if (tooltipSupplier != null) {
            return tooltipSupplier.get();
        }
        return List.of();
    }
}