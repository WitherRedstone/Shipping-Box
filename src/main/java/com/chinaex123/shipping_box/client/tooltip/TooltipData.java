package com.chinaex123.shipping_box.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 工具提示数据类。
 * <p>
 * 存储物品的兑换信息，用于显示在工具提示中。
 * <p>
 * 当玩家将鼠标悬停在售货箱中的物品上时，
 * 此类提供需要显示的额外信息，包括：
 * - 该物品可以兑换成什么；
 * - 兑换所需的数量；
 * - 兑换产出的数量；
 * - 额外的说明信息。
 */
public class TooltipData {

    /** 兑换信息文本列表 */
    private final List<Component> exchangeInfo;

    /** 输入物品（兑换消耗的物品） */
    private final ItemStack inputStack;

    /** 输出物品（兑换产出的物品） */
    private final ItemStack outputStack;

    /** 输入物品的数量（消耗多少个） */
    private final int inputCount;

    /** 输出物品的数量（产出多少个） */
    private final int outputCount;

    /** 额外的行文本（如：价格加成、特殊提示等） */
    private final List<Component> additionalLines;

    /**
     * 构造工具提示数据。
     *
     * @param exchangeInfo 兑换信息文本列表
     * @param inputStack   输入物品（兑换消耗的物品）
     * @param outputStack  输出物品（兑换产出的物品）
     * @param inputCount   输入物品的数量
     * @param outputCount  输出物品的数量
     */
    public TooltipData(List<Component> exchangeInfo, ItemStack inputStack, ItemStack outputStack, int inputCount, int outputCount) {
        this(exchangeInfo, inputStack, outputStack, inputCount, outputCount, null);
    }

    /**
     * 构造工具提示数据（完整版）。
     * <p>
     * 支持额外的行文本，用于显示特殊信息。
     *
     * @param exchangeInfo    兑换信息文本列表
     * @param inputStack      输入物品（兑换消耗的物品）
     * @param outputStack     输出物品（兑换产出的物品）
     * @param inputCount      输入物品的数量
     * @param outputCount     输出物品的数量
     * @param additionalLines 额外的行文本（如：价格加成提示）
     */
    public TooltipData(List<Component> exchangeInfo, ItemStack inputStack, ItemStack outputStack,
                       int inputCount, int outputCount, List<Component> additionalLines) {
        this.exchangeInfo = exchangeInfo;
        this.inputStack = inputStack;
        this.outputStack = outputStack;
        this.inputCount = inputCount;
        this.outputCount = outputCount;
        this.additionalLines = additionalLines;
    }

    /**
     * 获取兑换信息文本列表。
     *
     * @return 兑换信息文本列表
     */
    public List<Component> getExchangeInfo() {
        return exchangeInfo;
    }

    /**
     * 获取输入物品（兑换消耗的物品）。
     *
     * @return 输入物品堆栈
     */
    public ItemStack getInputStack() {
        return inputStack;
    }

    /**
     * 获取输出物品（兑换产出的物品）。
     *
     * @return 输出物品堆栈
     */
    public ItemStack getOutputStack() {
        return outputStack;
    }

    /**
     * 获取输入物品的数量。
     *
     * @return 消耗数量
     */
    public int getInputCount() {
        return inputCount;
    }

    /**
     * 获取输出物品的数量。
     *
     * @return 产出数量
     */
    public int getOutputCount() {
        return outputCount;
    }

    /**
     * 获取额外的行文本。
     *
     * @return 额外行文本列表
     */
    public List<Component> getAdditionalLines() {
        return this.additionalLines;
    }

    /**
     * 添加额外的行文本。
     *
     * @param line 要添加的行文本
     */
    public void addAdditionalLine(Component line) {
        this.additionalLines.add(line);
    }

    /**
     * 检查是否包含兑换信息。
     *
     * @return 如果有兑换信息则返回 true，否则返回 false
     */
    public boolean hasExchangeInfo() {
        return exchangeInfo != null && !exchangeInfo.isEmpty();
    }

    /**
     * 检查是否包含额外的行文本。
     *
     * @return 如果有额外行文本则返回 true，否则返回 false
     */
    public boolean hasAdditionalLines() {
        return additionalLines != null && !additionalLines.isEmpty();
    }
}