package com.chinaex123.shipping_box.event;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        private Object components;  // 改为Object类型以支持JsonObject和String
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

        // 修改setter方法
        public void setComponents(Object components) {
            this.components = components;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        // 修改getter方法
        public Object getComponents() {
            return components;
        }

        // 为了向后兼容，保留String版本的getter
        public String getComponentsAsString() {
            return components instanceof String ? (String) components : null;
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
                Object componentObject = null;

                // 解析内联组件
                int componentStart = item.indexOf('[');
                int componentEnd = item.lastIndexOf(']');
                if (componentStart > 0 && componentEnd > componentStart) {
                    itemId = item.substring(0, componentStart);
                    String componentString = item.substring(componentStart + 1, componentEnd);
                    componentObject = componentString;
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
                Object finalComponents = componentObject != null ? componentObject : components;

                System.out.println("Debug - Checking components: " + finalComponents + " (type: " + (finalComponents != null ? finalComponents.getClass().getSimpleName() : "null") + ")");

                if (finalComponents != null) {
                    if (finalComponents instanceof String) {
                        System.out.println("Debug - Using String component matcher");
                        return ExchangeRuleComponents.matchesComponents(stack, (String) finalComponents);
                    } else if (finalComponents instanceof JsonObject) {
                        System.out.println("Debug - Using JsonObject component matcher");
                        // 处理JsonObject类型的组件匹配 - 直接传递JsonObject对象
                        return ExchangeRuleComponents.matchesComponents(stack, (JsonObject) finalComponents);
                    }
                }

                return true;
            } catch (Exception e) {
                System.out.println("Debug - Exception in matchesItem: " + e.getMessage());
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
        private Object components;  // 改为Object类型以支持JsonObject和String

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

        // 修改setter方法
        public void setComponents(Object components) {
            this.components = components;
        }

        // 修改getter方法
        public Object getComponents() {
            return components;
        }

        // 为了向后兼容，保留String版本的getter
        public String getComponentsAsString() {
            return components instanceof String ? (String) components : null;
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

                // 处理不同类型的components
                Object finalComponents = componentString != null ? componentString : components;
                if (finalComponents != null) {
                    if (finalComponents instanceof JsonObject) {
                        // 直接是JsonObject对象
                        ExchangeRuleComponents.applyComponents(resultStack, (JsonObject) finalComponents);
                        System.out.println("Applied components: " + resultStack.getComponents());
                    } else if (finalComponents instanceof String componentStr) {
                        // 字符串格式
                        if (!componentStr.isEmpty()) {
                            if (componentStr.trim().startsWith("{") && componentStr.trim().endsWith("}")) {
                                JsonObject jsonObject = JsonParser.parseString(componentStr).getAsJsonObject();
                                ExchangeRuleComponents.applyComponents(resultStack, jsonObject);
                            } else {
                                ExchangeRuleComponents.applyComponents(resultStack, componentStr);
                            }
                            System.out.println("Applied components: " + resultStack.getComponents());
                        }
                    }
                }

                return resultStack;
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }
    }
}