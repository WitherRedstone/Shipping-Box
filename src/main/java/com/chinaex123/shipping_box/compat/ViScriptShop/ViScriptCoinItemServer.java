package com.chinaex123.shipping_box.compat.ViScriptShop;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.client.tooltip.TooltipItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

/**
 * 支持 ViScriptShop 联动的硬币类。
 * <p>
 * 提供右键兑换虚拟货币的功能：普通右键兑换单个硬币，
 * 潜行右键兑换整组硬币。兑换成功时播放音效并生成粒子，
 * 同时通过余额动画向玩家逐帧反馈余额变化。
 */
public class ViScriptCoinItemServer extends TooltipItems {

    /** 单枚硬币的兑换价值 */
    private final int coinValue;

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
     * 构造 ViScriptShop 联动硬币物品。
     * <p>
     * 根据 ViScriptShop 是否加载动态组合 Tooltip：
     * 已加载时额外追加联动说明与操作提示，未加载时仅显示基础提示。
     *
     * @param properties      物品属性
     * @param coinValue       硬币价值
     * @param tooltipSupplier Tooltip 内容提供器
     */
    public ViScriptCoinItemServer(Properties properties, int coinValue, Supplier<List<Component>> tooltipSupplier) {
        super(properties, () -> {
            // 只有当 ViScriptShop 可用时才显示 tooltip
            if (ModList.get().isLoaded("viscript_shop")) {
                // 显示联动相关的 tooltip
                List<Component> tooltips = new ArrayList<>(tooltipSupplier.get());
                tooltips.add(Component.translatable("tooltip.item.shipping_box.viscriptshop.info"));
                tooltips.add(Component.translatable("tooltip.item.shipping_box.viscriptshop.right_click"));
                tooltips.add(Component.translatable("tooltip.item.shipping_box.viscriptshop.sneak_click"));
                return tooltips;
            }
            // ViScriptShop 不可用时显示基础 tooltip
            return tooltipSupplier.get();
        });
        this.coinValue = coinValue;
    }

    /**
     * 通过工具类获取 ViScriptShop 余额。
     *
     * @param player 目标玩家
     * @return 玩家当前余额
     */
    private static int getViScriptShopMoney(ServerPlayer player) {
        return ViScriptShopUtil.getMoney(player);
    }

    /**
     * 通过工具类给 ViScriptShop 增加货币。
     *
     * @param player 目标玩家
     * @param amount 增加的货币数量
     * @return 是否成功增加
     */
    private static boolean addViScriptShopMoney(ServerPlayer player, int amount) {
        return ViScriptShopUtil.addMoney(player, amount);
    }

    /**
     * 处理右键交互 - 兑换虚拟货币。
     * <p>
     * ViScriptShop 未加载时直接放行；客户端仅返回成功；
     * 服务端根据是否潜行决定兑换数量（潜行为整组，否则为单个），
     * 兑换成功后扣减物品并播放音效、生成粒子。
     *
     * @param level  游戏世界
     * @param player 交互玩家
     * @param hand   交互手
     * @return 交互结果
     */
    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!ModList.get().isLoaded("viscript_shop")) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        boolean isSneaking = player.isShiftKeyDown();
        int exchangeAmount = isSneaking ? stack.getCount() : 1;
        int totalValue = coinValue * exchangeAmount;

        try {
            int currentBalance = getViScriptShopMoney(serverPlayer);
            startBalanceAnimation(serverPlayer, currentBalance, totalValue, exchangeAmount);

            if (addViScriptShopMoney(serverPlayer, totalValue)) {
                stack.shrink(exchangeAmount);
                serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.0F
                );
                spawnSuccessParticles(serverPlayer, player);
            } else {
                player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.exchange_failed"));
            }

        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.exchange_failed"));
            ShippingBox.LOGGER.warn("[CoinItem.use] ViScriptShop 货币兑换失败，玩家: {}，数量: {}，总价值: {}",
                    player.getUUID(), exchangeAmount, totalValue, e);
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * 生成成功粒子效果。
     * <p>
     * 在玩家胸部高度位置生成 12 个"快乐村民"粒子。
     *
     * @param serverPlayer 服务端玩家
     * @param player       玩家实体
     */
    private static void spawnSuccessParticles(ServerPlayer serverPlayer, Player player) {
        // 获取玩家的位置
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        // 使用更简单直接的方法 - 发送到所有在范围内的玩家
        if (serverPlayer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 12, 0.5, 0.5, 0.5, 0.5);
        }
    }

    /**
     * 开始余额动画。
     * <p>
     * 记录起始余额、总价值与兑换数量，由动画处理器逐帧推进。
     *
     * @param player         服务端玩家
     * @param startBalance   兑换开始时的余额
     * @param totalValue     兑换物品的总价值
     * @param exchangeAmount 实际兑换金额
     */
    private void startBalanceAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        AnimationState state = new AnimationState(startBalance, totalValue, exchangeAmount);
        animationStates.put(player.getUUID(), state);
    }

    /**
     * 服务器刻事件处理器 - 用于更新动画。
     * <p>
     * 每个服务端刻推进一次动画进度，向玩家发送当前余额，
     * 并移除已登出或动画结束的玩家状态。
     */
    @EventBusSubscriber
    public static class AnimationHandler {

        /**
         * 服务端刻事件处理器。
         * <p>
         * 遍历所有动画状态，为在线玩家发送余额进度消息并推进步数；
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

                player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.balance_animation",
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
     * 获取硬币价值。
     *
     * @return 硬币价值
     */
    public int getCoinValue() {
        return coinValue;
    }
}