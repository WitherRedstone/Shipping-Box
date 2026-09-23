package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.config.CommonConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 交易日志记录器。
 * <p>
 * 将兑换交易记录写入按日期命名的日志文件，
 * 记录内容包括时间戳、玩家名称、消耗物品与获得物品（含组件信息）。
 * 是否写入由配置开关控制。
 */
public class TransactionLogger {

    /** 日志文件名所用的日期格式 */
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 日志条目中的时间戳格式 */
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 日志目录路径：config/shipping_box/logs/ */
    private static final Path LOG_DIR = FMLPaths.CONFIGDIR.get().resolve("shipping_box/logs");

    /**
     * 记录交易日志。
     * <p>
     * 若配置未启用交易日志则直接返回；否则确保日志目录存在，
     * 并按当天日期追加写入一行交易记录。
     *
     * @param playerName      玩家名称
     * @param inputs          消耗的物品列表
     * @param outputs         获得的物品列表
     * @param virtualCurrency 获得的虚拟货币数量
     * @param level           世界实例（用于获取时间等信息）
     * @param rule            兑换规则（用于获取组件配置）
     */
    public static void logTransaction(String playerName, List<ItemStack> inputs, List<ItemStack> outputs, int virtualCurrency, Level level, ExchangeRule rule) {
        try {
            // 检查配置是否启用日志
            if (!CommonConfig.ENABLE_TRANSACTION_LOGGING.get()) {
                return; // 日志已禁用，直接返回
            }

            // 确保日志目录存在
            if (!Files.exists(LOG_DIR)) {
                Files.createDirectories(LOG_DIR);
            }

            // 获取当天的日志文件
            String dateStr = LocalDateTime.now().format(FILE_DATE_FORMAT);
            File logFile = LOG_DIR.resolve("transaction_" + dateStr + ".log").toFile();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                StringBuilder logEntry = new StringBuilder();

                // 时间戳
                logEntry.append("[").append(LocalDateTime.now().format(LOG_TIME_FORMAT)).append("] ");

                // 玩家信息
                logEntry.append("玩家：").append(playerName).append(" | ");

                // 输入物品（带规则组件信息）
                logEntry.append("输入：[");
                if (inputs.isEmpty()) {
                    logEntry.append("空");
                } else {
                    for (int i = 0; i < inputs.size(); i++) {
                        ItemStack stack = inputs.get(i);
                        String itemInfo = formatItemWithRuleComponents(stack, rule, i);
                        logEntry.append(itemInfo);
                        if (i < inputs.size() - 1) logEntry.append(", ");
                    }
                }
                logEntry.append("] | ");

                // 输出物品（带规则组件信息）
                logEntry.append("输出：[");
                if (virtualCurrency > 0) {
                    logEntry.append("VSS 货币：").append(virtualCurrency);
                    long validOutputCount = outputs.stream().filter(s -> !s.isEmpty() && s.getCount() > 0).count();
                    if (validOutputCount > 0) logEntry.append(", ");
                }

                // 过滤掉空物品和数量为 0 的物品
                boolean hasValidOutput = false;
                for (ItemStack stack : outputs) {
                    if (stack.isEmpty() || stack.getCount() <= 0) continue;

                    if (hasValidOutput) logEntry.append(", ");
                    String itemInfo = formatOutputItem(stack, rule);
                    logEntry.append(itemInfo);
                    hasValidOutput = true;
                }

                if (!hasValidOutput && virtualCurrency == 0) {
                    logEntry.append("EMPTY");
                }
                logEntry.append("]");

                // 写入文件
                writer.write(logEntry.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            ShippingBox.LOGGER.warn("[Shipping Box-TransactionLogger] 写入记录交易日志时出错");
        }
    }

    /**
     * 格式化输入物品，使用规则中的组件配置。
     *
     * @param stack 输入物品堆
     * @param rule  兑换规则
     * @param index 输入物品在规则输入列表中的索引
     * @return 格式化后的物品描述字符串
     */
    private static String formatItemWithRuleComponents(ItemStack stack, ExchangeRule rule, int index) {
        String itemName = Component.translatable(stack.getItem().getDescriptionId()).getString();
        StringBuilder result = new StringBuilder();
        result.append(itemName).append(" x").append(stack.getCount());

        // 从规则中获取对应输入的组件配置
        if (rule != null && rule.getInputs() != null && index < rule.getInputs().size()) {
            ExchangeRule.InputItem input = rule.getInputs().get(index);
            Object components = input.getComponents();

            if (components != null) {
                String componentStr = formatRuleComponents(components);
                if (!componentStr.isEmpty()) {
                    result.append(" [").append(componentStr).append("]");
                }
            }
        }

        return result.toString();
    }

    /**
     * 格式化输出物品，使用规则中的组件配置。
     *
     * @param stack 输出物品堆
     * @param rule  兑换规则
     * @return 格式化后的物品描述字符串
     */
    private static String formatOutputItem(ItemStack stack, ExchangeRule rule) {
        String itemName = Component.translatable(stack.getItem().getDescriptionId()).getString();
        StringBuilder result = new StringBuilder();
        result.append(itemName).append(" x").append(stack.getCount());

        // 从规则中获取输出的组件配置
        if (rule != null && rule.getOutputItem() != null) {
            Object components = rule.getOutputItem().getComponents();

            if (components != null) {
                String componentStr = formatRuleComponents(components);
                if (!componentStr.isEmpty()) {
                    result.append(" [").append(componentStr).append("]");
                }
            }
        }

        return result.toString();
    }

    /**
     * 格式化规则中的组件配置。
     * <p>
     * 支持 JsonObject 与 String 两种形式：JsonObject 会逐项拼接为
     * "name=value" 形式，组件 ID 会去除命名空间前缀。
     *
     * @param components 组件配置对象
     * @return 格式化后的组件描述字符串
     */
    private static String formatRuleComponents(Object components) {
        if (components instanceof com.google.gson.JsonObject jsonObj) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;

            // 处理每个组件
            for (var entry : jsonObj.entrySet()) {
                String componentName = entry.getKey();
                var componentValue = entry.getValue();

                // 简化组件 ID
                if (componentName.contains(":")) {
                    componentName = componentName.substring(componentName.indexOf(":") + 1);
                }

                // 格式化组件值
                String valueStr = formatComponentJsonValue(componentValue);

                if (!first) sb.append(", ");
                sb.append(componentName).append("=").append(valueStr);
                first = false;
            }

            return sb.toString();
        } else if (components instanceof String str) {
            return str;
        }

        return "";
    }

    /**
     * 格式化组件 JSON。
     * <p>
     * 若对象包含 "levels" 字段，则按附魔等级表格式化为 "{附魔=等级}"；
     * 其他对象直接返回原字符串表示，原始类型返回值本身。
     *
     * @param element 组件值的 JSON 元素
     * @return 格式化后的组件值字符串
     */
    private static String formatComponentJsonValue(com.google.gson.JsonElement element) {
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();

            // 特殊处理附魔
            if (obj.has("levels")) {
                var levelsObj = obj.getAsJsonObject("levels");
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (var entry : levelsObj.entrySet()) {
                    if (!first) sb.append(", ");
                    String enchId = entry.getKey();
                    if (enchId.contains(":")) {
                        enchId = enchId.substring(enchId.indexOf(":") + 1);
                    }
                    int level = entry.getValue().getAsInt();
                    sb.append(enchId).append("=").append(level);
                    first = false;
                }
                sb.append("}");
                return sb.toString();
            }

            // 其他对象格式
            return obj.toString();
        } else if (element.isJsonPrimitive()) {
            return element.getAsString();
        }

        return element.toString();
    }
}