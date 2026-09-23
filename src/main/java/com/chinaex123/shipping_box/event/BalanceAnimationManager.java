package com.chinaex123.shipping_box.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * 虚拟货币余额动画管理器。
 * <p>
 * 专门处理 ViScriptShop 余额增加时的动画效果：
 * 在多个服务端刻内逐步推进余额数值，向玩家逐帧反馈变化过程。
 * 通过事件订阅自动注册到游戏事件总线。
 */
@EventBusSubscriber
public class BalanceAnimationManager {

    /** 存储玩家的动画状态，键为玩家 UUID */
    private static final Map<UUID, AnimationState> animationStates = new HashMap<>();

    /**
     * 动画状态数据类。
     * <p>
     * 用于跟踪虚拟货币兑换过程中的动画进度和相关数值。
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
         * 构造动画状态。
         *
         * @param startBalance   兑换开始时的余额
         * @param totalValue     兑换物品的总价值
         * @param exchangeAmount 实际兑换金额
         */
        AnimationState(int startBalance, int totalValue, int exchangeAmount) {
            this.startBalance = startBalance;
            this.totalValue = totalValue;
            this.exchangeAmount = exchangeAmount;
        }
    }

    /**
     * 开始余额动画。
     * <p>
     * 记录起始余额、总价值与兑换次数，由服务端刻事件逐帧推进。
     *
     * @param player         玩家对象
     * @param startBalance   开始余额
     * @param totalValue     增加的总金额
     * @param exchangeAmount 兑换次数
     */
    public static void startAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        AnimationState state = new AnimationState(startBalance, totalValue, exchangeAmount);
        animationStates.put(player.getUUID(), state);
    }

    /**
     * 服务器刻事件处理器 - 用于更新动画。
     * <p>
     * 每个服务端刻推进一次动画进度，向玩家发送当前余额与增量消息；
     * 玩家离线或动画达到最大步数时移除对应状态。
     *
     * @param event 服务端刻事件
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