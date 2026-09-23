package com.chinaex123.shipping_box.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 提示物品基类。
 * <p>
 * 通过构造时传入的 Tooltip 内容提供器，为物品动态添加提示信息，
 * 便于在注册物品时按需生成多语言或条件化的说明文本。
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
     * 添加物品 Tooltip 信息。
     * <p>
     * 先调用父类方法添加基础提示，再追加提供器返回的提示内容。
     *
     * @param stack   物品堆
     * @param context Tooltip 上下文
     * @param tooltip Tooltip 列表
     * @param flag    Tooltip 标志
     */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (tooltipSupplier != null) {
            tooltip.addAll(tooltipSupplier.get());
        }
    }
}