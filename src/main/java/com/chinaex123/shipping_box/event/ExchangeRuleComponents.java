package com.chinaex123.shipping_box.event;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.Holder;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ExchangeRuleComponents {

    /**
     * 解析组件字符串为键值对映射
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
     * 检查物品堆栈是否匹配指定的组件要求
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
     * 检查单个组件是否匹配
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
     * 比较组件值
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
     * 标准化组件ID
     */
    public static ResourceLocation normalizeComponentId(String componentName) {
        // 标准化组件ID（添加minecraft:前缀如果需要）
        if (componentName.contains(":")) {
            return ResourceLocation.tryParse(componentName);
        }
        return ResourceLocation.tryParse("minecraft:" + componentName);
    }

    /**
     * 应用组件到物品堆栈
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
            // 删除日志输出
        }
    }

    /**
     * 从JSON格式应用组件
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
            // 删除日志输出
        }
    }

    /**
     * 从JSON应用单个组件
     */
    private static void applyComponentFromJson(ItemStack stack, String componentName, com.google.gson.JsonElement componentValue) {
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
                    stack.set((DataComponentType) componentType, parsedValue);
                }
            }

        } catch (Exception e) {
            // 删除日志输出
        }
    }

    /**
     * 从JSON应用附魔组件
     */
    private static void applyEnchantmentsFromJson(ItemStack stack, com.google.gson.JsonElement jsonElement) {
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

            for (var entry : levelsObj.entrySet()) {
                String enchId = entry.getKey();
                var levelElement = entry.getValue();

                if (levelElement.isJsonPrimitive() && levelElement.getAsJsonPrimitive().isNumber()) {
                    int level = levelElement.getAsInt();
                    ResourceLocation enchLoc = ResourceLocation.tryParse(enchId);

                    if (enchLoc != null) {
                        // 检查是否有其他的附魔注册表访问方式
                        var enchantment = BuiltInRegistries.REGISTRY.get(ResourceLocation.withDefaultNamespace("enchantment")).get(enchLoc);
                        if (enchantment != null) {
                            mutableEnchants.set((Holder<Enchantment>) enchantment, level);
                        }
                    }
                }
            }

            var finalEnchants = mutableEnchants.toImmutable();
            stack.set(DataComponents.ENCHANTMENTS, finalEnchants);

        } catch (Exception e) {
            // 删除日志输出
        }
    }

    /**
     * 从JSON应用存储的附魔组件
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

            for (var entry : levelsObj.entrySet()) {
                String enchId = entry.getKey();
                var levelElement = entry.getValue();

                if (levelElement.isJsonPrimitive() && levelElement.getAsJsonPrimitive().isNumber()) {
                    int level = levelElement.getAsInt();
                    ResourceLocation enchLoc = ResourceLocation.tryParse(enchId);

                    if (enchLoc != null) {
                        // 检查是否有其他的附魔注册表访问方式
                        var enchantment = BuiltInRegistries.REGISTRY.get(ResourceLocation.withDefaultNamespace("enchantment")).get(enchLoc);
                        if (enchantment != null) {
                            mutableEnchants.set((Holder<Enchantment>) enchantment, level);
                        }
                    }
                }
            }

            var finalEnchants = mutableEnchants.toImmutable();
            stack.set(DataComponents.STORED_ENCHANTMENTS, finalEnchants);

        } catch (Exception e) {
            // 删除日志输出
        }
    }

    /**
     * 应用单个组件到补丁构建器
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
            // 删除日志输出
        }
    }

    /**
     * 动态解析组件值
     */
    private static Object parseComponentValueDynamically(DataComponentType<?> componentType, String rawValue) {
        try {
            var codec = componentType.codec();
            if (codec == null) {
                return null;
            }

            // 将原始值转换为合适的JSON格式
            String jsonValue = convertToProperJson(rawValue);

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
     */
    private static String convertToProperJson(String value) {
        // 智能JSON格式化
        if (value == null || value.isEmpty()) {
            return "null";
        }

        // 特殊处理：如果值已经是完整的JSON对象或数组
        if ((value.startsWith("{") && value.endsWith("}")) ||
                (value.startsWith("[") && value.endsWith("]"))) {
            try {
                // 验证是否为有效JSON
                com.google.gson.JsonParser.parseString(value);
                return value;
            } catch (Exception e) {
                // 保持原有逻辑但移除日志
            }
        }

        // 处理组件赋值格式：component={structure}
        if (value.contains("=") && value.contains("{")) {
            int equalsIndex = value.indexOf('=');
            String componentName = value.substring(0, equalsIndex).trim();
            String componentValue = value.substring(equalsIndex + 1).trim();

            // 如果右侧是JSON结构，直接返回
            if ((componentValue.startsWith("{") && componentValue.endsWith("}")) ||
                    (componentValue.startsWith("[") && componentValue.endsWith("]"))) {
                try {
                    com.google.gson.JsonParser.parseString(componentValue);
                    return componentValue;
                } catch (Exception e) {
                    return componentValue;
                }
            }
        }

        // 字符串值
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value;
        }

        // 数字值
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            return value;
        }

        // 布尔值
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return value.toLowerCase();
        }

        // 其他情况包装为字符串
        return "\"" + value + "\"";
    }

    /**
     * 安全地设置组件值
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
            // 删除日志输出
        }
    }
}
