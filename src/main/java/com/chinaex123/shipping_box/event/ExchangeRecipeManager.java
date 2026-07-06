package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.google.gson.*;
import net.minecraft.ChatFormatting;
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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    private static final int MAX_RULE_COUNT = 1_000_000;

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

        // 加载外部配置目录中的规则（仅当 KubeJS 不存在时，KubeJS 的 kubejs/data/ 目录
        // 会被 resourceManager 作为数据包自动扫描，无需额外加载，否则会导致规则加倍）
        if (!net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            loadConfigRules(rules, currentErrors);
        }

        try {
            // 遍历所有匹配的资源配置文件（数据包）
            var resources = resourceManager.listResources(CONFIG_FOLDER, path -> path.getPath().endsWith(".json"));

            for (ResourceLocation resourceLocation : resources.keySet()) {
                try {
                    // 正确处理 Optional<Resource>
                    Optional<Resource> resourceOptional = resourceManager.getResource(resourceLocation);
                    if (resourceOptional.isPresent()) {
                        Resource resource = resourceOptional.get();
                        try (InputStream inputStream = resource.open();
                             BufferedReader reader = new BufferedReader(
                                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                            // 解析 JSON 配置文件
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
                                            // 详细验证错误信息
                                            String validationError = getValidationErrorDetails(rule);
                                            if (validationError != null && !validationError.isEmpty()) {
                                                currentErrors.add(String.format("error.shipping_box.rule_validation_failed|%d|%s|%s",
                                                        ruleIndex + 1, resourceLocation.getPath(), validationError));
                                            } else {
                                                currentErrors.add(String.format("error.shipping_box.rule_validation_failed|%d|%s|%s",
                                                        ruleIndex + 1, resourceLocation.getPath(), "unknown_error"));
                                            }
                                        }
                                    } catch (JsonParseException e) {
                                        currentErrors.add(String.format("error.shipping_box.json_parse_error|%s|%s",
                                                resourceLocation.getPath(), e.getMessage()));
                                    } catch (Exception e) {
                                        currentErrors.add(String.format("error.shipping_box.rule_parse_error|%s|%s",
                                                resourceLocation.getPath(), e.getMessage()));
                                    }
                                    ruleIndex++;
                                }
                            } else {
                                currentErrors.add(String.format("error.shipping_box.missing_rules_array|%s",
                                        resourceLocation.getPath()));
                            }
                        }
                    }
                } catch (Exception e) {
                    currentErrors.add(String.format("error.shipping_box.resource_load_error|%s|%s",
                            resourceLocation.getPath(), e.getMessage()));
                }
            }

        } catch (Exception e) {
            currentErrors.add(String.format("error.shipping_box.scan_error|%s", e.getMessage()));
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
     * 获取规则验证失败的详细信息
     * @param rule 验证失败的规则
     * @return 详细错误信息键
     */
    private static String getValidationErrorDetails(ExchangeRule rule) {
        try {
            // 检查输入物品
            if (rule.getInputs() == null || rule.getInputs().isEmpty()) {
                return "missing_input";
            }

            for (ExchangeRule.InputItem input : rule.getInputs()) {
                if (input.getItem() == null && input.getTag() == null) {
                    return "invalid_input_item";
                }
                if (input.getItem() != null && !input.getItem().isEmpty()) {
                    if (!BuiltInRegistries.ITEM.containsKey(Objects.requireNonNull(ResourceLocation.tryParse(input.getItem())))) {
                        return "unknown_item|" + input.getItem();
                    }
                }
                if (input.getTag() != null && !input.getTag().isEmpty()) {
                    String tagId = input.getTag().startsWith("#") ? input.getTag().substring(1) : input.getTag();
                    if (ResourceLocation.tryParse(tagId) == null) {
                        return "invalid_tag|" + input.getTag();
                    }
                }
            }

            // 检查输出物品
            if (rule.getOutputItem() == null) {
                return "missing_output";
            }

            var output = rule.getOutputItem();

            // 虚拟货币模式验证
            if (output.isCoin()) {
                if ("dynamic_pricing".equals(output.getType())) {
                    if (output.getDynamicProperties() == null) {
                        return "missing_dynamic_properties";
                    }
                    int[] thresholds = output.getDynamicProperties().getThreshold();
                    int[] values = output.getDynamicProperties().getValue();
                    if (thresholds == null || values == null) {
                        return "missing_threshold_or_value";
                    }
                    if (thresholds.length != values.length) {
                        return "threshold_value_mismatch";
                    }
                    for (int i = 1; i < thresholds.length; i++) {
                        if (thresholds[i] <= thresholds[i-1]) {
                            return "threshold_not_increasing";
                        }
                    }
                }
                return null; // 虚拟货币模式通过验证
            }

            // 动态定价模式验证
            if ("dynamic_pricing".equals(output.getType())) {
                if (output.getItem() == null || output.getItem().isEmpty()) {
                    return "missing_output_item";
                }
                if (output.getDynamicProperties() == null) {
                    return "missing_dynamic_properties";
                }
                int[] thresholds = output.getDynamicProperties().getThreshold();
                int[] values = output.getDynamicProperties().getValue();
                if (thresholds == null || values == null) {
                    return "missing_threshold_or_value";
                }
                if (thresholds.length != values.length) {
                    return "threshold_value_mismatch";
                }
                for (int i = 1; i < thresholds.length; i++) {
                    if (thresholds[i] <= thresholds[i-1]) {
                        return "threshold_not_increasing";
                    }
                }
            }

            // 权重模式验证
            if ("weight".equals(output.getType())) {
                if (output.getItems() == null || output.getItems().isEmpty()) {
                    return "missing_weighted_items";
                }
                for (ExchangeRule.WeightedItem item : output.getItems()) {
                    if (item.getItem() == null || item.getItem().isEmpty()) {
                        return "invalid_weighted_item";
                    }
                    if (item.getWeight() <= 0) {
                        return "invalid_weight";
                    }
                }
            }

            // 节气联动模式验证
            if ("ecliptic_seasons".equals(output.getType())) {
                // 验证基本物品信息
                if (output.getItem() == null || output.getItem().isEmpty()) {
                    return "missing_output_item";
                }
                // 验证节气联动属性
                if (output.getEclipticSeasonsProperties() == null) {
                    return "missing_ecliptic_seasons_properties";
                }
                // 验证季节列表
                var ecsProps = output.getEclipticSeasonsProperties();
                if (ecsProps.getSeason() == null || ecsProps.getSeason().isEmpty()) {
                    return "missing_season_list";
                }
                // 验证季节名称
                for (String season : ecsProps.getSeason()) {
                    if (!isValidSeason(season)) {
                        return "invalid_season|" + season;
                    }
                }
            }

            // 普通物品模式验证
            if (output.getItem() == null || output.getItem().isEmpty()) {
                return "missing_output_item";
            }

            return null; // 验证通过
        } catch (Exception e) {
            return "validation_exception|" + e.getMessage();
        }
    }

    /**
     * 检查是否为有效的季节名称
     * @param season 季节名称
     * @return 是否有效
     */
    private static boolean isValidSeason(String season) {
        if (season == null || season.isEmpty()) {
            return false;
        }
        // 允许的季节值
        return "all".equals(season) ||
                "spring".equals(season) ||
                "summer".equals(season) ||
                "autumn".equals(season) ||
                "winter".equals(season);
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

                        // 发送具体错误信息（解析本地化键）
                        for (String errorMsg : pendingErrorMessages) {
                            Component errorComponent = parseLocalizedError(errorMsg);
                            player.displayClientMessage(errorComponent, false);
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
     * 解析本地化错误信息
     * @param errorString 格式：key|param1|param2
     * @return 格式化后的文本组件
     */
    private static Component parseLocalizedError(String errorString) {
        try {
            String[] parts = errorString.split("\\|");
            String key = parts[0];

            if (parts.length == 1) {
                // 没有参数
                return Component.translatable(key).withStyle(ChatFormatting.RED);
            } else {
                // 有参数，提取参数
                String[] params = new String[parts.length - 1];
                System.arraycopy(parts, 1, params, 0, parts.length - 1);

                // 创建带参数的本地化组件
                Object[] paramObjects = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    paramObjects[i] = Component.literal(params[i]);
                }

                return Component.translatable(key, paramObjects).withStyle(ChatFormatting.RED);
            }
        } catch (Exception e) {
            // 解析失败时显示原始信息
            return Component.literal(errorString).withStyle(ChatFormatting.RED);
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

        // 处理节气联动模式
        if (outputObj.has("type") && "ecliptic_seasons".equals(outputObj.get("type").getAsString())) {
            output.setType("ecliptic_seasons");

            // 解析基本物品信息
            if (outputObj.has("item")) {
                output.setItem(outputObj.get("item").getAsString());
            }

            if (outputObj.has("count")) {
                output.setCount(outputObj.get("count").getAsInt());
            }

            // 处理 components 字段的类型
            if (outputObj.has("components")) {
                JsonElement componentsElement = outputObj.get("components");
                if (componentsElement.isJsonObject()) {
                    // 直接保存 JsonObject
                    output.setComponents(componentsElement.getAsJsonObject());
                } else if (componentsElement.isJsonPrimitive()) {
                    // 字符串格式
                    output.setComponents(componentsElement.getAsString());
                }
            }

            // 解析节气联动属性
            if (outputObj.has("ecliptic_seasons") && outputObj.get("ecliptic_seasons").isJsonObject()) {
                JsonObject ecsPropsObj = outputObj.getAsJsonObject("ecliptic_seasons");

                ExchangeRule.EclipticSeasonsProperties ecsProps = new ExchangeRule.EclipticSeasonsProperties();

                // 解析季节列表
                if (ecsPropsObj.has("season") && ecsPropsObj.get("season").isJsonArray()) {
                    JsonArray seasonArray = ecsPropsObj.getAsJsonArray("season");
                    List<String> seasons = new ArrayList<>();
                    for (JsonElement seasonElement : seasonArray) {
                        seasons.add(seasonElement.getAsString());
                    }
                    ecsProps.setSeason(seasons);
                }

                // 解析仅限季节出售
                if (ecsPropsObj.has("seasonal_only")) {
                    ecsProps.setSeasonal_only(ecsPropsObj.get("seasonal_only").getAsBoolean());
                }

                // 解析应季加成
                if (ecsPropsObj.has("add_season_bonus")) {
                    ecsProps.setAdd_season_bonus(ecsPropsObj.get("add_season_bonus").getAsInt());
                }

                // 解析非应季减益
                if (ecsPropsObj.has("reduce_season_bonus")) {
                    ecsProps.setReduce_season_bonus(ecsPropsObj.get("reduce_season_bonus").getAsInt());
                }

                output.setEclipticSeasonsProperties(ecsProps);
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
        if (!isPositiveRuleNumber(input.getCount())) {
            return false;
        }

        if (input.getTag() != null && !input.getTag().isEmpty()) {
            try {
                String tagId = input.getTag().startsWith("#") ? input.getTag().substring(1) : input.getTag();
                ResourceLocation tagResource = ResourceLocation.tryParse(tagId);
                return tagResource != null;
            } catch (Exception e) {
                return false;
            }
        } else if (input.getItem() != null && !input.getItem().isEmpty()) {
            return validateItemWithComponents(input.getItem());
        }

        return false;
    }

    private boolean isPositiveRuleNumber(int value) {
        return value > 0 && value <= MAX_RULE_COUNT;
    }

    private boolean validateDynamicPricing(ExchangeRule.DynamicPricingProperties properties) {
        if (properties == null) {
            return false;
        }

        int[] thresholds = properties.getThreshold();
        int[] values = properties.getValue();
        if (thresholds == null || values == null || thresholds.length == 0 || thresholds.length != values.length) {
            return false;
        }

        for (int i = 0; i < thresholds.length; i++) {
            if (thresholds[i] < 0 || !isPositiveRuleNumber(values[i])) {
                return false;
            }
            if (i > 0 && thresholds[i] <= thresholds[i - 1]) {
                return false;
            }
        }

        return true;
    }
    /**
     * 验证输出物品的有效性
     *
     * @param output 输出物品对象
     * @return 物品有效返回true，否则返回false
     */
    private boolean validateOutputItem(ExchangeRule.OutputItem output) {
        if (output == null) {
            return false;
        }

        if (output.isCoin()) {
            if ("dynamic_pricing".equals(output.getType())) {
                return validateDynamicPricing(output.getDynamicProperties());
            }
            return isPositiveRuleNumber(output.getCount());
        }

        if ("dynamic_pricing".equals(output.getType())) {
            return output.getItem() != null && !output.getItem().isEmpty()
                    && isPositiveRuleNumber(output.getCount())
                    && validateDynamicPricing(output.getDynamicProperties())
                    && validateItemWithComponents(output.getItem());
        }

        if ("weight".equals(output.getType()) && output.getItems() != null && !output.getItems().isEmpty()) {
            long totalWeight = 0L;
            for (ExchangeRule.WeightedItem weightedItem : output.getItems()) {
                if (weightedItem.getItem() == null || weightedItem.getItem().isEmpty()
                        || !isPositiveRuleNumber(weightedItem.getCount())
                        || weightedItem.getWeight() <= 0
                        || !validateItemWithComponents(weightedItem.getItem())) {
                    return false;
                }
                totalWeight += weightedItem.getWeight();
                if (totalWeight > Integer.MAX_VALUE) {
                    return false;
                }
            }
            return totalWeight > 0;
        }

        if ("ecliptic_seasons".equals(output.getType())) {
            if (output.getItem() == null || output.getItem().isEmpty() || !isPositiveRuleNumber(output.getCount())) {
                return false;
            }

            if (output.getEclipticSeasonsProperties() == null) {
                return false;
            }

            var ecsProps = output.getEclipticSeasonsProperties();
            if (ecsProps.getSeason() == null || ecsProps.getSeason().isEmpty()) {
                return false;
            }

            return validateItemWithComponents(output.getItem());
        }

        if (output.getItem() == null || output.getItem().isEmpty() || !isPositiveRuleNumber(output.getCount())) {
            return false;
        }

        return validateItemWithComponents(output.getItem());
    }
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
     * 优先匹配精确度更高的规则（有组件要求 > 有标签要求 > 仅有物品 ID）
     *
     * @param availableStacks 可用物品列表
     * @return 匹配的规则，如果没有匹配则返回 null
     */
    public static ExchangeRule findMatchingRule(List<ItemStack> availableStacks) {
        ExchangeRule bestMatch = null;
        int bestMatchScore = -1;

        for (ExchangeRule rule : currentRules) {
            if (matchesRule(rule, availableStacks)) {
                int matchScore = calculateMatchPrecision(rule);

                if (matchScore > bestMatchScore) {
                    bestMatch = rule;
                    bestMatchScore = matchScore;
                }
            }
        }

        return bestMatch;
    }

    /**
     * 计算规则的匹配精确度
     * 精确度评分标准（优先级从高到低）：
     * 1. 有组件要求：基础 100 分
     * 2. 组件内容复杂度：每个组件属性 +10 分
     * 3. 组件嵌套深度：每层嵌套 +5 分
     * 4. 仅有物品 ID: 基础 10 分
     * 5. 有标签要求：基础 5 分
     * 6. 有数量要求（大于 1）：额外 +1 分
     * 7. 输入物品数量：每个输入物品 +2 分（多物品配方更精确）
     *
     * @param rule 兑换规则
     * @return 规则的精确度分数
     */
    private static int calculateMatchPrecision(ExchangeRule rule) {
        int precision = 0;

        for (ExchangeRule.InputItem input : rule.getInputs()) {
            // 第一层：判断是否有组件
            if (input.getComponents() != null) {
                // 有组件要求：基础 100 分
                precision += 100;

                // 第二层：计算组件复杂度（更精确的匹配）
                if (input.getComponents() instanceof JsonObject componentsObj) {
                    // 计算 JSON 对象的属性数量
                    precision += componentsObj.size() * 10;

                    // 计算嵌套深度
                    precision += calculateNestingDepth(componentsObj) * 5;
                } else if (input.getComponents() instanceof String componentStr) {
                    // 字符串格式：按逗号分割计算属性数量
                    String[] parts = componentStr.split(",");
                    precision += parts.length * 10;

                    // 如果包含等号，说明有具体的值，额外加分
                    for (String part : parts) {
                        if (part.contains("=")) {
                            precision += 2;
                        }
                    }
                }
            } else if (input.getItem() != null && !input.getItem().isEmpty()) {
                // 仅有物品 ID: 中等精度 +10 分
                precision += 10;
            } else if (input.getTag() != null && !input.getTag().isEmpty()) {
                // 有标签要求：最低精度 +5 分
                precision += 5;
            }

            // 额外加成：有数量要求（大于 1）
            if (input.getCount() > 1) {
                precision += 1;
            }
        }

        // 额外加成：多物品配方（输入物品越多，规则越具体）
        if (rule.getInputs().size() > 1) {
            precision += rule.getInputs().size() * 2;
        }

        return precision;
    }

    /**
     * 计算 JSON 对象的嵌套深度
     * 用于评估组件的复杂程度
     *
     * @param obj JSON 对象
     * @return 嵌套深度
     */
    private static int calculateNestingDepth(JsonObject obj) {
        int maxDepth = 0;

        for (var entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            int currentDepth = 1;

            if (value.isJsonObject()) {
                currentDepth += calculateNestingDepth(value.getAsJsonObject());
            } else if (value.isJsonArray()) {
                currentDepth += 1;
            }

            if (currentDepth > maxDepth) {
                maxDepth = currentDepth;
            }
        }

        return maxDepth;
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

        // 节气联动模式
        if ("ecliptic_seasons".equals(output.getType())) {
            obj.addProperty("type", "ecliptic_seasons");
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

            // 序列化节气联动属性
            if (output.getEclipticSeasonsProperties() != null) {
                obj.add("ecliptic_seasons", serializeEclipticSeasonsProperties(output.getEclipticSeasonsProperties()));
            }

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
     * 序列化节气联动属性为 JSON 对象
     * 将 ExchangeRule.EclipticSeasonsProperties 实例转换为 JSON 格式
     *
     * @param props 要序列化的节气联动属性实例
     * @return 包含节气联动配置的 JSON 对象
     */
    private static JsonObject serializeEclipticSeasonsProperties(ExchangeRule.EclipticSeasonsProperties props) {
        JsonObject ecsPropsObj = new JsonObject();

        // 序列化季节列表
        if (props.getSeason() != null) {
            JsonArray seasonArray = new JsonArray();
            for (String season : props.getSeason()) {
                seasonArray.add(season);
            }
            ecsPropsObj.add("season", seasonArray);
        }

        // 序列化仅限季节出售
        ecsPropsObj.addProperty("seasonal_only", props.isSeasonal_only());

        // 序列化应季加成
        ecsPropsObj.addProperty("add_season_bonus", props.getAdd_season_bonus());

        // 序列化非应季减益
        ecsPropsObj.addProperty("reduce_season_bonus", props.getReduce_season_bonus());

        return ecsPropsObj;
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

        // 处理节气联动模式
        if (obj.has("type") && "ecliptic_seasons".equals(obj.get("type").getAsString())) {
            output.setType("ecliptic_seasons");

            if (obj.has("item")) {
                output.setItem(obj.get("item").getAsString());
            }

            if (obj.has("count")) {
                output.setCount(obj.get("count").getAsInt());
            }

            // 处理组件配置
            if (obj.has("components")) {
                JsonElement componentsElement = obj.get("components");
                if (componentsElement.isJsonObject()) {
                    output.setComponents(componentsElement.getAsJsonObject());
                } else if (componentsElement.isJsonPrimitive()) {
                    output.setComponents(componentsElement.getAsString());
                }
            }

            // 反序列化节气联动属性
            if (obj.has("ecliptic_seasons") && obj.get("ecliptic_seasons").isJsonObject()) {
                JsonObject ecsPropsObj = obj.getAsJsonObject("ecliptic_seasons");

                ExchangeRule.EclipticSeasonsProperties ecsProps = new ExchangeRule.EclipticSeasonsProperties();

                // 反序列化季节列表
                if (ecsPropsObj.has("season") && ecsPropsObj.get("season").isJsonArray()) {
                    JsonArray seasonArray = ecsPropsObj.getAsJsonArray("season");
                    List<String> seasons = new ArrayList<>();
                    for (JsonElement seasonElement : seasonArray) {
                        seasons.add(seasonElement.getAsString());
                    }
                    ecsProps.setSeason(seasons);
                }

                // 反序列化仅限季节出售
                if (ecsPropsObj.has("seasonal_only")) {
                    ecsProps.setSeasonal_only(ecsPropsObj.get("seasonal_only").getAsBoolean());
                }

                // 反序列化应季加成
                if (ecsPropsObj.has("add_season_bonus")) {
                    ecsProps.setAdd_season_bonus(ecsPropsObj.get("add_season_bonus").getAsInt());
                }

                // 反序列化非应季减益
                if (ecsPropsObj.has("reduce_season_bonus")) {
                    ecsProps.setReduce_season_bonus(ecsPropsObj.get("reduce_season_bonus").getAsInt());
                }

                output.setEclipticSeasonsProperties(ecsProps);
            }

            return output;
        }

        // 普通物品模式
        output.setItem(obj.get("item").getAsString());
        output.setCount(obj.get("count").getAsInt());

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

    // ==================== 外部配置目录规则加载支持（网页编辑器使用） ====================

    private void loadConfigRules(List<ExchangeRule> rules, List<String> errors) {
        try {
            Path dir = getExternalRulesDir();
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return;
            }

            try (var stream = Files.walk(dir)) {
                List<Path> files = stream
                        .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();

                for (Path file : files) {
                    try {
                        String raw = Files.readString(file, StandardCharsets.UTF_8);
                        JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                        loadRulesFromJson(json, file.toString(), rules, errors);
                    } catch (JsonParseException e) {
                        errors.add(String.format("error.shipping_box.json_parse_error|%s|%s",
                                file.toString(), e.getMessage()));
                    } catch (Exception e) {
                        errors.add(String.format("error.shipping_box.resource_load_error|%s|%s",
                                file.toString(), e.getMessage()));
                    }
                }
            }
        } catch (Exception e) {
            errors.add(String.format("error.shipping_box.resource_load_error|%s|%s",
                    getExternalRulesDir().toString(), e.getMessage()));
        }
    }

    private Path getExternalRulesDir() {
        // 如果 KubeJS 存在，优先从 KubeJS 数据目录读取（与保存逻辑保持一致）
        if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            return FMLPaths.GAMEDIR.get().resolve("kubejs/data/shipping_box/exchange_rules");
        }
        return FMLPaths.CONFIGDIR.get().resolve("shipping_box/exchange_rules");
    }

    /**
     * 从 JSON 对象加载规则（供外部目录加载和数据包共用）
     */
    private void loadRulesFromJson(JsonObject json, String source, List<ExchangeRule> rules, List<String> errors) {
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
                        String validationError = getValidationErrorDetails(rule);
                        if (validationError != null && !validationError.isEmpty()) {
                            errors.add(String.format("error.shipping_box.rule_validation_failed|%d|%s|%s",
                                    ruleIndex + 1, source, validationError));
                        } else {
                            errors.add(String.format("error.shipping_box.rule_validation_failed|%d|%s|%s",
                                    ruleIndex + 1, source, "unknown_error"));
                        }
                    }
                } catch (JsonParseException e) {
                    errors.add(String.format("error.shipping_box.json_parse_error|%s|%s",
                            source, e.getMessage()));
                } catch (Exception e) {
                    errors.add(String.format("error.shipping_box.rule_parse_error|%s|%s",
                            source, e.getMessage()));
                }
                ruleIndex++;
            }
        } else {
            errors.add(String.format("error.shipping_box.missing_rules_array|%s", source));
        }
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