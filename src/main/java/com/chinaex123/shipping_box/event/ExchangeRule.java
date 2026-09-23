package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.Holder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * 兑换规则类。
 * <p>
 * 定义输入输出物品的匹配条件和动态定价属性。
 * 内部包含输入物品、输出物品、权重物品、动态定价与节气联动等配置结构。
 */
public class ExchangeRule {

    /** 日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeRule.class);

    /** 输入物品列表 */
    private List<InputItem> inputs;
    /** 输出物品配置 */
    private OutputItem output;

    /**
     * 获取输入物品列表。
     *
     * @return 输入物品列表
     */
    public List<InputItem> getInputs() {
        return inputs;
    }

    /**
     * 设置输入物品列表。
     *
     * @param inputs 输入物品列表
     */
    public void setInputs(List<InputItem> inputs) {
        this.inputs = inputs;
    }

    /**
     * 获取输出物品信息。
     *
     * @return 输出物品对象
     */
    public OutputItem getOutputItem() {
        return output;
    }

    /**
     * 设置输出物品信息。
     *
     * @param output 输出物品对象
     */
    public void setOutput(OutputItem output) {
        this.output = output;
    }

    /**
     * 输入物品类，定义兑换规则中的输入物品规格。
     */
    public static class InputItem {

        /** 物品 ID */
        private String item;
        /** 标签 ID */
        private String tag;
        /** 组件配置，支持 JsonObject 和 String */
        private Object components;
        /** 需求数量，默认为 1 */
        private int count = 1;

        /**
         * 获取物品 ID。
         *
         * @return 物品 ID
         */
        public String getItem() {
            return item;
        }

        /**
         * 设置物品 ID。
         *
         * @param item 物品 ID
         */
        public void setItem(String item) {
            this.item = item;
        }

        /**
         * 获取标签 ID。
         *
         * @return 标签 ID
         */
        public String getTag() {
            return tag;
        }

        /**
         * 设置标签 ID。
         *
         * @param tag 标签 ID
         */
        public void setTag(String tag) {
            this.tag = tag;
        }

        /**
         * 设置组件配置。
         *
         * @param components 组件配置对象
         */
        public void setComponents(Object components) {
            this.components = components;
        }

        /**
         * 获取需求数量。
         *
         * @return 需求数量
         */
        public int getCount() {
            return count;
        }

        /**
         * 设置需求数量。
         *
         * @param count 需求数量
         */
        public void setCount(int count) {
            this.count = count;
        }

        /**
         * 获取组件配置。
         *
         * @return 组件配置对象
         */
        public Object getComponents() {
            return components;
        }

        /**
         * 检查物品堆是否匹配此输入要求。
         * <p>
         * 支持物品 ID、标签和组件匹配。
         *
         * @param stack 要检查的物品堆
         * @return 匹配返回 true，否则返回 false
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
         * 检查物品是否匹配指定标签。
         *
         * @param stack 要检查的物品堆
         * @return 匹配返回 true，否则返回 false
         */
        private boolean matchesTag(ItemStack stack) {
            try {
                String tagIdStr = tag.startsWith("#") ? tag.substring(1) : tag;
                Identifier tagId = Identifier.tryParse(tagIdStr);
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
         * 检查物品是否匹配指定 ID 和组件要求。
         *
         * @param stack 要检查的物品堆
         * @return 匹配返回 true，否则返回 false
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

                Identifier itemResource = Identifier.tryParse(itemId);
                if (itemResource == null) {
                    return false;
                }

                Item requiredItem = BuiltInRegistries.ITEM.get(itemResource).map(Holder::value).orElse(null);
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
                ShippingBox.LOGGER.error("[ExchangeRule.matchesItem]在兑换规则中匹配物品失败: {}", e.getMessage());
                return false;
            }
        }
    }

    /**
     * 权重物品类，定义权重随机模式下的单个候选物品。
     */
    public static class WeightedItem {

        /** 物品 ID */
        private String item;
        /** 数量，默认为 1 */
        private int count = 1;
        /** 权重，默认为 1 */
        private int weight = 1;
        /** 组件配置 */
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

    /**
     * 动态定价属性类。
     * <p>
     * 通过阈值数组与价值数组描述分段定价，并记录重置天数。
     */
    public static class DynamicPricingProperties {

        /** 阈值数组 */
        private int[] threshold;
        /** 对应的价格数组 */
        private int[] value;
        /** 清除天数，-1 表示永不清除 */
        private int day;

        public int[] getThreshold() { return threshold; }
        public void setThreshold(int[] threshold) { this.threshold = threshold; }
        public int[] getValue() { return value; }
        public void setValue(int[] value) { this.value = value; }
        public int getDay() { return day; }
        public void setDay(int day) { this.day = day; }
    }

    /**
     * 节气联动配置类。
     * <p>
     * 描述销售季节、是否仅限季节出售，以及应季加成与非应季减益。
     */
    public static class EclipticSeasonsProperties {

        /** 季节列表 [spring, summer, autumn, winter, all] */
        @SerializedName("season")
        private List<String> season;

        /** 仅限当前季节出售 */
        @SerializedName("seasonal_only")
        private boolean seasonal_only = false;

        /** 应季时价格加成百分比 */
        @SerializedName("add_season_bonus")
        private int add_season_bonus = 0;

        /** 非应季时价格减益百分比 */
        @SerializedName("reduce_season_bonus")
        private int reduce_season_bonus = 0;

        /**
         * 无参构造函数，供 Gson 反序列化使用。
         */
        public EclipticSeasonsProperties() {
        }

