package com.chinaex123.shipping_box.modCompat.ViScriptShop;

import net.minecraft.server.level.ServerPlayer;

/**
 * 货币提供者接口
 * 用于统一不同模组的虚拟货币系统
 */
public interface ICurrencyProvider {
    /**
     * 给玩家增加货币
     * @param player 目标玩家
     * @param amount 增加的货币数量
     */
    void addMoney(ServerPlayer player, int amount);

    /**
     * 获取玩家当前货币数量
     * @param player 目标玩家
     * @return 玩家的货币数量
     */
    int getMoney(ServerPlayer player);

    /**
     * 设置玩家货币数量
     * @param player 目标玩家
     * @param amount 要设置的货币数量
     */
    void setMoney(ServerPlayer player, int amount);

    /**
     * 从玩家扣除货币
     * @param player 目标玩家
     * @param amount 要扣除的货币数量
     * @return 实际扣除的货币数量（可能小于请求的数量）
     */
    int removeMoney(ServerPlayer player, int amount);
}
