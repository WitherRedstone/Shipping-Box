package com.chinaex123.shipping_box.event;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 兑换规则类
 * <p>
 * 定义了物品兑换的输入输出规则
 * 包含输入物品列表和输出物品的定义
 * 支持多个输入物品对应一个输出物品的兑换逻辑
 */
public class ExchangeRule {
    /** 输入物品列表 */
    private List<InputItem> inputs = new ArrayList<>();

    /** 输出物品定义 */
    private OutputItem output;

    /**
     * 获取输入物品列表
     *
     * @return List<InputItem> 输入物品列表
     */
    public List<InputItem> getInputs() {
        return inputs;
    }

    /**
     * 设置输入物品列表
     *
     * @param inputs 输入物品列表
     */
    public void setInputs(List<InputItem> inputs) {
        this.inputs = inputs;
    }

    /**
     * 获取输出物品定义
     *
     * @return OutputItem 输出物品
     */
    public OutputItem getOutputItem() {
        return output;
    }

    /**
     * 设置输出物品定义
     *
     * @param output 输出物品
     */
    public void setOutput(OutputItem output) {
        this.output = output;
    }

    /**
     * 获取兑换结果的物品堆
     * 根据输出物品定义创建对应的ItemStack
     *
     * @return ItemStack 兑换结果物品堆，如果物品无效则返回空物品堆
     */
    public ItemStack getResultStack() {
        ResourceLocation id = ResourceLocation.parse(output.getItem());
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != null) {
            return new ItemStack(item, output.getCount());
        }
        return ItemStack.EMPTY;
    }

    /**
     * 输入物品内部类
     * 定义兑换规则中所需的输入物品及其数量
     */
    public static class InputItem {
        /** 物品标识符（命名空间:id格式） */
        private String item;

        /** 所需物品数量，默认为1 */
        private int count = 1;

        /**
         * 获取物品标识符
         *
         * @return String 物品标识符
         */
        public String getItem() {
            return item;
        }

        /**
         * 设置物品标识符
         *
         * @param item 物品标识符
         */
        public void setItem(String item) {
            this.item = item;
        }

        /**
         * 获取所需物品数量
         *
         * @return int 物品数量
         */
        public int getCount() {
            return count;
        }

        /**
         * 设置所需物品数量
         *
         * @param count 物品数量
         */
        public void setCount(int count) {
            this.count = count;
        }

        /**
         * 检查给定的物品堆是否匹配此输入要求
         *
         * @param stack 要检查的物品堆
         * @return boolean 匹配返回true，否则返回false
         */
        public boolean matches(ItemStack stack) {
            ResourceLocation id = ResourceLocation.parse(item);
            Item requiredItem = BuiltInRegistries.ITEM.get(id);
            return requiredItem != null && stack.getItem() == requiredItem;
        }
    }

    /**
     * 输出物品内部类
     * 定义兑换规则的输出物品及其数量
     */
    public static class OutputItem {
        /** 物品标识符（命名空间:id格式） */
        private String item;

        /** 输出物品数量，默认为1 */
        private int count = 1;

        /**
         * 获取物品标识符
         *
         * @return String 物品标识符
         */
        public String getItem() {
            return item;
        }

        /**
         * 设置物品标识符
         *
         * @param item 物品标识符
         */
        public void setItem(String item) {
            this.item = item;
        }

        /**
         * 获取输出物品数量
         *
         * @return int 物品数量
         */
        public int getCount() {
            return count;
        }

        /**
         * 设置输出物品数量
         *
         * @param count 物品数量
         */
        public void setCount(int count) {
            this.count = count;
        }
    }
}
