package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

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
 * 支持物品ID和标签两种方式定义输入物品
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID)
public class ExchangeRecipeManager extends SimplePreparableReloadListener<List<ExchangeRule>> {
    /** JSON解析器实例 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 配置文件夹路径 */
    private static final String CONFIG_FOLDER = "recipe_manager";

    /** 当前生效的兑换规则列表 */
    private static List<ExchangeRule> currentRules = new ArrayList<>();

    /**
     * 准备阶段：从资源配置中加载并解析兑换规则
     *
     * @param resourceManager 资源管理器
     * @param profiler 性能分析器
     * @return List<ExchangeRule> 解析后的规则列表
     */
    @Override
    protected List<ExchangeRule> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<ExchangeRule> rules = new ArrayList<>();

        try {
            // 遍历所有匹配的资源配置文件
            for (ResourceLocation resourceLocation : resourceManager.listResources(
                    CONFIG_FOLDER,
                    path -> path.getPath().endsWith(".json")).keySet()) {

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
                                for (JsonElement element : json.getAsJsonArray("rules")) {
                                    ExchangeRule rule = parseRule(element.getAsJsonObject());
                                    if (validateRule(rule)) {
                                        rules.add(rule);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 静默处理加载错误
                }
            }

        } catch (Exception e) {
            // 静默处理扫描错误
        }

        return rules;
    }

    /**
     * 解析单个兑换规则JSON对象
     *
     * @param json 规则JSON对象
     * @return ExchangeRule 解析后的兑换规则
     */
    private ExchangeRule parseRule(JsonObject json) {
        ExchangeRule rule = new ExchangeRule();
        List<ExchangeRule.InputItem> inputs = new ArrayList<>();

        // 解析输入物品列表
        if (json.has("input") && json.get("input").isJsonArray()) {
            // 多个输入物品
            for (JsonElement element : json.getAsJsonArray("input")) {
                JsonObject inputObj = element.getAsJsonObject();
                ExchangeRule.InputItem input = new ExchangeRule.InputItem();

                // 支持标签和物品ID两种方式
                if (inputObj.has("tag")) {
                    input.setTag(inputObj.get("tag").getAsString());
                } else if (inputObj.has("item")) {
                    input.setItem(inputObj.get("item").getAsString());
                }

                if (inputObj.has("count")) {
                    input.setCount(inputObj.get("count").getAsInt());
                }
                inputs.add(input);
            }
        } else if (json.has("input") && json.get("input").isJsonObject()) {
            // 单个输入物品
            JsonObject inputObj = json.getAsJsonObject("input");
            ExchangeRule.InputItem input = new ExchangeRule.InputItem();

            // 支持标签和物品ID两种方式
            if (inputObj.has("tag")) {
                input.setTag(inputObj.get("tag").getAsString());
            } else if (inputObj.has("item")) {
                input.setItem(inputObj.get("item").getAsString());
            }

            if (inputObj.has("count")) {
                input.setCount(inputObj.get("count").getAsInt());
            }
            inputs.add(input);
        }

        rule.setInputs(inputs);

        // 解析输出物品
        JsonObject outputObj = json.getAsJsonObject("output");
        ExchangeRule.OutputItem output = new ExchangeRule.OutputItem();
        output.setItem(outputObj.get("item").getAsString());
        if (outputObj.has("count")) {
            output.setCount(outputObj.get("count").getAsInt());
        }
        rule.setOutput(output);

        return rule;
    }

    /**
     * 验证兑换规则的有效性
     * 检查输入和输出物品是否存在于游戏注册表中
     *
     * @param rule 要验证的兑换规则
     * @return boolean 规则有效返回true，否则返回false
     */
    private boolean validateRule(ExchangeRule rule) {
        // 验证所有输入物品
        for (ExchangeRule.InputItem input : rule.getInputs()) {
            // 如果是标签形式
            if (input.getTag() != null && !input.getTag().isEmpty()) {
                try {
                    ResourceLocation tagId = ResourceLocation.tryParse(input.getTag().substring(1)); // 移除#前缀
                    if (tagId == null) {
                        return false;
                    }
                    // 验证标签是否存在（这里简化处理，实际可能需要更严格的验证）
                    TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), tagId);
                    // 标签验证通过
                } catch (Exception e) {
                    return false;
                }
            }
            // 如果是物品ID形式
            else if (input.getItem() != null && !input.getItem().isEmpty()) {
                ResourceLocation itemId = ResourceLocation.tryParse(input.getItem());
                if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                    return false;
                }
            }
            // 既没有标签也没有物品ID
            else {
                return false;
            }
        }

        // 验证输出物品
        ResourceLocation outputId = ResourceLocation.tryParse(rule.getOutputItem().getItem());
        if (outputId == null || !BuiltInRegistries.ITEM.containsKey(outputId)) {
            return false;
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
     * @return List<ExchangeRule> 当前规则列表
     */
    public static List<ExchangeRule> getRules() {
        return currentRules;
    }

    /**
     * 查找匹配给定物品列表的兑换规则
     *
     * @param availableStacks 可用物品列表
     * @return ExchangeRule 匹配的规则，如果没有匹配则返回null
     */
    public static ExchangeRule findMatchingRule(List<ItemStack> availableStacks) {
        for (ExchangeRule rule : currentRules) {
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
     * @return boolean 满足规则返回true，否则返回false
     */
    private static boolean matchesRule(ExchangeRule rule, List<ItemStack> availableStacks) {
        boolean[] matched = new boolean[rule.getInputs().size()];

        // 检查每个所需的输入物品
        for (int i = 0; i < rule.getInputs().size(); i++) {
            ExchangeRule.InputItem required = rule.getInputs().get(i);

            for (ItemStack stack : availableStacks) {
                if (!matched[i] && required.matches(stack) && stack.getCount() >= required.getCount()) {
                    matched[i] = true;
                    break;
                }
            }
        }

        // 确保所有输入物品都已匹配
        for (boolean m : matched) {
            if (!m) return false;
        }
        return true;
    }

    /**
     * 消耗指定规则所需的输入物品
     *
     * @param rule 兑换规则
     * @param availableStacks 可用物品列表
     * @return List<ItemStack> 消耗后剩余的物品列表
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
