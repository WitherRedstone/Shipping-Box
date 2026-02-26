//package com.chinaex123.shipping_box.attribute.utils;
//
//import net.minecraft.nbt.CompoundTag;
//import net.minecraft.world.entity.player.Player;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.UUID;
//
///**
// * 玩家出售价格属性管理类
// * 管理每个玩家的出售价格加成系数
// */
//public class SellingPriceBoost {
//
//    /** 存储玩家UUID到出售价格系数的映射 */
//    private static final Map<UUID, Double> playerSellingPrices = new HashMap<>();
//
//    /** 默认出售价格系数 */
//    private static final double DEFAULT_SELLING_PRICE = 0.0;
//
//    /**
//     * 获取玩家的出售价格系数
//     *
//     * @param player 目标玩家
//     * @return 玩家的出售价格系数，如果未设置则返回默认值0.0
//     */
//    public static double getSellingPrice(Player player) {
//        if (player == null) return DEFAULT_SELLING_PRICE;
//
//        UUID playerUUID = player.getUUID();
//        return playerSellingPrices.getOrDefault(playerUUID, DEFAULT_SELLING_PRICE);
//    }
//
//    /**
//     * 设置玩家的出售价格系数
//     *
//     * @param player 目标玩家
//     * @param price 出售价格系数（0.0表示无加成，0.2表示20%加成）
//     */
//    public static void setSellingPrice(Player player, double price) {
//        if (player == null) return;
//
//        UUID playerUUID = player.getUUID();
//        playerSellingPrices.put(playerUUID, Math.max(0.0, price)); // 确保不为负数
//    }
//
//    /**
//     * 计算加成后的物品数量
//     *
//     * @param player 玩家对象
//     * @param baseAmount 基础物品数量
//     * @return 加成后的物品数量（向下取整）
//     */
//    public static int calculateEnhancedAmount(Player player, int baseAmount) {
//        double sellingPrice = getSellingPrice(player);
//        if (sellingPrice <= 0.0) {
//            return baseAmount; // 无加成时返回原数量
//        }
//
//        // 计算加成后的数量：基础数量 × (1 + 加成系数)
//        double enhancedAmount = baseAmount * (1.0 + sellingPrice);
//        return (int) Math.floor(enhancedAmount); // 向下取整确保不会超出预期
//    }
//
//    /**
//     * 增加玩家的出售价格系数
//     *
//     * @param player 目标玩家
//     * @param increment 要增加的系数值
//     */
//    public static void increaseSellingPrice(Player player, double increment) {
//        if (player == null || increment <= 0.0) return;
//
//        double currentPrice = getSellingPrice(player);
//        setSellingPrice(player, currentPrice + increment);
//    }
//
//    /**
//     * 减少玩家的出售价格系数
//     *
//     * @param player 目标玩家
//     * @param decrement 要减少的系数值
//     */
//    public static void decreaseSellingPrice(Player player, double decrement) {
//        if (player == null || decrement <= 0.0) return;
//
//        double currentPrice = getSellingPrice(player);
//        setSellingPrice(player, Math.max(0.0, currentPrice - decrement));
//    }
//
//    /**
//     * 重置玩家的出售价格系数为默认值
//     *
//     * @param player 目标玩家
//     */
//    public static void resetSellingPrice(Player player) {
//        if (player == null) return;
//
//        UUID playerUUID = player.getUUID();
//        playerSellingPrices.remove(playerUUID);
//    }
//
//    /**
//     * 检查玩家是否设置了出售价格加成
//     *
//     * @param player 目标玩家
//     * @return 如果有加成返回true，否则返回false
//     */
//    public static boolean hasSellingPriceBoost(Player player) {
//        return getSellingPrice(player) > 0.0;
//    }
//
//    /**
//     * 获取格式化的加成信息字符串
//     *
//     * @param player 目标玩家
//     * @return 格式化的加成百分比字符串，如"+20%"
//     */
//    public static String getFormattedBoostInfo(Player player) {
//        double sellingPrice = getSellingPrice(player);
//        if (sellingPrice <= 0.0) {
//            return "";
//        }
//        return String.format("+%.0f%%", sellingPrice * 100);
//    }
//
//    /**
//     * 将所有玩家的出售价格数据保存到NBT标签
//     *
//     * @param tag 目标NBT复合标签
//     */
//    public static void saveToNBT(CompoundTag tag) {
//        CompoundTag sellingPricesTag = new CompoundTag();
//
//        for (Map.Entry<UUID, Double> entry : playerSellingPrices.entrySet()) {
//            sellingPricesTag.putDouble(entry.getKey().toString(), entry.getValue());
//        }
//
//        tag.put("PlayerSellingPrices", sellingPricesTag);
//    }
//
//    /**
//     * 从NBT标签加载所有玩家的出售价格数据
//     *
//     * @param tag 包含数据的NBT复合标签
//     */
//    public static void loadFromNBT(CompoundTag tag) {
//        playerSellingPrices.clear();
//
//        if (tag.contains("PlayerSellingPrices")) {
//            CompoundTag sellingPricesTag = tag.getCompound("PlayerSellingPrices");
//
//            for (String key : sellingPricesTag.getAllKeys()) {
//                try {
//                    UUID playerUUID = UUID.fromString(key);
//                    double sellingPrice = sellingPricesTag.getDouble(key);
//                    if (sellingPrice > 0.0) { // 只加载有效的加成数据
//                        playerSellingPrices.put(playerUUID, sellingPrice);
//                    }
//                } catch (IllegalArgumentException e) {
//                    // 无效的UUID格式，跳过
//                }
//            }
//        }
//    }
//
//    /**
//     * 清除所有玩家的出售价格数据（仅用于调试或重置）
//     */
//    public static void clearAllData() {
//        playerSellingPrices.clear();
//    }
//}