        public List<String> getSeason() { return season; }
        public void setSeason(List<String> season) { this.season = season; }
        public boolean isSeasonal_only() { return seasonal_only; }
        public void setSeasonal_only(boolean seasonal_only) { this.seasonal_only = seasonal_only; }
        public int getAdd_season_bonus() { return add_season_bonus; }
        public void setAdd_season_bonus(int add_season_bonus) { this.add_season_bonus = add_season_bonus; }
        public int getReduce_season_bonus() { return reduce_season_bonus; }
        public void setReduce_season_bonus(int reduce_season_bonus) { this.reduce_season_bonus = reduce_season_bonus; }
    }

    /**
     * 输出物品类，定义兑换规则中的输出物品规格。
     * <p>
     * 支持普通物品、虚拟货币、权重随机、动态定价与节气联动等模式。
     */
    public static class OutputItem {

        /** 物品 ID */
        private String item;
        /** 数量，默认为 1 */
        private int count = 1;
        /** 组件配置，支持 JsonObject 和 String */
        private Object components;
        /** 虚拟货币标识符 */
        private boolean coin = false;

        // 权重相关字段

        /** 输出模式类型，默认 "item" */
        @SerializedName("type")
        private String type = "item";
        /** 权重物品列表 */
        private List<WeightedItem> items;

        // 动态定价相关字段

        /** 动态定价属性 */
        private DynamicPricingProperties dynamicProperties;

        // 节气联动相关字段

        /** 节气联动属性 */
        @SerializedName("ecliptic_seasons")
        private EclipticSeasonsProperties eclipticSeasonsProperties;

        /**
         * 获取物品 ID。
         *
         * @return 物品 ID
         */
        public String getItem() {
            return item;
        }

        /**
         * 设置物品 ID。
         *
         * @param item 物品 ID
         */
        public void setItem(String item) {
            this.item = item;
        }

        /**
         * 获取数量。
         *
         * @return 数量
         */
        public int getCount() {
            return count;
        }

        /**
         * 设置数量。
         *
         * @param count 数量
         */
        public void setCount(int count) {
            this.count = count;
        }

        /**
         * 设置组件配置。
         *
         * @param components 组件配置对象
         */
        public void setComponents(Object components) {
            this.components = components;
        }

        /**
         * 获取组件配置。
         *
         * @return 组件配置对象
         */
        public Object getComponents() {
            return components;
        }

        /**
         * 是否为虚拟货币模式。
         *
         * @return 是虚拟货币模式返回 true
         */
        public boolean isCoin() {
            return coin;
        }

        /**
         * 设置是否为虚拟货币模式。
         *
         * @param coin 虚拟货币标识
         */
        public void setCoin(boolean coin) {
            this.coin = coin;
        }

        /**
         * 获取输出模式类型。
         *
         * @return 输出模式类型
         */
        public String getType() {
            return type;
        }

        /**
         * 设置输出模式类型。
         *
         * @param type 输出模式类型
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * 获取权重物品列表。
         *
         * @return 权重物品列表
         */
        public List<WeightedItem> getItems() {
            return items;
        }

        /**
         * 设置权重物品列表。
         *
         * @param items 权重物品列表
         */
        public void setItems(List<WeightedItem> items) {
            this.items = items;
        }

        /**
         * 获取动态定价属性。
         *
         * @return 动态定价属性
         */
        public DynamicPricingProperties getDynamicProperties() {
            return dynamicProperties;
        }

        /**
         * 设置动态定价属性。
         *
         * @param dynamicProperties 动态定价属性
         */
        public void setDynamicProperties(DynamicPricingProperties dynamicProperties) {
            this.dynamicProperties = dynamicProperties;
        }

        /**
         * 获取节气联动属性。
         *
         * @return 节气联动属性
         */
        public EclipticSeasonsProperties getEclipticSeasonsProperties() {
            return eclipticSeasonsProperties;
        }

        /**
         * 设置节气联动属性。
         *
         * @param eclipticSeasonsProperties 节气联动属性
         */
        public void setEclipticSeasonsProperties(EclipticSeasonsProperties eclipticSeasonsProperties) {
            this.eclipticSeasonsProperties = eclipticSeasonsProperties;
        }

        /**
         * 根据配置生成结果物品堆。
         * <p>
         * 支持物品 ID、数量和组件数据的完整构建。
         * 虚拟货币模式返回空物品堆；动态定价与普通物品模式构建对应物品；
         * 权重模式委托随机抽取逻辑。
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

                    Identifier itemResource = Identifier.tryParse(itemId);
                    if (itemResource == null) {
                        return ItemStack.EMPTY;
                    }

                    Item resultItem = BuiltInRegistries.ITEM.get(itemResource).map(Holder::value).orElse(null);
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

                Identifier itemResource = Identifier.tryParse(itemId);
                if (itemResource == null) {
                    return ItemStack.EMPTY;
                }

                Item resultItem = BuiltInRegistries.ITEM.get(itemResource).map(Holder::value).orElse(null);
                ItemStack resultStack = new ItemStack(resultItem, count);

                // 处理不同类型的组件
                Object finalComponents = componentString != null ? componentString : components;
                if (finalComponents != null) {
                    if (finalComponents instanceof JsonObject) {
                        // 直接是 JsonObject 对象
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
         * 根据权重随机选择一个物品。
         *
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
                        Identifier itemResource = Identifier.tryParse(weightedItem.getItem());
                        if (itemResource == null) {
                            continue;
                        }

                        Item resultItem = BuiltInRegistries.ITEM.get(itemResource).map(Holder::value).orElse(null);
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
         * 根据动态定价规则计算当前应该输出的数量。
         * <p>
         * 从低到高检查阈值区间：售出数量小于某阈值时取对应价值；
         * 超过所有阈值时取最后一个价值。
         *
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