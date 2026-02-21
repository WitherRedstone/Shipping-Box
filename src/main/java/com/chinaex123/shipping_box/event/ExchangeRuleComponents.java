package com.chinaex123.shipping_box.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.RegistryAccess;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExchangeRuleComponents {

    // 缓存附魔注册表，避免重复查找
    private static Registry<Enchantment> enchantmentRegistry = null;

    // 静态初始化块 - 类加载时尝试初始化
    static {
        try {
            ResourceLocation key = Registries.ENCHANTMENT.location();
            Registry<?> registry = BuiltInRegistries.REGISTRY.get(key);

            if (registry == null) {
                // 备选键名
                registry = BuiltInRegistries.REGISTRY.get(ResourceLocation.tryParse("enchantment"));
            }

            if (registry != null) {
                @SuppressWarnings("unchecked")
                Registry<Enchantment> typedRegistry = (Registry<Enchantment>) registry;
                enchantmentRegistry = typedRegistry;
            }
        } catch (Exception e) {
            // 初始化失败时静默处理
        }
    }

    /**
     * 初始化附魔注册表
     * 在 onServerStarting 事件中调用
     *
     * @param registryAccess 注册表访问对象
     */
    public static void initEnchantmentRegistry(RegistryAccess registryAccess) {
        try {
            enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
        } catch (Exception e) {
            // 初始化失败时静默处理
        }
    }

    /**
     * 辅助方法：安全地获取附魔注册表
     */
    private static Registry<Enchantment> getEnchantmentRegistry() {
        return enchantmentRegistry;
    }

    /**
     * 解析组件字符串为键值对映射
     * 支持格式："name1=value1,name2=value2" 或 "name1=\"value1\",name2=\"value2\""
     *
     * @param componentString 组件字符串
     * @return 解析后的组件映射表
     */
    public static Map<String, String> parseComponentString(String componentString) {
        Map<String, String> components = new HashMap<>();

        // 首先尝试按逗号分割（处理多个组件）
        String[] parts = componentString.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            // 查找等号位置
            int equalsIndex = part.indexOf('=');
            if (equalsIndex > 0) {
                String name = part.substring(0, equalsIndex).trim();
                String value = part.substring(equalsIndex + 1).trim();

                // 处理可能的引号包围
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                    value = value.substring(1, value.length() - 1);
                }

                components.put(name, value);
            }
        }

        return components;
    }

    /**
     * 检查物品堆是否匹配指定的组件要求
     *
     * @param stack 要检查的物品堆
     * @param componentString 组件要求字符串
     * @return 所有组件都匹配返回true，否则返回false
     */
    public static boolean matchesComponents(ItemStack stack, String componentString) {
        try {
            // 解析组件字符串为键值对
            Map<String, String> componentMap = parseComponentString(componentString);

            for (Map.Entry<String, String> entry : componentMap.entrySet()) {
                String componentName = entry.getKey();
                String componentValue = entry.getValue();

                if (!matchesSingleComponent(stack, componentName, componentValue)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return true; // 出错时宽松匹配
        }
    }

    /**
     * 检查物品堆的单个组件是否匹配指定要求
     *
     * @param stack 要检查的物品堆
     * @param componentName 组件名称
     * @param componentValue 期望的组件值
     * @return 组件匹配返回true，否则返回false
     */
    private static boolean matchesSingleComponent(ItemStack stack, String componentName, String componentValue) {
        try {
            ResourceLocation componentId = normalizeComponentId(componentName);
            if (componentId == null) {
                return false;
            }

            // 获取组件类型
            DataComponentType<?> componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
            if (componentType == null) {
                // 组件类型不存在，宽松匹配
                return true;
            }

            // 获取物品的实际组件值
            Object actualValue = stack.get(componentType);
            if (actualValue == null) {
                return false;
            }

            // 比较组件值
            return compareComponentValues(actualValue, componentValue);
        } catch (Exception e) {
            return true; // 出错时宽松匹配
        }
    }

    /**
     * 比较实际组件值与期望值是否匹配
     * 支持字符串、数字、布尔值等基本类型比较
     *
     * @param actualValue 实际的组件值
     * @param expectedValue 期望的组件值字符串
     * @return 值匹配返回true，否则返回false
     */
    private static boolean compareComponentValues(Object actualValue, String expectedValue) {
        try {
            // 处理不同类型的值比较
            return switch (actualValue) {
                case String s -> actualValue.equals(expectedValue.replace("\"", ""));
                case Number number -> actualValue.toString().equals(expectedValue);
                case Boolean b -> actualValue.toString().equals(expectedValue);
                case null, default ->
                        true; // 对于复杂对象，进行宽松匹配
            };
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 标准化组件ID格式
     * 如果组件名称不包含命名空间，则自动添加"minecraft:"前缀
     *
     * @param componentName 组件名称
     * @return 标准化的ResourceLocation对象，无效时返回null
     */
    public static ResourceLocation normalizeComponentId(String componentName) {
        // 标准化组件ID（添加minecraft:前缀如果需要）
        if (componentName.contains(":")) {
            return ResourceLocation.tryParse(componentName);
        }
        return ResourceLocation.tryParse("minecraft:" + componentName);
    }

    /**
     * 将组件字符串应用到物品堆上
     * 支持JSON对象格式和键值对格式两种输入方式
     *
     * @param stack 目标物品堆
     * @param componentString 组件配置字符串
     */
    public static void applyComponents(ItemStack stack, String componentString) {
        try {
            // 检查是否为JSON对象格式
            if (componentString.trim().startsWith("{") && componentString.trim().endsWith("}")) {
                applyComponentsFromJson(stack, componentString);
            } else {
                // 保持原有的键值对解析方式作为后备
                Map<String, String> componentMap = parseComponentString(componentString);

                // 构建组件补丁
                DataComponentPatch.Builder patchBuilder = DataComponentPatch.builder();

                for (Map.Entry<String, String> entry : componentMap.entrySet()) {
                    String componentName = entry.getKey();
                    String componentValue = entry.getValue();

                    applySingleComponent(patchBuilder, componentName, componentValue);
                }

                // 应用组件补丁
                DataComponentPatch patch = patchBuilder.build();
                stack.applyComponents(patch);
            }
        } catch (Exception e) {
            // 应用失败时静默处理
        }
    }

    /**
     * 从JSON格式字符串解析并应用组件到物品堆
     *
     * @param stack 目标物品堆
     * @param jsonString JSON格式的组件配置字符串
     */
    private static void applyComponentsFromJson(ItemStack stack, String jsonString) {
        try {
            // 解析JSON
            var jsonElement = com.google.gson.JsonParser.parseString(jsonString);
            if (!jsonElement.isJsonObject()) {
                return;
            }

            var jsonObject = jsonElement.getAsJsonObject();

            // 处理每个组件
            for (var entry : jsonObject.entrySet()) {
                String componentName = entry.getKey();
                var componentValue = entry.getValue();

                applyComponentFromJson(stack, componentName, componentValue);
            }
        } catch (Exception e) {
            // 解析失败时静默处理
        }
    }

    /**
     * 从JSON元素解析并应用单个组件到物品堆
     * 支持特殊组件（如附魔）和通用组件的处理
     *
     * @param stack 目标物品堆
     * @param componentName 组件名称
     * @param componentValue 组件值的JSON元素
     */
    @SuppressWarnings("unchecked")
    private static void applyComponentFromJson(ItemStack stack, String componentName, JsonElement componentValue) {
        try {
            ResourceLocation componentId = normalizeComponentId(componentName);
            if (componentId == null) {
                return;
            }

            // 特殊处理附魔组件
            if ("enchantments".equals(componentName) || componentId.getPath().equals("enchantments")) {
                applyEnchantmentsFromJson(stack, componentValue);
                return;
            }

            // 特殊处理存储的附魔组件
            if ("stored_enchantments".equals(componentName) || componentId.getPath().equals("stored_enchantments")) {
                applyStoredEnchantmentsFromJson(stack, componentValue);
                return;
            }

            // 通用组件处理
            DataComponentType<?> componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
            if (componentType == null) {
                return;
            }

            // 使用组件codec解析JSON
            var result = componentType.codec().parse(JsonOps.INSTANCE, componentValue);
            if (result.isSuccess()) {
                Object parsedValue = result.result().orElse(null);
                if (parsedValue != null) {
                    // 安全地转换类型
                    DataComponentType<Object> rawType = (DataComponentType<Object>) componentType;
                    stack.set(rawType, parsedValue);
                }
            }

        } catch (Exception e) {
            // 应用失败时静默处理
        }
    }

    /**
     * 从JSON元素解析并应用附魔组件到物品堆
     *
     * @param stack 目标物品堆
     * @param jsonElement 附魔配置的JSON元素
     */
    private static void applyEnchantmentsFromJson(ItemStack stack, com.google.gson.JsonElement jsonElement) {
        try {
            var enchantmentsObj = jsonElement.getAsJsonObject();
            var levelsObj = enchantmentsObj.getAsJsonObject("levels");
            var mutableEnchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

            // 获取附魔注册表
            Registry<Enchantment> enchantmentRegistry = getEnchantmentRegistry();

            if (enchantmentRegistry == null) {
                return;
            }

            for (var entry : levelsObj.entrySet()) {
                String enchId = entry.getKey();
                var levelElement = entry.getValue();
                int level = levelElement.getAsInt();
                ResourceLocation enchLoc = ResourceLocation.tryParse(enchId);

                if (enchLoc != null) {
                    Optional<Holder.Reference<Enchantment>> enchantment = enchantmentRegistry.getHolder(enchLoc);
                    enchantment.ifPresent(enchantmentReference -> mutableEnchants.set(enchantmentReference, level));
                }
            }

            var finalEnchants = mutableEnchants.toImmutable();
            stack.set(DataComponents.ENCHANTMENTS, finalEnchants);

        } catch (Exception e) {
            // 应用失败时静默处理
        }
    }

    /**
     * 从JSON应用存储的附魔组件
     *
     * @param stack 目标物品堆
     * @param jsonElement 附魔配置的JSON元素
     */
    private static void applyStoredEnchantmentsFromJson(ItemStack stack, com.google.gson.JsonElement jsonElement) {
        try {
            if (!jsonElement.isJsonObject()) {
                return;
            }

            var enchantmentsObj = jsonElement.getAsJsonObject();
            if (!enchantmentsObj.has("levels") || !enchantmentsObj.get("levels").isJsonObject()) {
                return;
            }

            var levelsObj = enchantmentsObj.getAsJsonObject("levels");
            var mutableEnchants = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY
            );

            // 获取附魔注册表
            Registry<Enchantment> enchantmentRegistry = getEnchantmentRegistry();
            if (enchantmentRegistry == null) {
                return;
            }

            for (var entry : levelsObj.entrySet()) {
                String enchId = entry.getKey();
                var levelElement = entry.getValue();

                if (levelElement.isJsonPrimitive() && levelElement.getAsJsonPrimitive().isNumber()) {
                    int level = levelElement.getAsInt();
                    ResourceLocation enchLoc = ResourceLocation.tryParse(enchId);

                    if (enchLoc != null) {
                        Optional<Holder.Reference<Enchantment>> enchantment = enchantmentRegistry.getHolder(enchLoc);
                        enchantment.ifPresent(enchantmentReference -> mutableEnchants.set(enchantmentReference, level));
                    }
                }
            }

            var finalEnchants = mutableEnchants.toImmutable();
            stack.set(DataComponents.STORED_ENCHANTMENTS, finalEnchants);

        } catch (Exception e) {
            // 应用失败时静默处理
        }
    }

    /**
     * 应用单个组件到补丁构建器
     *
     * @param patchBuilder 补丁构建器
     * @param componentName 组件名称
     * @param componentValue 组件值字符串
     */
    private static void applySingleComponent(DataComponentPatch.Builder patchBuilder, String componentName, String componentValue) {
        try {
            ResourceLocation componentId = normalizeComponentId(componentName);
            if (componentId == null) {
                return;
            }

            DataComponentType<?> componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
            if (componentType == null) {
                return;
            }

            // 使用组件的codec自动解析和设置
            Object parsedValue = parseComponentValueDynamically(componentType, componentValue);
            if (parsedValue != null) {
                setComponentValueSafely(patchBuilder, componentType, parsedValue);
            }

        } catch (Exception e) {
            // 应用失败时静默处理
        }
    }


    /**
     * 动态解析组件值
     *
     * @param componentType 组件类型
     * @param rawValue 原始值字符串
     * @return 解析后的对象，失败返回null
     */
    private static Object parseComponentValueDynamically(DataComponentType<?> componentType, String rawValue) {
        try {
            var codec = componentType.codec();
            if (codec == null) {
                return null;
            }

            // 将原始值转换为合适的JSON格式
            String jsonValue = convertToProperJson(rawValue);
            if (jsonValue == null) {
                return null;
            }

            // 使用组件的codec进行解析
            var jsonElement = com.google.gson.JsonParser.parseString(jsonValue);
            var result = codec.parse(com.mojang.serialization.JsonOps.INSTANCE, jsonElement);

            return result.result().orElse(null);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 转换为适当的JSON格式
     *
     * @param value 原始值字符串
     * @return JSON格式字符串
     */
    private static String convertToProperJson(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // 移除首尾空白字符
        value = value.trim();

        // 如果已经是有效的JSON对象或数组，直接返回
        if ((value.startsWith("{") && value.endsWith("}")) ||
                (value.startsWith("[") && value.endsWith("]"))) {
            return value;
        }

        // 处理数字值
        try {
            Double.parseDouble(value);
            return value;
        } catch (NumberFormatException e) {
            // 不是数字，继续处理
        }

        // 处理布尔值
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return value;
        }

        // 处理看起来像JSON但缺少结尾的情况
        if (value.contains("{") && !value.endsWith("}")) {
            // 尝试补全JSON对象
            String fixedValue = fixIncompleteJsonObject(value);
            if (fixedValue != null) {
                return fixedValue;
            }
        }

        // 如果以上都不匹配，将其作为字符串处理（添加引号）
        return "\"" + value + "\"";
    }

    /**
     * 补全不完整的JSON对象字符串
     *
     * @param incompleteJson 不完整的JSON字符串
     * @return 修复后的JSON字符串，如果无法修复则返回null
     */
    private static String fixIncompleteJsonObject(String incompleteJson) {
        // 简单的修复策略：计算花括号是否匹配
        int openBraces = 0;
        int closeBraces = 0;

        for (char c : incompleteJson.toCharArray()) {
            if (c == '{') openBraces++;
            else if (c == '}') closeBraces++;
        }

        // 如果开括号比闭括号多，尝试补全
        if (openBraces > closeBraces) {
            String result = incompleteJson + "}".repeat(Math.max(0, openBraces - closeBraces));

            // 验证修复后的JSON是否有效
            try {
                JsonParser.parseString(result);
                return result;
            } catch (JsonSyntaxException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * 安全地设置组件值
     *
     * @param builder 补丁构建器
     * @param type 组件类型
     * @param value 组件值
     */
    @SuppressWarnings("unchecked")
    private static <T> void setComponentValueSafely(DataComponentPatch.Builder builder,
                                                    DataComponentType<T> type,
                                                    Object value) {
        try {
            if (value != null) {
                builder.set(type, (T) value);
            }
        } catch (Exception e) {
            // 设置失败时静默处理
        }
    }
}