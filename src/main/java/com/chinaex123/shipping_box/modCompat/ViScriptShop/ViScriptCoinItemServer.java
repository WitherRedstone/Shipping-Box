package com.chinaex123.shipping_box.modCompat.ViScriptShop;

import com.chinaex123.shipping_box.tooltip.TooltipItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 支持ViScriptShop联动的硬币类
 * 提供右键兑换虚拟货币的功能
 */
public class ViScriptCoinItemServer extends TooltipItems {
    private final int coinValue;

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
     * 构造函数
     *
     * @param properties 物品属性
     * @param coinValue 硬币价值
     * @param tooltipSupplier Tooltip内容提供器
     */
    public ViScriptCoinItemServer(Properties properties, int coinValue, Supplier<List<Component>> tooltipSupplier) {
        super(properties, () -> {
            // 只有当ViScriptShop可用时才显示tooltip
            if (ModList.get().isLoaded("viscript_shop")) {
                // 显示联动相关的tooltip
                List<Component> tooltips = new ArrayList<>(tooltipSupplier.get());
                tooltips.add(Component.translatable("tooltip.item.shipping_box.viscriptshop.info"));
                tooltips.add(Component.translatable("tooltip.item.shipping_box.viscriptshop.right_click"));
                tooltips.add(Component.translatable("tooltip.item.shipping_box.viscriptshop.sneak_click"));
                return tooltips;
            }
            // ViScriptShop不可用时显示基础tooltip
            return tooltipSupplier.get();
        });
        this.coinValue = coinValue;
    }

    /**
     * 通过工具类获取ViScriptShop余额
     */
    private static int getViScriptShopMoney(ServerPlayer player) {
        return ViScriptShopUtil.getMoney(player);
    }

    /**
     * 通过工具类给ViScriptShop增加货币
     */
    private static boolean addViScriptShopMoney(ServerPlayer player, int amount) {
        return ViScriptShopUtil.addMoney(player, amount);
    }

    /**
     * 处理右键交互 - 兑换虚拟货币
     *
     * @param level 游戏世界
     * @param player 交互玩家
     * @param hand 交互手
     * @return 交互结果
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 检查ViScriptShop是否加载
        if (!ModList.get().isLoaded("viscript_shop")) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        // 检查是否为服务器玩家
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        // 执行ViScriptShop功能
        boolean isSneaking = player.isShiftKeyDown();
        int exchangeAmount = isSneaking ? stack.getCount() : 1;
        int totalValue = coinValue * exchangeAmount;

        try {
            // 获取玩家当前余额
            int currentBalance = getViScriptShopMoney(serverPlayer);

            // 开始动画
            startBalanceAnimation(serverPlayer, currentBalance, totalValue, exchangeAmount);

            // 执行实际的货币增加
            if (addViScriptShopMoney(serverPlayer, totalValue)) {
                // 消耗硬币
                stack.shrink(exchangeAmount);

                // 播放成功音效
                serverPlayer.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.0F);

                // 添加粒子效果 - 修正后的位置和参数
                spawnSuccessParticles(serverPlayer, player);
            } else {
                player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.exchange_failed"));
            }

        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.exchange_failed"));
            System.err.println("[ShippingBox] ViScriptShop integration failed: " + e.getMessage());
        }

        return InteractionResultHolder.success(stack);
    }

    /**
     * 生成成功粒子效果
     */
    private static void spawnSuccessParticles(ServerPlayer serverPlayer, Player player) {
        // 获取玩家的位置
        double x = player.getX();
        double y = player.getY() + 1.0; // 胸部高度
        double z = player.getZ();

        // 使用更简单直接的方法 - 发送到所有在范围内的玩家
        serverPlayer.serverLevel().sendParticles(
                ParticleTypes.HAPPY_VILLAGER, x, y, z, 12, 0.5, 0.5, 0.5, 0.5);
    }

    /**
     * 开始余额动画
     */
    private void startBalanceAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        AnimationState state = new AnimationState(startBalance, totalValue, exchangeAmount);
        animationStates.put(player.getUUID(), state);
    }

    /**
     * 服务器tick事件处理器 - 用于更新动画
     */
    @EventBusSubscriber
    public static class AnimationHandler {
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

    /**
     * 获取硬币价值
     *
     * @return 硬币价值
     */
    public int getCoinValue() {
        return coinValue;
    }
}
