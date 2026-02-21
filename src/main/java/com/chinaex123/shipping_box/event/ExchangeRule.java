package com.chinaex123.shipping_box.event;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ExchangeRule {

    private List<InputItem> inputs;
    private OutputItem output;

    /**
     * 获取输入物品列表
     *
     * @return 输入物品列表
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
     * 获取输出物品信息
     *
     * @return 输出物品对象
     */
    public OutputItem getOutputItem() {
        return output;
    }

    /**
     * 设置输出物品信息
     *
     * @param output 输出物品对象
     */
    public void setOutput(OutputItem output) {
        this.output = output;
    }

    /**
     * 输入物品类，定义兑换规则中的输入物品规格
     */
    public static class InputItem {
        private String item;  // 物品ID
        private String tag;   // 标签ID
        private String components;  // 组件数据
        private int count = 1;

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public void setComponents(String components) {
            this.components = components;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        /**
         * 检查物品堆是否匹配此输入要求
         * 支持物品ID、标签和组件匹配
         *
         * @param stack 要检查的物品堆
         * @return 匹配返回true，否则返回false
         */
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty() || stack.getCount() < count) {
                return false;
            }

            if (tag != null && !tag.isEmpty()) {
                return matchesTag(stack);
            } else if (item != null && !item.isEmpty()) {
                return matchesItem(stack);
            }

            return false;
        }

        /**
         * 检查物品是否匹配指定标签
         *
         * @param stack 要检查的物品堆
         * @return 匹配返回true，否则返回false
         */
        private boolean matchesTag(ItemStack stack) {
            try {
                String tagIdStr = tag.startsWith("#") ? tag.substring(1) : tag;
                ResourceLocation tagId = ResourceLocation.tryParse(tagIdStr);
                if (tagId != null) {
                    TagKey<Item> itemTag = TagKey.create(BuiltInRegistries.ITEM.key(), tagId);
                    return stack.is(itemTag);
                }
            } catch (Exception e) {
                return false;
            }
            return false;
        }

        /**
         * 检查物品是否匹配指定ID和组件要求
         *
         * @param stack 要检查的物品堆
         * @return 匹配返回true，否则返回false
         */
        private boolean matchesItem(ItemStack stack) {
            try {
                String itemId = item;
                String componentString = null;

                // 解析内联组件
                int componentStart = item.indexOf('[');
                int componentEnd = item.lastIndexOf(']');
                if (componentStart > 0 && componentEnd > componentStart) {
                    itemId = item.substring(0, componentStart);
                    componentString = item.substring(componentStart + 1, componentEnd);
                }

                ResourceLocation itemResource = ResourceLocation.tryParse(itemId);
                if (itemResource == null) {
                    return false;
                }

                Item requiredItem = BuiltInRegistries.ITEM.get(itemResource);
                if (!stack.is(requiredItem)) {
                    return false;
                }

                // 检查组件匹配
                String finalComponents = componentString != null ? componentString : components;
                if (finalComponents != null && !finalComponents.isEmpty()) {
                    return ExchangeRuleComponents.matchesComponents(stack, finalComponents);
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * 输出物品类，定义兑换规则中的输出物品规格
     */
    public static class OutputItem {
        private String item;
        private int count = 1;
        private String components;  // 组件数据字符串

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public void setComponents(String components) {
            this.components = components;
        }

        /**
         * 根据配置生成结果物品堆
         * 支持物品ID、数量和组件数据的完整构建
         *
         * @return 构建成功的物品堆，失败时返回空物品堆
         */
        public ItemStack getResultStack() {
            try {
                String itemId = item;
                String componentString = null;

                // 解析内联组件
                int componentStart = item.indexOf('[');
                int componentEnd = item.lastIndexOf(']');
                if (componentStart > 0 && componentEnd > componentStart) {
                    itemId = item.substring(0, componentStart);
                    componentString = item.substring(componentStart + 1, componentEnd);
                }

                ResourceLocation itemResource = ResourceLocation.tryParse(itemId);
                if (itemResource == null) {
                    return ItemStack.EMPTY;
                }

                Item resultItem = BuiltInRegistries.ITEM.get(itemResource);
                ItemStack resultStack = new ItemStack(resultItem, count);

                // 应用组件
                String finalComponents = componentString != null ? componentString : components;
                if (finalComponents != null && !finalComponents.isEmpty()) {
                    ExchangeRuleComponents.applyComponents(resultStack, finalComponents);
                }

                return resultStack;
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }
    }
}