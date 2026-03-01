package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicPricingManager {
    private static final String DATA_NAME = "dynamic_pricing_data";

    /**
     * 获取服务器的持久化存储管理器
     * <p>
     * 通过ServerLifecycleHooks获取当前运行的Minecraft服务器实例，
     * 并返回主世界的维度数据存储管理器。如果服务器未启动或不可用，
     * 则返回null。
     *
     * @return DimensionDataStorage 服务器主世界的持久化存储管理器，如果服务器不可用则返回null
     */
    private static DimensionDataStorage getStorage() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.overworld().getDataStorage();
        }
        return null;
    }

    /**
     * 获取或创建持久化数据实例
     * <p>
     * 通过获取服务器的持久化存储管理器，使用指定的数据工厂和数据名称
     * 来获取或创建PricingData实例。如果无法获取存储管理器，则返回null。
     *
     * @return PricingData 持久化数据实例，如果存储管理器不可用则返回null
     */
    private static PricingData getPricingData() {
        DimensionDataStorage storage = getStorage();
        if (storage != null) {
            return storage.computeIfAbsent(PricingData.FACTORY, DATA_NAME);
        }
        return null;
    }

    /**
     * 保存销售数据到持久化存储
     * <p>
     * 此方法用于触发数据的持久化保存操作。由于使用了Minecraft的SavedData机制，
     * 数据会在标记为脏数据(setDirty())时自动保存，因此此方法体为空实现。
     */
    public static void saveData() {}

    /**
     * 获取指定物品的已售出数量
     * <p>
     * 通过物品标识符查询对应的销售统计数据。如果数据存储不可用，
     * 则返回默认值0。
     *
     * @param itemIdentifier 物品标识符，用于定位特定物品的销售数据
     * @return int 指定物品的已售出数量，如果数据不可用则返回0
     */
    public static int getSoldCount(String itemIdentifier) {
        PricingData data = getPricingData();
        if (data != null) {
            int count = data.getCount(itemIdentifier);
            return count;
        }
        return 0;
    }

    /**
     * 增加指定物品的售出数量并同步到所有客户端
     * <p>
     * 此方法负责更新指定物品的销售统计数据，并确保数据在所有客户端间同步。
     * 在增加销售计数之前会先检查是否需要根据重置规则进行重置操作。
     *
     * @param itemIdentifier 物品标识符，用于定位特定物品的销售数据
     * @param count 要增加的销售数量
     */
    public static void addSoldCount(String itemIdentifier, int count) {
        PricingData data = getPricingData();
        if (data != null) {
            // 在增加销售计数前检查是否需要重置
            checkAndResetIfNeeded(itemIdentifier);

            int oldCount = data.getCount(itemIdentifier);
            data.addCount(itemIdentifier, count);
            int newCount = data.getCount(itemIdentifier);
            // 强制标记为脏数据以确保保存
            data.setDirty();

            // 同步到所有客户端
            ShippingBoxNetworking.sendSoldCountSync(itemIdentifier, newCount);
        }
    }

    /**
     * 检查并重置指定物品的销售计数（如果需要）
     * <p>
     * 根据物品的动态定价规则配置，判断是否需要重置销售计数。
     * 支持三种重置模式：
     * - day = -1: 永不重置，仅记录销售日期
     * - day = 0: 每日自动重置
     * - day > 0: 按指定天数周期重置
     *
     * @param itemIdentifier 物品标识符，用于定位对应的重置规则
     */
    private static void checkAndResetIfNeeded(String itemIdentifier) {
        PricingData data = getPricingData();
        if (data == null) {
            return;
        }

        // 查找对应的动态定价规则
        List<ExchangeRule> rules = ExchangeRecipeManager.getRules();

        for (ExchangeRule rule : rules) {
            ExchangeRule.OutputItem output = rule.getOutputItem();

            // 检查是否为目标物品且为动态定价模式
            if ("dynamic_pricing".equals(output.getType()) &&
                    output.getDynamicProperties() != null) {

                // 对于虚拟货币模式，使用输入物品作为标识符进行匹配
                String ruleItemIdentifier;
                if (output.isCoin()) {
                    // 虚拟货币模式使用输入物品作为标识符
                    ruleItemIdentifier = rule.getInputs().get(0).getItem();
                } else {
                    // 普通动态定价模式使用输出物品作为标识符
                    ruleItemIdentifier = output.getItem();
                }

                // 检查是否匹配
                if (itemIdentifier.equals(ruleItemIdentifier)) {
                    int resetDay = output.getDynamicProperties().getDay();

                    if (resetDay == -1) {
                        // day = -1: 永不重置，只记录销售日期
                        data.recordSaleDay(itemIdentifier);
                        break;
                    } else if (resetDay == 0) {
                        // day = 0: 每天重置
                        data.resetCount(itemIdentifier);
                        data.recordSaleDay(itemIdentifier);
                        break;
                    } else if (resetDay > 0) {
                        // day > 0: 按天数重置
                        boolean shouldReset = data.shouldResetCount(itemIdentifier, resetDay);

                        if (shouldReset) {
                            data.resetCount(itemIdentifier);
                            // 重置后重新记录当前日期
                            data.recordSaleDay(itemIdentifier);
                        } else {
                            // 如果不需要重置，更新销售日期
                            data.recordSaleDay(itemIdentifier);
                        }
                        break; // 找到对应规则后退出循环
                    }
                }
            }
        }
    }

    /**
     * 获取指定物品距离上次销售经过的天数
     * <p>
     * 查询指定物品最后一次销售至今经过的游戏天数，用于重置时间计算。
     * 如果物品从未销售过或数据不可用，则返回-1。
     *
     * @param itemIdentifier 物品标识符，用于定位特定物品的销售记录
     * @return 物品距离上次销售经过的天数，如果从未销售过则返回-1
     */
    public static int getDaysSinceLastSale(String itemIdentifier) {
        PricingData data = getPricingData();
        if (data != null) {
            return data.getDaysSinceLastSale(itemIdentifier);
        }
        return -1;
    }

    /**
     * 获取指定物品的重置剩余天数
     * <p>
     * 根据物品的动态定价规则配置，计算距离下次重置还需要多少天。
     * 支持三种重置模式的剩余天数计算：
     * - day = -1: 永不重置，返回-1
     * - day = 0: 每日重置，返回0（表示随时可以重置）
     * - day > 0: 按天数周期重置，返回具体剩余天数
     *
     * @param itemIdentifier 物品标识符，用于定位对应的重置规则
     * @return 剩余天数，负数表示已经超过重置时间，-1表示永不重置，0表示每日重置
     */
    public static int getResetRemainingDays(String itemIdentifier) {
        // 查找对应的重置天数配置
        List<ExchangeRule> rules = ExchangeRecipeManager.getRules();
        for (ExchangeRule rule : rules) {
            ExchangeRule.OutputItem output = rule.getOutputItem();
            if ("dynamic_pricing".equals(output.getType()) &&
                    output.getDynamicProperties() != null) {

                // 对于虚拟货币模式，使用输入物品作为标识符进行匹配
                String ruleItemIdentifier;
                if (output.isCoin()) {
                    // 虚拟货币模式使用输入物品作为标识符
                    ruleItemIdentifier = rule.getInputs().getFirst().getItem();
                } else {
                    // 普通动态定价模式使用输出物品作为标识符
                    ruleItemIdentifier = output.getItem();
                }

                // 检查是否匹配
                if (itemIdentifier.equals(ruleItemIdentifier)) {
                    int resetDay = output.getDynamicProperties().getDay();

                    if (resetDay == -1) {
                        // 永不重置
                        return -1;
                    } else if (resetDay == 0) {
                        // 每天重置，剩余天数总是0（表示随时可以重置）
                        return 0;
                    } else if (resetDay > 0) {
                        // 按天数重置
                        PricingData data = getPricingData();
                        if (data != null) {
                            return data.getResetRemainingDays(itemIdentifier, resetDay);
                        }
                    }
                    break;
                }
            }
        }
        return -1; // 未找到配置或无重置设置
    }

    /**
     * 获取所有物品的销售统计数据
     * <p>
     * 返回包含所有物品销售统计的映射表。如果数据存储不可用，
     * 则返回空的HashMap。
     *
     * @return Map<String, Integer> 物品标识符到销售数量的映射表
     */
    public static Map<String, Integer> getAllSoldCounts() {
        PricingData data = getPricingData();
        if (data != null) {
            return new HashMap<>(data.getData());
        }
        return new HashMap<>();
    }

    /**
     * 设置销售统计数据
     * <p>
     * 用指定的销售统计映射表替换当前的所有销售数据。
     * 如果数据存储不可用，则不执行任何操作。
     *
     * @param counts 销售统计映射表，键为物品标识符，值为对应的销售数量
     */
    public static void setAllSoldCounts(Map<String, Integer> counts) {
        PricingData data = getPricingData();
        if (data != null) {
            data.setData(counts);
        }
    }

    /**
     * 清空所有销售数据
     * <p>
     * 将所有物品的销售统计数据重置为空的HashMap。
     * 如果数据存储不可用，则不执行任何操作。
     */
    public static void clearAllData() {
        PricingData data = getPricingData();
        if (data != null) {
            data.setData(new HashMap<>());
        }
    }
}
