package com.chinaex123.shipping_box.event.strategy;

import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 兑换策略接口
 * <p>
 * 定义兑换策略的统一契约，所有具体策略实现类需实现此接口。
 * 采用策略模式支持不同兑换规则的差异化处理，
 * 包括普通物品、虚拟货币、动态定价、权重随机等多种策略变体。
 */
public interface ExchangeStrategy {
    /**
     * 执行兑换策略
     * <p>
     * 根据兑换规则、最大兑换次数和其他上下文信息，
     * 计算并生成兑换结果物品及虚拟货币数量。
     * 具体的计算逻辑由各策略实现类自行决定。
     *
     * @param rule                  兑换规则，包含输入物品和输出配置信息
     * @param maxExchanges          最大兑换次数，决定输出的总批量大小
     * @param level                 当前世界实例，用于获取上下文环境
     * @param playerUUID            执行兑换的玩家 UUID，用于属性加成等个性化计算
     * @param results               输出结果物品列表（方法执行后填充）
     * @param totalVirtualCurrency  输出总虚拟货币数量（方法执行后累加）
     */
    void execute(ExchangeRule rule, int maxExchanges, Level level, UUID playerUUID, List<ItemStack> results, AtomicInteger totalVirtualCurrency);
}
