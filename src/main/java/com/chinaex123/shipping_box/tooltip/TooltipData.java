package com.chinaex123.shipping_box.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 工具提示数据类
 * 存储物品的兑换信息用于显示在工具提示中
 */
public class TooltipData {
    private final List<Component> exchangeInfo;
    private final ItemStack inputStack;
    private final ItemStack outputStack;
    private final int inputCount;
    private final int outputCount;

    public TooltipData(List<Component> exchangeInfo, ItemStack inputStack, ItemStack outputStack, int inputCount, int outputCount) {
        this.exchangeInfo = exchangeInfo;
        this.inputStack = inputStack;
        this.outputStack = outputStack;
        this.inputCount = inputCount;
        this.outputCount = outputCount;
    }

    public List<Component> getExchangeInfo() {
        return exchangeInfo;
    }

    public ItemStack getInputStack() {
        return inputStack;
    }

    public ItemStack getOutputStack() {
        return outputStack;
    }

    public int getInputCount() {
        return inputCount;
    }

    public int getOutputCount() {
        return outputCount;
    }

    /**
     * 检查是否包含有效的兑换信息
     */
    public boolean hasExchangeInfo() {
        return exchangeInfo != null && !exchangeInfo.isEmpty();
    }
}
