package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 兑换策略接口 **/
public interface ExchangeStrategy {
    /**
     * 执行兑换策略
     * @param rule 兑换规则
     * @param maxExchanges 最大兑换次数
     * @param level 世界实例
     * @param playerUUID 玩家UUID
     * @param results 结果物品列表（输出）
     * @param totalVirtualCurrency 总虚拟货币数量（输出）
     */
    void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency);
}
