package com.chinaex123.shipping_box.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 支持自定义Tooltip的物品类
 * 可以直接用于创建各种需要Tooltip的物品
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
    public void appendHoverText(@NotNull ItemStack stack, @Nullable TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (tooltipSupplier != null) {
            tooltip.addAll(tooltipSupplier.get());
        }
    }
}
