package com.chinaex123.shipping_box.compat.ViScriptShop;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.server.level.ServerPlayer;

/**
 * ViScriptShop 兼容工具类。
 * <p>
 * 通过反射机制与 ViScriptShop 模组进行交互，
 * 在不直接依赖该模组的情况下查询与增加玩家的虚拟货币。
 * 反射字段仅初始化一次，并通过标志位控制避免重复检测。
 */
public class ViScriptShopUtil {

    /** ViScriptShop 服务工具类引用，为 null 表示模组不可用 */
    private static Class<?> viScriptShopClass = null;
    /** 查询玩家余额的方法引用 */
    private static java.lang.reflect.Method getMoneyMethod = null;
    /** 给玩家添加货币的方法引用 */
    private static java.lang.reflect.Method addMoneyMethod = null;
    /** 是否已完成反射初始化检查 */
    private static boolean viScriptShopChecked = false;

    /**
     * 初始化 ViScriptShop 反射相关字段。
     * <p>
     * 通过反射获取 ViScriptShop 的类和方法引用，避免直接依赖。
     * 该方法只执行一次，通过 viScriptShopChecked 标志位控制。
     */
    private static void initViScriptShopReflection() {
        if (viScriptShopChecked) return;

        try {
            // 通过反射获取 ViScriptShop 服务工具类
            viScriptShopClass = Class.forName("com.viscriptshop.util.ViScriptShopServerUtil");
            // 获取查询玩家余额的方法
            getMoneyMethod = viScriptShopClass.getMethod("getMoney", ServerPlayer.class);
            // 获取给玩家添加货币的方法
            addMoneyMethod = viScriptShopClass.getMethod("addMoney", ServerPlayer.class, int.class);
        } catch (Exception e) {
            ShippingBox.LOGGER.warn("[ViScriptShopUtil] ViScriptShop 反射初始化失败: {}", e.getMessage());
            viScriptShopClass = null;
        }
        viScriptShopChecked = true;
    }

    /**
     * 给玩家添加虚拟货币。
     * <p>
     * 反射调用失败或模组不可用时返回 false。
     *
     * @param player 服务器玩家
     * @param amount 要添加的金额
     * @return 是否成功
     */
    public static boolean addMoney(ServerPlayer player, int amount) {
        initViScriptShopReflection();
        if (viScriptShopClass == null) return false;

        try {
            addMoneyMethod.invoke(null, player, amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取玩家虚拟货币余额。
     * <p>
     * 反射调用失败或模组不可用时返回 0。
     *
     * @param player 服务器玩家
     * @return 余额
     */
    public static int getMoney(ServerPlayer player) {
        initViScriptShopReflection();
        if (viScriptShopClass == null) return 0;

        try {
            return (Integer) getMoneyMethod.invoke(null, player);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 检查 ViScriptShop 是否可用。
     *
     * @return 是否可用
     */
    public static boolean isAvailable() {
        initViScriptShopReflection();
        return viScriptShopClass != null;
    }
}