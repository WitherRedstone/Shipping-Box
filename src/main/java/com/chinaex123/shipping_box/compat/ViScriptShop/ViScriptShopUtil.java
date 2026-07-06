package com.chinaex123.shipping_box.compat.ViScriptShop;

import net.minecraft.server.level.ServerPlayer;

/** ViScriptShop兼容工具类 **/
public class ViScriptShopUtil {
    private static Class<?> viScriptShopClass = null;
    private static java.lang.reflect.Method getMoneyMethod = null;
    private static java.lang.reflect.Method addMoneyMethod = null;
    private static boolean viScriptShopChecked = false;

    /**
     * 初始化ViScriptShop反射相关字段
     * 通过反射获取ViScriptShop的类和方法引用，避免直接依赖
     * 该方法只执行一次，通过viScriptShopChecked标志位控制
     */
    private static void initViScriptShopReflection() {
        if (viScriptShopChecked) return;

        try {
            // 通过反射获取ViScriptShop服务工具类
            viScriptShopClass = Class.forName("com.viscriptshop.util.ViScriptShopServerUtil");
            // 获取查询玩家余额的方法
            getMoneyMethod = viScriptShopClass.getMethod("getMoney", ServerPlayer.class);
            // 获取给玩家添加货币的方法
            addMoneyMethod = viScriptShopClass.getMethod("addMoney", ServerPlayer.class, int.class);
        } catch (Exception e) {
            // 如果反射失败，将类引用设为null表示ViScriptShop不可用
            viScriptShopClass = null;
        }
        viScriptShopChecked = true;
    }

    /**
     * 给玩家添加虚拟货币
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
     * 获取玩家虚拟货币余额
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
     * 检查ViScriptShop是否可用
     * @return 是否可用
     */
    public static boolean isAvailable() {
        initViScriptShopReflection();
        return viScriptShopClass != null;
    }
}
