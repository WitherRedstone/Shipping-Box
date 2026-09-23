package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 兑换策略接口。
 * <p>
 * 定义各类兑换模式的统一执行入口。不同兑换模式（普通物品、
 * 权重随机、虚拟货币、动态定价等）通过实现本接口提供各自的处理逻辑，
 * 由兑换管理器按规则类型分派调用。
 */
public interface ExchangeStrategy {

    /**
     * 执行兑换策略。
     * <p>
     * 根据具体实现处理兑换逻辑，并通过输出参数
     * （结果物品列表与总虚拟货币数量）返回处理结果。
     *
     * @param rule                 兑换规则
     * @param maxExchanges         最大兑换次数
     * @param level                世界实例
     * @param playerUUID           玩家 UUID
     * @param results              结果物品列表（输出）
     * @param totalVirtualCurrency 总虚拟货币数量（输出）
     */
    void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency);
}