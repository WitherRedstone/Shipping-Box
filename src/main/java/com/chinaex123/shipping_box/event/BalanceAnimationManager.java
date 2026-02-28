package com.chinaex123.shipping_box.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * 虚拟货币余额动画管理器
 * 专门处理ViScriptShop余额增加时的动画效果
 * 直接复制爬爬币的动画实现以确保一致性
 */
@EventBusSubscriber
public class BalanceAnimationManager {

    // 存储玩家的动画状态
    private static final Map<UUID, AnimationState> animationStates = new HashMap<>();

    /**
     * 动画状态数据类
     * 用于跟踪虚拟货币兑换过程中的动画进度和相关数值
     */
    private static class AnimationState {
        /** 兑换开始时的余额 */
        final int startBalance;
        /** 兑换物品的总价值 */
        final int totalValue;
        /** 实际兑换金额 */
        final int exchangeAmount;
        /** 当前动画步数 */
        int currentStep = 0;
        /** 最大动画步数 */
        final int maxSteps = 20;

        /**
         * 构造函数
         *
         * @param startBalance 兑换开始时的余额
         * @param totalValue 兑换物品的总价值
         * @param exchangeAmount 实际兑换金额
         */
        AnimationState(int startBalance, int totalValue, int exchangeAmount) {
            this.startBalance = startBalance;
            this.totalValue = totalValue;
            this.exchangeAmount = exchangeAmount;
        }
    }

    /**
     * 开始余额动画
     *
     * @param player 玩家对象
     * @param startBalance 开始余额
     * @param totalValue 增加的总金额
     * @param exchangeAmount 兑换次数
     */
    public static void startAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        AnimationState state = new AnimationState(startBalance, totalValue, exchangeAmount);
        animationStates.put(player.getUUID(), state);
    }

    /**
     * 服务器tick事件处理器 - 用于更新动画
     * 完全复制爬爬币的实现
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 动画更新逻辑保持不变
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, AnimationState> entry : animationStates.entrySet()) {
            UUID playerId = entry.getKey();
            AnimationState state = entry.getValue();

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                toRemove.add(playerId);
                continue;
            }

            int currentBalance = state.startBalance + (int)((state.totalValue / 20.0) * state.currentStep);
            int increment = state.totalValue;

            player.displayClientMessage(Component.translatable("message.shipping_box.viscriptshop.balance_animation",
                    currentBalance,
                    increment), true);

            state.currentStep++;

            if (state.currentStep > state.maxSteps) {
                toRemove.add(playerId);
            }
        }

        for (UUID playerId : toRemove) {
            animationStates.remove(playerId);
        }
    }
}
