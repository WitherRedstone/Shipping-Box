package com.chinaex123.shipping_box.event;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

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
        private Object components;  // 支持JsonObject和String
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

        public Object getComponents() {
            return components;
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
                    componentObject = item.substring(componentStart + 1, componentEnd);
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

                if (finalComponents != null) {
                    if (finalComponents instanceof String) {
                        return ExchangeRuleComponents.matchesComponents(stack, (String) finalComponents);
                    } else if (finalComponents instanceof JsonObject) {
                        return ExchangeRuleComponents.matchesComponents(stack, (JsonObject) finalComponents);
                    }
                }

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public static class WeightedItem {
        private String item;
        private int count = 1;
        private int weight = 1;
        private Object components;

        public String getItem() { return item; }
        public void setItem(String item) { this.item = item; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }
        public Object getComponents() { return components; }
        public void setComponents(Object components) { this.components = components; }
    }


    public static class DynamicPricingProperties {
        private int[] threshold; // 阈值数组
        private int[] value; // 对应的价格数组
        private int day; // 清除天数，-1表示永不清除

        public int[] getThreshold() { return threshold; }
        public void setThreshold(int[] threshold) { this.threshold = threshold; }
        public int[] getValue() { return value; }
        public void setValue(int[] value) { this.value = value; }
        public int getDay() { return day; }
        public void setDay(int day) { this.day = day; }
    }

    /**
     * 输出物品类，定义兑换规则中的输出物品规格
     */
    public static class OutputItem {
        private String item; // 物品ID
        private int count = 1; //  数量
        private Object components;  // 支持JsonObject和String
        private boolean coin = false; // 虚拟货币标识符

        // 权重相关字段
        private String type = "item"; // 默认类型设为"item"而不是null
        private List<WeightedItem> items; // 权重物品列表

        // 动态定价相关字段
        private DynamicPricingProperties dynamicProperties;

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

        public void setComponents(Object components) {
            this.components = components;
        }

        public Object getComponents() {
            return components;
        }

        public boolean isCoin() {
            return coin;
        }

        public void setCoin(boolean coin) {
            this.coin = coin;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<WeightedItem> getItems() {
            return items;
        }

        public void setItems(List<WeightedItem> items) {
            this.items = items;
        }

        public DynamicPricingProperties getDynamicProperties() {
            return dynamicProperties;
        }

        public void setDynamicProperties(DynamicPricingProperties dynamicProperties) {
            this.dynamicProperties = dynamicProperties;
        }

        /**
         * 根据配置生成结果物品堆
         * 支持物品ID、数量和组件数据的完整构建
         *
         * @return 构建成功的物品堆，失败时返回空物品堆
         */
        public ItemStack getResultStack() {
            try {
                // 虚拟货币兑换模式（包括动态定价+虚拟货币模式）
                if (this.coin) {
                    // 直接返回空物品堆
                    return ItemStack.EMPTY;
                }

                // 动态定价模式处理
                if ("dynamic_pricing".equals(this.type) && this.dynamicProperties != null) {
                    // 创建基础物品堆
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

                    // 处理组件
                    Object finalComponents = componentString != null ? componentString : components;
                    if (finalComponents != null) {
                        if (finalComponents instanceof JsonObject) {
                            ExchangeRuleComponents.applyComponents(resultStack, (JsonObject) finalComponents);
                        } else if (finalComponents instanceof String componentStr) {
                            if (!componentStr.isEmpty()) {
                                if (componentStr.trim().startsWith("{") && componentStr.trim().endsWith("}")) {
                                    JsonObject jsonObject = JsonParser.parseString(componentStr).getAsJsonObject();
                                    ExchangeRuleComponents.applyComponents(resultStack, jsonObject);
                                } else {
                                    ExchangeRuleComponents.applyComponents(resultStack, componentStr);
                                }
                            }
                        }
                    }

                    return resultStack;
                }

                // 权重模式处理
                if ("weight".equals(this.type) && this.items != null && !this.items.isEmpty()) {
                    return getRandomWeightedItem();
                }

                // 普通物品模式
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

                // 处理不同类型的组件
                Object finalComponents = componentString != null ? componentString : components;
                if (finalComponents != null) {
                    if (finalComponents instanceof JsonObject) {
                        // 直接是JsonObject对象
                        ExchangeRuleComponents.applyComponents(resultStack, (JsonObject) finalComponents);
                    } else if (finalComponents instanceof String componentStr) {
                        // 字符串格式
                        if (!componentStr.isEmpty()) {
                            if (componentStr.trim().startsWith("{") && componentStr.trim().endsWith("}")) {
                                JsonObject jsonObject = JsonParser.parseString(componentStr).getAsJsonObject();
                                ExchangeRuleComponents.applyComponents(resultStack, jsonObject);
                            } else {
                                ExchangeRuleComponents.applyComponents(resultStack, componentStr);
                            }
                        }
                    }
                }

                return resultStack;
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }

        /**
         * 根据权重随机选择一个物品
         * @return 随机选中的物品堆
         */
        public ItemStack getRandomWeightedItem() {
            try {
                // 计算总权重
                int totalWeight = 0;
                for (WeightedItem weightedItem : items) {
                    totalWeight += weightedItem.getWeight();
                }

                if (totalWeight <= 0) {
                    return ItemStack.EMPTY;
                }

                // 生成随机数
                int randomValue = new Random().nextInt(totalWeight);

                // 根据权重选择物品
                int currentWeight = 0;
                for (WeightedItem weightedItem : items) {
                    currentWeight += weightedItem.getWeight();
                    if (randomValue < currentWeight) {
                        // 创建选中的物品
                        ResourceLocation itemResource = ResourceLocation.tryParse(weightedItem.getItem());
                        if (itemResource == null) {
                            continue;
                        }

                        Item resultItem = BuiltInRegistries.ITEM.get(itemResource);
                        ItemStack resultStack = new ItemStack(resultItem, weightedItem.getCount());

                        // 应用组件
                        Object components = weightedItem.getComponents();
                        if (components != null) {
                            if (components instanceof JsonObject) {
                                ExchangeRuleComponents.applyComponents(resultStack, (JsonObject) components);
                            } else if (components instanceof String componentStr) {
                                if (!componentStr.isEmpty()) {
                                    if (componentStr.trim().startsWith("{") && componentStr.trim().endsWith("}")) {
                                        JsonObject jsonObject = JsonParser.parseString(componentStr).getAsJsonObject();
                                        ExchangeRuleComponents.applyComponents(resultStack, jsonObject);
                                    } else {
                                        ExchangeRuleComponents.applyComponents(resultStack, componentStr);
                                    }
                                }
                            }
                        }

                        return resultStack;
                    }
                }

                return ItemStack.EMPTY;
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }

        /**
         * 根据动态定价规则计算当前应该输出的数量
         * @param soldCount 已售出的数量
         * @return 根据阈值计算出的输出数量
         */
        public int getDynamicCount(int soldCount) {
            if (dynamicProperties == null ||
                    dynamicProperties.getThreshold() == null ||
                    dynamicProperties.getValue() == null) {
                return count; // 如果没有动态定价配置，返回默认数量
            }

            int[] thresholds = dynamicProperties.getThreshold();
            int[] values = dynamicProperties.getValue();

            // 如果阈值数组为空或长度不匹配，返回默认数量
            if (thresholds.length == 0 || thresholds.length != values.length) {
                return count;
            }

            // 分段定价逻辑：从低到高检查阈值区间
            for (int i = 0; i < thresholds.length; i++) {
                // 如果售出数量小于当前阈值，则使用对应的价值
                if (soldCount < thresholds[i]) {
                    return values[i];
                }
            }

            // 如果售出数量大于等于所有阈值，使用最低价值
            return values[values.length-1];
        }
    }
}