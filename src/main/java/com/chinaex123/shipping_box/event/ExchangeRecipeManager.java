package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 兑换配方管理器
 * <p>
 * 负责加载、解析和管理物品兑换规则
 * 通过资源重载系统动态加载配置文件
 * 提供配方匹配和物品消耗功能
 * 支持物品ID、标签和组件三种方式定义输入物品
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID)
public class ExchangeRecipeManager extends SimplePreparableReloadListener<List<ExchangeRule>> {

    /** JSON解析器实例 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 配置文件夹路径 */
    private static final String CONFIG_FOLDER = "exchange_rules";

    /** 当前生效的兑换规则列表 */
    private static List<ExchangeRule> currentRules = new ArrayList<>();

    /** 存储待发送的错误信息 */
    private static final List<String> pendingErrorMessages = new ArrayList<>();

    /**
     * 准备阶段：从资源配置中加载并解析兑换规则
     *
     * @param resourceManager 资源管理器，用于访问配置文件
     * @param profiler 性能分析器，用于监控加载性能
     * @return 解析后的有效兑换规则列表
     */
    @Override
    protected List<ExchangeRule> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<ExchangeRule> rules = new ArrayList<>();
        List<String> currentErrors = new ArrayList<>();

        try {
            // 遍历所有匹配的资源配置文件
            var resources = resourceManager.listResources(CONFIG_FOLDER, path -> path.getPath().endsWith(".json"));

            for (ResourceLocation resourceLocation : resources.keySet()) {
                try {
                    // 正确处理Optional<Resource>
                    Optional<Resource> resourceOptional = resourceManager.getResource(resourceLocation);
                    if (resourceOptional.isPresent()) {
                        Resource resource = resourceOptional.get();
                        try (InputStream inputStream = resource.open();
                             BufferedReader reader = new BufferedReader(
                                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                            // 解析JSON配置文件
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                            // 解析规则数组
                            if (json.has("rules") && json.get("rules").isJsonArray()) {
                                JsonArray rulesArray = json.getAsJsonArray("rules");

                                int ruleIndex = 0;
                                for (JsonElement element : rulesArray) {
                                    try {
                                        JsonObject ruleObj = element.getAsJsonObject();

                                        ExchangeRule rule = parseRule(ruleObj);

                                        if (validateRule(rule)) {
                                            rules.add(rule);
                                        } else {
                                            currentErrors.add("Rule " + (ruleIndex + 1) + " validation failed in " + resourceLocation.getPath());
                                        }
                                    } catch (Exception e) {
                                        currentErrors.add("Rule " + (ruleIndex + 1) + " parse error: " + e.getMessage() + " in " + resourceLocation.getPath());
                                    }
                                    ruleIndex++;
                                }
                            } else {
                                currentErrors.add("Missing 'rules' array in " + resourceLocation.getPath());
                            }
                        }
                    }
                } catch (Exception e) {
                    currentErrors.add("Resource load error for " + resourceLocation.getPath() + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            currentErrors.add("Scan error: " + e.getMessage());
        }

        // 将错误信息添加到待发送列表
        if (!currentErrors.isEmpty()) {
            synchronized (pendingErrorMessages) {
                pendingErrorMessages.addAll(currentErrors);
            }
        }

        return rules;
    }

    /**
     * 服务器tick事件监听器
     * 用于发送积累的错误信息给在线玩家
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!pendingErrorMessages.isEmpty()) {
            synchronized (pendingErrorMessages) {
                // 只有当有玩家在线时才发送消息
                if (ServerLifecycleHooks.getCurrentServer() != null && !ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().isEmpty()) {
                    for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                        // 发送标题提示
                        player.displayClientMessage(Component.translatable("message.shipping_box.recipe_error_title"), false);

                        // 发送具体错误信息
                        for (String errorMsg : pendingErrorMessages) {
                            player.displayClientMessage(Component.literal("§c" + errorMsg), false);
                        }

                        // 发送帮助信息
                        player.displayClientMessage(Component.translatable("message.shipping_box.recipe_error_help"), false);
                    }
                    // 清空已发送的错误信息
                    pendingErrorMessages.clear();
                }
            }
        }
    }

    /**
     * 解析单个兑换规则JSON对象
     *
     * @param json 规则JSON对象
     * @return 解析后的兑换规则实例
     */
    private ExchangeRule parseRule(JsonObject json) {
        ExchangeRule rule = new ExchangeRule();
        List<ExchangeRule.InputItem> inputs = new ArrayList<>();

        // 解析输入物品列表
        if (json.has("input") && json.get("input").isJsonArray()) {
            // 多个输入物品
            for (JsonElement element : json.getAsJsonArray("input")) {
                JsonObject inputObj = element.getAsJsonObject();
                ExchangeRule.InputItem input = parseInputItem(inputObj);
                inputs.add(input);
            }
        } else if (json.has("input") && json.get("input").isJsonObject()) {
            // 单个输入物品
            JsonObject inputObj = json.getAsJsonObject("input");
            ExchangeRule.InputItem input = parseInputItem(inputObj);
            inputs.add(input);
        }

        rule.setInputs(inputs);

        // 解析输出物品
        JsonObject outputObj = json.getAsJsonObject("output");
        ExchangeRule.OutputItem output = parseOutputItem(outputObj);
        rule.setOutput(output);

        return rule;
    }

    /**
     * 解析输入物品JSON对象
     * 支持标签、物品ID和组件等多种定义方式
     *
     * @param inputObj 输入物品JSON对象
     * @return 解析后的输入物品实例
     */
    private ExchangeRule.InputItem parseInputItem(JsonObject inputObj) {
        ExchangeRule.InputItem input = new ExchangeRule.InputItem();

        // 支持标签
        if (inputObj.has("tag")) {
            input.setTag(inputObj.get("tag").getAsString());
        }
        // 支持物品ID
        else if (inputObj.has("item")) {
            input.setItem(inputObj.get("item").getAsString());
        }

        // 正确处理components字段的类型
        if (inputObj.has("components")) {
            JsonElement componentsElement = inputObj.get("components");
            if (componentsElement.isJsonObject()) {
                // 直接保存JsonObject
                input.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                // 字符串格式
                input.setComponents(componentsElement.getAsString());
            }
        }

        if (inputObj.has("count")) {
            input.setCount(inputObj.get("count").getAsInt());
        }

        return input;
    }

    /**
     * 解析输出物品JSON对象
     *
     * @param outputObj 输出物品JSON对象
     * @return 解析后的输出物品实例
     */
    private ExchangeRule.OutputItem parseOutputItem(JsonObject outputObj) {
        ExchangeRule.OutputItem output = new ExchangeRule.OutputItem();

        // 首先检查是否为动态定价模式（优先处理）
        if (outputObj.has("type") && "dynamic_pricing".equals(outputObj.get("type").getAsString())) {
            output.setType("dynamic_pricing");

            // 检查是否为动态定价+虚拟货币模式
            if (outputObj.has("coin") && outputObj.get("coin").getAsBoolean()) {
                output.setCoin(true);
                // 虚拟货币模式下不需要item字段，value数组定义了数量
            } else {
                // 普通动态定价模式需要item字段
                if (outputObj.has("item")) {
                    output.setItem(outputObj.get("item").getAsString());
                }
            }

            // 解析动态定价属性
            if (outputObj.has("dynamic_properties") && outputObj.get("dynamic_properties").isJsonObject()) {
                JsonObject dynamicPropsObj = outputObj.getAsJsonObject("dynamic_properties");

                ExchangeRule.DynamicPricingProperties dynamicProps = new ExchangeRule.DynamicPricingProperties();

                // 解析阈值数组
                if (dynamicPropsObj.has("threshold") && dynamicPropsObj.get("threshold").isJsonArray()) {
                    JsonArray thresholdArray = dynamicPropsObj.getAsJsonArray("threshold");
                    int[] thresholds = new int[thresholdArray.size()];
                    for (int i = 0; i < thresholdArray.size(); i++) {
                        thresholds[i] = thresholdArray.get(i).getAsInt();
                    }
                    dynamicProps.setThreshold(thresholds);
                }

                // 解析价值数组
                if (dynamicPropsObj.has("value") && dynamicPropsObj.get("value").isJsonArray()) {
                    JsonArray valueArray = dynamicPropsObj.getAsJsonArray("value");
                    int[] values = new int[valueArray.size()];
                    for (int i = 0; i < valueArray.size(); i++) {
                        values[i] = valueArray.get(i).getAsInt();
                    }
                    dynamicProps.setValue(values);
                }

                // 解析天数
                if (dynamicPropsObj.has("day")) {
                    dynamicProps.setDay(dynamicPropsObj.get("day").getAsInt());
                }

                output.setDynamicProperties(dynamicProps);
            }

            return output;
        }

        // 处理纯虚拟货币模式（非动态定价）
        if (outputObj.has("coin") && outputObj.get("coin").getAsBoolean()) {
            output.setCoin(true);
            if (outputObj.has("count")) {
                int count = outputObj.get("count").getAsInt();
                output.setCount(count);
            }
            return output;
        }

        // 处理权重模式
        if (outputObj.has("type") && "weight".equals(outputObj.get("type").getAsString())) {
            output.setType("weight");

            // 解析权重物品列表
            if (outputObj.has("items") && outputObj.get("items").isJsonArray()) {
                List<ExchangeRule.WeightedItem> weightedItems = new ArrayList<>();
                JsonArray itemsArray = outputObj.getAsJsonArray("items");

                for (JsonElement itemElement : itemsArray) {
                    JsonObject itemObj = itemElement.getAsJsonObject();
                    ExchangeRule.WeightedItem weightedItem = parseWeightedItem(itemObj);
                    weightedItems.add(weightedItem);
                }

                output.setItems(weightedItems);
            }

            return output;
        }

        // 普通物品模式
        if (outputObj.has("item")) {
            output.setItem(outputObj.get("item").getAsString());
        }

        // 处理components字段的类型
        if (outputObj.has("components")) {
            JsonElement componentsElement = outputObj.get("components");
            if (componentsElement.isJsonObject()) {
                // 直接保存JsonObject
                output.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                // 字符串格式
                output.setComponents(componentsElement.getAsString());
            }
        }

        if (outputObj.has("count")) {
            output.setCount(outputObj.get("count").getAsInt());
        }

        return output;
    }

    /**
     * 解析权重物品JSON对象
     *
     * @param itemObj 权重物品JSON对象
     * @return 解析后的权重物品实例
     */
    private ExchangeRule.WeightedItem parseWeightedItem(JsonObject itemObj) {
        ExchangeRule.WeightedItem weightedItem = new ExchangeRule.WeightedItem();

        // 解析基本属性
        if (itemObj.has("item")) {
            weightedItem.setItem(itemObj.get("item").getAsString());
        }
        if (itemObj.has("count")) {
            weightedItem.setCount(itemObj.get("count").getAsInt());
        }
        if (itemObj.has("weight")) {
            weightedItem.setWeight(itemObj.get("weight").getAsInt());
        }

        // 解析组件
        if (itemObj.has("components")) {
            JsonElement componentsElement = itemObj.get("components");
            if (componentsElement.isJsonObject()) {
                weightedItem.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                weightedItem.setComponents(componentsElement.getAsString());
            }
        }

        return weightedItem;
    }

    /**
     * 验证兑换规则的有效性
     * 检查输入和输出物品是否都有效
     *
     * @param rule 要验证的兑换规则
     * @return 规则有效返回true，否则返回false
     */
    private boolean validateRule(ExchangeRule rule) {
        // 验证所有输入物品
        for (ExchangeRule.InputItem input : rule.getInputs()) {
            if (!validateInputItem(input)) {
                return false;
            }
        }

        // 验证输出物品
        return validateOutputItem(rule.getOutputItem());
    }

    /**
     * 验证输入物品的有效性
     * 支持标签和物品ID两种验证方式
     *
     * @param input 输入物品对象
     * @return 物品有效返回true，否则返回false
     */
    private boolean validateInputItem(ExchangeRule.InputItem input) {
        // 如果是标签形式
        if (input.getTag() != null && !input.getTag().isEmpty()) {
            try {
                String tagId = input.getTag().startsWith("#") ? input.getTag().substring(1) : input.getTag();
                ResourceLocation tagResource = ResourceLocation.tryParse(tagId);
                return tagResource != null;
                // 标签验证通过
            } catch (Exception e) {
                return false;
            }
        }
        // 如果是物品ID形式
        else if (input.getItem() != null && !input.getItem().isEmpty()) {
            return validateItemWithComponents(input.getItem());
        }

        return false;
    }

    /**
     * 验证输出物品的有效性
     *
     * @param output 输出物品对象
     * @return 物品有效返回true，否则返回false
     */
    private boolean validateOutputItem(ExchangeRule.OutputItem output) {
        // 虚拟货币模式下不需要验证物品ID
        if (output.isCoin()) {
            // 对于动态定价+虚拟货币模式，只需要验证动态属性
            if ("dynamic_pricing".equals(output.getType())) {
                if (output.getDynamicProperties() == null) {
                    return false;
                }

                int[] thresholds = output.getDynamicProperties().getThreshold();
                int[] values = output.getDynamicProperties().getValue();

                if (thresholds == null || values == null || thresholds.length != values.length) {
                    return false;
                }

                // 验证阈值数组是否递增
                for (int i = 1; i < thresholds.length; i++) {
                    if (thresholds[i] <= thresholds[i-1]) {
                        return false;
                    }
                }

                return true; // 虚拟货币模式不需要验证具体物品
            }
            return true; // 普通虚拟货币模式
        }

        // 动态定价模式验证（非虚拟货币）
        if ("dynamic_pricing".equals(output.getType())) {
            if (output.getItem() == null || output.getItem().isEmpty()) {
                return false;
            }

            if (output.getDynamicProperties() == null) {
                return false;
            }

            int[] thresholds = output.getDynamicProperties().getThreshold();
            int[] values = output.getDynamicProperties().getValue();

            if (thresholds == null || values == null || thresholds.length != values.length) {
                return false;
            }

            // 验证阈值数组是否递增
            for (int i = 1; i < thresholds.length; i++) {
                if (thresholds[i] <= thresholds[i-1]) {
                    return false;
                }
            }

            return validateItemWithComponents(output.getItem());
        }

        // 权重模式验证
        if ("weight".equals(output.getType()) && output.getItems() != null) {
            for (ExchangeRule.WeightedItem weightedItem : output.getItems()) {
                if (!validateItemWithComponents(weightedItem.getItem())) {
                    return false;
                }
            }
            return true;
        }

        if (output.getItem() == null || output.getItem().isEmpty()) {
            return false;
        }

        return validateItemWithComponents(output.getItem());
    }

    /**
     * 验证可能包含组件的物品ID字符串
     * 支持物品ID与组件信息的组合格式验证
     *
     * @param itemString 物品字符串（可能包含组件信息）
     * @return 物品有效返回true，否则返回false
     */
    private boolean validateItemWithComponents(String itemString) {
        try {
            String itemId = itemString;

            // 检查是否包含组件部分 [ ... ]
            int componentStart = itemString.indexOf('[');
            int componentEnd = itemString.lastIndexOf(']');

            if (componentStart > 0 && componentEnd > componentStart) {
                itemId = itemString.substring(0, componentStart);
                String componentString = itemString.substring(componentStart + 1, componentEnd);

                // 验证组件字符串格式
                if (!validateComponentString(componentString)) {
                    return false;
                }
            }

            // 验证物品ID
            ResourceLocation itemResource = ResourceLocation.tryParse(itemId);
            if (itemResource == null) {
                return false;
            }

            return BuiltInRegistries.ITEM.containsKey(itemResource);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证组件字符串的格式有效性
     * 检查组件名称和值的基本格式是否正确
     *
     * @param componentString 组件字符串，格式为"name=value"或"name1=value1,name2=value2"
     * @return 格式有效返回true，否则返回false
     */
    private boolean validateComponentString(String componentString) {
        if (componentString == null || componentString.isEmpty()) {
            return true;
        }

        // 简单验证：确保有等号，并且格式基本正确
        String[] components = componentString.split(",");
        for (String comp : components) {
            comp = comp.trim();
            if (comp.isEmpty()) continue;

            // 检查是否有等号
            int equalsIndex = comp.indexOf('=');
            if (equalsIndex <= 0) {
                return false;
            }

            // 检查组件名称
            String componentName = comp.substring(0, equalsIndex).trim();
            ResourceLocation componentId = ResourceLocation.tryParse(componentName);
            if (componentId == null) {
                return false;
            }

            // 检查组件值（这里只做基本格式检查）
            String componentValue = comp.substring(equalsIndex + 1).trim();
            if (componentValue.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 应用阶段：将解析好的规则应用到当前环境中
     *
     * @param rules 解析后的规则列表
     * @param resourceManager 资源管理器
     * @param profiler 性能分析器
     */
    @Override
    protected void apply(List<ExchangeRule> rules, ResourceManager resourceManager, ProfilerFiller profiler) {
        currentRules = rules;
    }

    /**
     * 获取当前所有有效的兑换规则
     *
     * @return 当前规则列表的不可变视图
     */
    public static List<ExchangeRule> getRules() {
        return currentRules;
    }

    /**
     * 查找匹配给定物品列表的兑换规则
     *
     * @param availableStacks 可用物品列表
     * @return 匹配的规则，如果没有匹配则返回null
     */
    public static ExchangeRule findMatchingRule(List<ItemStack> availableStacks) {
        for (int i = 0; i < currentRules.size(); i++) {
            ExchangeRule rule = currentRules.get(i);

            if (matchesRule(rule, availableStacks)) {
                return rule;
            }
        }

        return null;
    }

    /**
     * 检查给定物品列表是否满足指定规则的要求
     *
     * @param rule 兑换规则
     * @param availableStacks 可用物品列表
     * @return 满足规则返回true，否则返回false
     */
    private static boolean matchesRule(ExchangeRule rule, List<ItemStack> availableStacks) {
        // 为每个输入物品创建计数器
        int[] requiredCounts = new int[rule.getInputs().size()];
        boolean[] satisfied = new boolean[rule.getInputs().size()];

        // 初始化所需数量
        for (int i = 0; i < rule.getInputs().size(); i++) {
            requiredCounts[i] = rule.getInputs().get(i).getCount();
        }

        // 遍历可用物品，尝试满足需求
        for (ItemStack stack : availableStacks) {
            if (stack.isEmpty()) continue;

            // 检查这个物品能否满足任何未满足的需求
            for (int i = 0; i < rule.getInputs().size(); i++) {
                if (!satisfied[i] && rule.getInputs().get(i).matches(stack)) {
                    int canConsume = Math.min(stack.getCount(), requiredCounts[i]);
                    requiredCounts[i] -= canConsume;

                    if (requiredCounts[i] <= 0) {
                        satisfied[i] = true;
                    }
                    break; // 一个物品只能满足一个需求
                }
            }
        }

        // 检查所有需求是否都满足
        boolean allSatisfied = true;
        for (int i = 0; i < rule.getInputs().size(); i++) {
            if (!satisfied[i]) {
                allSatisfied = false;
            }
        }

        return allSatisfied;
    }

    /**
     * 消耗指定规则所需的输入物品
     *
     * @param rule 兑换规则
     * @param availableStacks 可用物品列表
     * @return 消耗后剩余的物品列表
     */
    public static List<ItemStack> consumeInputs(ExchangeRule rule, List<ItemStack> availableStacks) {
        List<ItemStack> remaining = new ArrayList<>(availableStacks);

        for (ExchangeRule.InputItem required : rule.getInputs()) {
            for (int j = 0; j < remaining.size(); j++) {
                ItemStack stack = remaining.get(j);
                if (required.matches(stack)) {
                    if (stack.getCount() > required.getCount()) {
                        stack.setCount(stack.getCount() - required.getCount());
                    } else if (stack.getCount() == required.getCount()) {
                        remaining.remove(j);
                    }
                    break;
                }
            }
        }

        return remaining;
    }

    /**
     * 将当前规则序列化为JSON字符串
     * 用于网络传输到客户端
     *
     * @return 序列化的JSON字符串
     */
    public static String serializeRulesToJson() {
        try {
            JsonObject root = new JsonObject();
            JsonArray rulesArray = new JsonArray();

            for (ExchangeRule rule : currentRules) {
                JsonObject ruleObj = new JsonObject();

                // 序列化输入物品
                if (rule.getInputs().size() == 1) {
                    // 单个输入
                    ruleObj.add("input", serializeInputItem(rule.getInputs().getFirst()));
                } else {
                    // 多个输入
                    JsonArray inputsArray = new JsonArray();
                    for (ExchangeRule.InputItem input : rule.getInputs()) {
                        inputsArray.add(serializeInputItem(input));
                    }
                    ruleObj.add("input", inputsArray);
                }

                // 序列化输出物品
                ruleObj.add("output", serializeOutputItem(rule.getOutputItem()));

                rulesArray.add(ruleObj);
            }

            root.add("rules", rulesArray);
            return GSON.toJson(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 从JSON字符串反序列化规则并在客户端设置
     *
     * @param json JSON字符串
     */
    public static void setClientRules(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray rulesArray = root.getAsJsonArray("rules");

            List<ExchangeRule> clientRules = new ArrayList<>();

            for (JsonElement element : rulesArray) {
                JsonObject ruleObj = element.getAsJsonObject();
                ExchangeRule rule = new ExchangeRule();

                // 反序列化输入物品
                List<ExchangeRule.InputItem> inputs = new ArrayList<>();
                if (ruleObj.get("input").isJsonArray()) {
                    // 多个输入
                    for (JsonElement inputElement : ruleObj.getAsJsonArray("input")) {
                        inputs.add(deserializeInputItem(inputElement.getAsJsonObject()));
                    }
                } else {
                    // 单个输入
                    inputs.add(deserializeInputItem(ruleObj.getAsJsonObject("input")));
                }
                rule.setInputs(inputs);

                // 反序列化输出物品
                rule.setOutput(deserializeOutputItem(ruleObj.getAsJsonObject("output")));

                clientRules.add(rule);
            }

            // 在客户端设置规则
            currentRules = clientRules;
        } catch (Exception e) {
            // 静默处理反序列化错误
        }
    }

    /**
     * 序列化输入物品为JSON对象
     * 将ExchangeRule.InputItem实例转换为JSON格式
     *
     * @param input 要序列化的输入物品实例
     * @return 包含输入物品配置的JSON对象
     */
    private static JsonObject serializeInputItem(ExchangeRule.InputItem input) {
        JsonObject obj = new JsonObject();

        if (input.getItem() != null) {
            obj.addProperty("item", input.getItem());
        }
        if (input.getTag() != null) {
            obj.addProperty("tag", input.getTag());
        }

        // 处理组件配置
        if (input.getComponents() != null) {
            if (input.getComponents() instanceof JsonObject) {
                obj.add("components", (JsonObject) input.getComponents());
            } else if (input.getComponents() instanceof String) {
                obj.addProperty("components", (String) input.getComponents());
            }
        }

        obj.addProperty("count", input.getCount());

        return obj;
    }

    /**
     * 序列化输出物品为JSON对象
     * 将ExchangeRule.OutputItem实例转换为JSON格式
     *
     * @param output 要序列化的输出物品实例
     * @return 包含输出物品配置的JSON对象
     */
    private static JsonObject serializeOutputItem(ExchangeRule.OutputItem output) {
        JsonObject obj = new JsonObject();

        // 虚拟货币模式
        if (output.isCoin()) {
            obj.addProperty("coin", true);
            obj.addProperty("count", output.getCount());

            // 如果是动态定价+虚拟货币模式，也要保留type信息
            if ("dynamic_pricing".equals(output.getType())) {
                obj.addProperty("type", "dynamic_pricing");
                // 注意：虚拟货币模式下item可以为null，不要强制添加

                // 序列化动态定价属性
                if (output.getDynamicProperties() != null) {
                    JsonObject dynamicPropsObj = serializeDynamicPricingProperties(output.getDynamicProperties());
                    obj.add("dynamic_properties", dynamicPropsObj);
                }
            }
            return obj;
        }

        // 动态定价模式（非虚拟货币）
        if ("dynamic_pricing".equals(output.getType()) && output.getDynamicProperties() != null) {
            obj.addProperty("type", "dynamic_pricing");
            if (output.getItem() != null) {
                obj.addProperty("item", output.getItem());
            }

            // 序列化动态定价属性
            JsonObject dynamicPropsObj = serializeDynamicPricingProperties(output.getDynamicProperties());
            obj.add("dynamic_properties", dynamicPropsObj);
            return obj;
        }

        // 权重模式
        if ("weight".equals(output.getType()) && output.getItems() != null) {
            obj.addProperty("type", "weight");
            JsonArray itemsArray = new JsonArray();

            for (ExchangeRule.WeightedItem weightedItem : output.getItems()) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("item", weightedItem.getItem());
                itemObj.addProperty("count", weightedItem.getCount());
                itemObj.addProperty("weight", weightedItem.getWeight());

                // 序列化组件
                if (weightedItem.getComponents() != null) {
                    if (weightedItem.getComponents() instanceof JsonObject) {
                        itemObj.add("components", (JsonObject) weightedItem.getComponents());
                    } else if (weightedItem.getComponents() instanceof String) {
                        itemObj.addProperty("components", (String) weightedItem.getComponents());
                    }
                }

                itemsArray.add(itemObj);
            }

            obj.add("items", itemsArray);
            return obj;
        }

        // 普通物品模式
        obj.addProperty("item", output.getItem());
        obj.addProperty("count", output.getCount());

        // 处理组件配置
        if (output.getComponents() != null) {
            if (output.getComponents() instanceof JsonObject) {
                obj.add("components", (JsonObject) output.getComponents());
            } else if (output.getComponents() instanceof String) {
                obj.addProperty("components", (String) output.getComponents());
            }
        }

        return obj;
    }

    /**
     * 序列化动态定价属性为JSON对象
     * 将ExchangeRule.DynamicPricingProperties实例转换为JSON格式
     *
     * @param props 要序列化的动态定价属性实例
     * @return 包含动态定价配置的JSON对象
     */
    private static JsonObject serializeDynamicPricingProperties(ExchangeRule.DynamicPricingProperties props) {
        JsonObject dynamicPropsObj = new JsonObject();

        // 序列化阈值数组
        if (props.getThreshold() != null) {
            JsonArray thresholdArray = new JsonArray();
            for (int threshold : props.getThreshold()) {
                thresholdArray.add(threshold);
            }
            dynamicPropsObj.add("threshold", thresholdArray);
        }

        // 序列化价值数组
        if (props.getValue() != null) {
            JsonArray valueArray = new JsonArray();
            for (int value : props.getValue()) {
                valueArray.add(value);
            }
            dynamicPropsObj.add("value", valueArray);
        }

        // 序列化天数
        dynamicPropsObj.addProperty("day", props.getDay());

        return dynamicPropsObj;
    }

    /**
     * 反序列化输入物品配置
     * 将JSON对象转换为ExchangeRule.InputItem实例
     *
     * @param obj 包含输入物品配置的JSON对象
     * @return 配置好的输入物品实例
     */
    private static ExchangeRule.InputItem deserializeInputItem(JsonObject obj) {
        ExchangeRule.InputItem input = new ExchangeRule.InputItem();

        if (obj.has("item")) {
            input.setItem(obj.get("item").getAsString());
        }
        if (obj.has("tag")) {
            input.setTag(obj.get("tag").getAsString());
        }

        // 处理组件配置
        if (obj.has("components")) {
            JsonElement componentsElement = obj.get("components");
            if (componentsElement.isJsonObject()) {
                input.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                input.setComponents(componentsElement.getAsString());
            }
        }

        if (obj.has("count")) {
            input.setCount(obj.get("count").getAsInt());
        }

        return input;
    }

    /**
     * 反序列化输出物品配置
     * 将JSON对象转换为ExchangeRule.OutputItem实例
     *
     * @param obj 包含输出物品配置的JSON对象
     * @return 配置好的输出物品实例
     */
    private static ExchangeRule.OutputItem deserializeOutputItem(JsonObject obj) {
        ExchangeRule.OutputItem output = new ExchangeRule.OutputItem();

        // 处理虚拟货币标识符
        if (obj.has("coin") && obj.get("coin").getAsBoolean()) {
            output.setCoin(true);
            if (obj.has("count")) {
                output.setCount(obj.get("count").getAsInt());
            }

            // 检查是否为动态定价+虚拟货币模式
            if (obj.has("type") && "dynamic_pricing".equals(obj.get("type").getAsString())) {
                output.setType("dynamic_pricing");

                // 反序列化动态定价属性
                if (obj.has("dynamic_properties") && obj.get("dynamic_properties").isJsonObject()) {
                    JsonObject dynamicPropsObj = obj.getAsJsonObject("dynamic_properties");
                    ExchangeRule.DynamicPricingProperties dynamicProps = new ExchangeRule.DynamicPricingProperties();

                    // 反序列化阈值数组
                    if (dynamicPropsObj.has("threshold") && dynamicPropsObj.get("threshold").isJsonArray()) {
                        JsonArray thresholdArray = dynamicPropsObj.getAsJsonArray("threshold");
                        int[] thresholds = new int[thresholdArray.size()];
                        for (int i = 0; i < thresholdArray.size(); i++) {
                            thresholds[i] = thresholdArray.get(i).getAsInt();
                        }
                        dynamicProps.setThreshold(thresholds);
                    }

                    // 反序列化价值数组
                    if (dynamicPropsObj.has("value") && dynamicPropsObj.get("value").isJsonArray()) {
                        JsonArray valueArray = dynamicPropsObj.getAsJsonArray("value");
                        int[] values = new int[valueArray.size()];
                        for (int i = 0; i < valueArray.size(); i++) {
                            values[i] = valueArray.get(i).getAsInt();
                        }
                        dynamicProps.setValue(values);
                    }

                    // 反序列化天数
                    if (dynamicPropsObj.has("day")) {
                        dynamicProps.setDay(dynamicPropsObj.get("day").getAsInt());
                    }

                    output.setDynamicProperties(dynamicProps);
                }
            }
            return output;
        }

        // 处理动态定价模式（非虚拟货币）
        if (obj.has("type") && "dynamic_pricing".equals(obj.get("type").getAsString())) {
            output.setType("dynamic_pricing");

            if (obj.has("item")) {
                output.setItem(obj.get("item").getAsString());
            }

            // 反序列化动态定价属性
            if (obj.has("dynamic_properties") && obj.get("dynamic_properties").isJsonObject()) {
                JsonObject dynamicPropsObj = obj.getAsJsonObject("dynamic_properties");
                ExchangeRule.DynamicPricingProperties dynamicProps = new ExchangeRule.DynamicPricingProperties();

                // 反序列化阈值数组
                if (dynamicPropsObj.has("threshold") && dynamicPropsObj.get("threshold").isJsonArray()) {
                    JsonArray thresholdArray = dynamicPropsObj.getAsJsonArray("threshold");
                    int[] thresholds = new int[thresholdArray.size()];
                    for (int i = 0; i < thresholdArray.size(); i++) {
                        thresholds[i] = thresholdArray.get(i).getAsInt();
                    }
                    dynamicProps.setThreshold(thresholds);
                }

                // 反序列化价值数组
                if (dynamicPropsObj.has("value") && dynamicPropsObj.get("value").isJsonArray()) {
                    JsonArray valueArray = dynamicPropsObj.getAsJsonArray("value");
                    int[] values = new int[valueArray.size()];
                    for (int i = 0; i < valueArray.size(); i++) {
                        values[i] = valueArray.get(i).getAsInt();
                    }
                    dynamicProps.setValue(values);
                }

                // 反序列化天数
                if (dynamicPropsObj.has("day")) {
                    dynamicProps.setDay(dynamicPropsObj.get("day").getAsInt());
                }

                output.setDynamicProperties(dynamicProps);
            }

            return output;
        }

        // 处理权重模式
        if (obj.has("type") && "weight".equals(obj.get("type").getAsString())) {
            output.setType("weight");

            if (obj.has("items") && obj.get("items").isJsonArray()) {
                List<ExchangeRule.WeightedItem> weightedItems = new ArrayList<>();
                JsonArray itemsArray = obj.getAsJsonArray("items");

                for (JsonElement itemElement : itemsArray) {
                    JsonObject itemObj = itemElement.getAsJsonObject();
                    ExchangeRule.WeightedItem weightedItem = deserializeWeightedItem(itemObj);
                    weightedItems.add(weightedItem);
                }

                output.setItems(weightedItems);
            }

            return output;
        }

        // 普通物品模式
        output.setItem(obj.get("item").getAsString());
        output.setCount(obj.get("count").getAsInt());

        // 处理组件配置（如果存在）
        if (obj.has("components")) {
            JsonElement componentsElement = obj.get("components");
            if (componentsElement.isJsonObject()) {
                output.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                output.setComponents(componentsElement.getAsString());
            }
        }

        return output;
    }

    /**
     * 反序列化权重物品配置
     * 将JSON对象转换为ExchangeRule.WeightedItem实例
     *
     * @param itemObj 包含权重物品配置的JSON对象
     * @return 配置好的权重物品实例
     */
    private static ExchangeRule.WeightedItem deserializeWeightedItem(JsonObject itemObj) {
        ExchangeRule.WeightedItem weightedItem = new ExchangeRule.WeightedItem();

        weightedItem.setItem(itemObj.get("item").getAsString());
        weightedItem.setCount(itemObj.get("count").getAsInt());
        weightedItem.setWeight(itemObj.get("weight").getAsInt());

        // 反序列化组件
        if (itemObj.has("components")) {
            JsonElement componentsElement = itemObj.get("components");
            if (componentsElement.isJsonObject()) {
                weightedItem.setComponents(componentsElement.getAsJsonObject());
            } else if (componentsElement.isJsonPrimitive()) {
                weightedItem.setComponents(componentsElement.getAsString());
            }
        }

        return weightedItem;
    }

    /**
     * 资源重载监听器注册事件
     * 将此管理器注册为资源重载监听器
     *
     * @param event 资源重载监听器添加事件
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ExchangeRecipeManager());
    }
}