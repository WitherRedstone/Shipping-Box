package com.chinaex123.shipping_box.item;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.init.ModItems;
import com.chinaex123.shipping_box.compat.ViScriptShop.ViScriptShopUtil;
import com.chinaex123.shipping_box.client.tooltip.TooltipItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

/**
 * 次元钱包物品类。
 * <p>
 * 实现跨维度物品存储和动画效果。
 * 普通右键将背包中的实体硬币转换为虚拟货币（无硬币时查询余额），
 * 潜行右键将指向容器中的实体硬币转换为虚拟货币，
 * 转换过程中通过余额动画逐帧反馈变化。
 */
public class DimensionalPouchItem extends TooltipItems {

    /** 存储玩家的动画状态，键为玩家 UUID */
    private static final Map<UUID, AnimationState> animationStates = new HashMap<>();

    /**
     * 动画状态数据类。
     * <p>
     * 用于跟踪虚拟货币转换过程中的动画进度和相关数值。
     */
    private static class AnimationState {

        /** 转换开始时的余额 */
        final int startBalance;
        /** 转换物品的总价值 */
        final int totalValue;
        /** 实际转换金额 */
        final int exchangeAmount;
        /** 当前动画步数 */
        int currentStep = 0;
        /** 最大动画步数 */
        final int maxSteps = 20;

        /**
         * 构造动画状态。
         *
         * @param startBalance   转换开始时的余额
         * @param totalValue     转换物品的总价值
         * @param exchangeAmount 实际转换金额
         */
        AnimationState(int startBalance, int totalValue, int exchangeAmount) {
            this.startBalance = startBalance;
            this.totalValue = totalValue;
            this.exchangeAmount = exchangeAmount;
        }
    }

    /**
     * 构造次元钱包物品。
     * <p>
     * 根据 ViScriptShop 是否加载动态组合 Tooltip：
     * 已加载时显示联动说明与操作提示，未加载时仅显示基础提示。
     *
     * @param properties 物品属性
     */
    public DimensionalPouchItem(Properties properties) {
        super(properties, () -> {
            // 只有当 ViScriptShop 可用时才显示 tooltip
            if (ModList.get().isLoaded("viscript_shop")) {
                return List.of(
                        Component.translatable("tooltip.item.shipping_box.viscriptshop.info"),
                        Component.translatable("tooltip.item.shipping_box.viscriptshop.rule"),
                        Component.translatable("tooltip.item.shipping_box.dimensional_pouch.right_click"),
                        Component.translatable("tooltip.item.shipping_box.dimensional_pouch.sneak_click")
                );
            }
            // ViScriptShop 不可用时显示基础 tooltip
            return List.of(
                    Component.translatable("tooltip.item.shipping_box.dimensional_pouch.oh")
            );
        });
    }

    /**
     * 处理次元钱袋物品的使用逻辑。
     * <p>
     * 根据玩家是否潜行来决定执行不同的功能：
     * - 潜行时：将指向容器内的实体货币转换为虚拟货币；
     * - 非潜行时：处理玩家背包内的实体货币转换或查询虚拟货币余额。
     *
     * @param level  当前游戏世界
     * @param player 使用物品的玩家
     * @param hand   使用物品的手（主手或副手）
     * @return 交互结果
     */
    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!ModList.get().isLoaded("viscript_shop")) {
            return InteractionResult.FAIL;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        // 检查是否潜行
        if (player.isShiftKeyDown()) {
            // 潜行时尝试与方块交互
            HitResult hitResult = player.pick(5.0, 0.0f, false);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hitResult;
                BlockPos blockPos = blockHit.getBlockPos();
                BlockEntity blockEntity = level.getBlockEntity(blockPos);

                // 检查是否为容器
                if (blockEntity instanceof Container container) {
                    convertContainerCoinsToVirtual(serverPlayer, container);
                    return InteractionResult.SUCCESS;
                }
            }
        } else {
            // 非潜行时执行次元钱袋功能
            handlePouchAction(player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 将容器内的实体货币转换为虚拟货币。
     * <p>
     * 遍历指定容器的所有槽位，查找并统计其中的实体硬币，
     * 然后通过 ViScriptShop API 将这些硬币的价值转换为虚拟货币。
     *
     * @param player    执行转换操作的服务器玩家
     * @param container 要扫描和清空硬币的容器
     */
    private void convertContainerCoinsToVirtual(ServerPlayer player, Container container) {
        int totalConvertedValue = 0;
        List<Integer> slotsToRemove = new ArrayList<>();

        // 遍历容器槽位
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            int coinValue = getCoinValue(stack.getItem());
            if (coinValue > 0) {
                int count = stack.getCount();
                totalConvertedValue += coinValue * count;
                slotsToRemove.add(i);
            }
        }

        // 如果找到了可转换的硬币
        if (totalConvertedValue > 0) {
            try {
                // 获取当前余额并开始动画
                int currentBalance = ViScriptShopUtil.getMoney(player);
                startBalanceAnimation(player, currentBalance, totalConvertedValue, 1);

                // 添加虚拟货币
                if (ViScriptShopUtil.addMoney(player, totalConvertedValue)) {
                    // 清空找到的硬币槽位
                    for (int slot : slotsToRemove) {
                        container.setItem(slot, ItemStack.EMPTY);
                    }

                    // 播放成功音效
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.NOTE_BLOCK_CHIME.value(),
                            SoundSource.PLAYERS, 1.0F, 1.0F
                    );
                } else {
                    player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.exchange_failed"));
                }
            } catch (Exception e) {
                player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.exchange_failed"));
                ShippingBox.LOGGER.warn("[DimensionalPouchItem.convertContainerCoinsToVirtual] ViScriptShop 容器货币转换失败，玩家: {}，总价值: {}，涉及槽位: {}",
                        player.getUUID(), totalConvertedValue, slotsToRemove.size(), e);
            }
        } else {
            player.sendSystemMessage(Component.translatable("message.shipping_box.dimensional_pouch.no_container_coins"),true);
        }

        player.getMainHandItem();
    }

    /**
     * 处理次元钱袋的主要功能逻辑。
     * <p>
     * 检查玩家背包中是否存在实体货币，如果存在则进行转换，
     * 否则显示当前虚拟货币余额。
     *
     * @param player 使用次元钱袋的玩家
     */
    private void handlePouchAction(Player player) {
        if (player.level().isClientSide()) {
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // 检查背包中的实体货币
        Inventory inventory = player.getInventory();
        int totalConvertedValue = 0;
        List<ItemStack> coinsToRemove = new ArrayList<>();

        // 遍历玩家背包寻找实体货币
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            int coinValue = getCoinValue(stack.getItem());
            if (coinValue > 0) {
                int count = stack.getCount();
                totalConvertedValue += coinValue * count;
                coinsToRemove.add(stack);
            }
        }

        try {
            if (totalConvertedValue > 0) {
                // 有实体货币，进行转换
                convertCoinsToVirtual(serverPlayer, totalConvertedValue, coinsToRemove);
            } else {
                // 没有实体货币，显示余额
                showCurrentBalance(serverPlayer);
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.exchange_failed"));
            ShippingBox.LOGGER.warn("[DimensionalPouchItem.handlePouchAction] ViScriptShop 货币转换失败，玩家: {}，总价值: {}，涉及物品数: {}",
                    player.getUUID(), totalConvertedValue, coinsToRemove.size(), e);
        }
    }

    /**
     * 将实体货币转换为虚拟货币并播放动画效果。
     * <p>
     * 通过 ViScriptShop API 向玩家账户添加虚拟货币，
     * 成功后清空对应的实体硬币并播放音效。
     *
     * @param player        执行转换的服务器玩家
     * @param totalValue    要转换的总虚拟货币值
     * @param coinsToRemove 需要移除的实体硬币列表
     */
    private void convertCoinsToVirtual(ServerPlayer player, int totalValue, List<ItemStack> coinsToRemove) {
        // 获取玩家当前余额
        int currentBalance = ViScriptShopUtil.getMoney(player);

        // 开始动画
        startBalanceAnimation(player, currentBalance, totalValue, 1);

        if (ViScriptShopUtil.addMoney(player, totalValue)) {
            // 移除实体硬币
            for (ItemStack coinStack : coinsToRemove) {
                coinStack.setCount(0);
            }

            // 播放成功音效
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_CHIME.value(),
                    SoundSource.PLAYERS, 1.0F, 1.0F
            );

        } else {
            player.sendSystemMessage(Component.translatable("message.shipping_box.viscriptshop.exchange_failed"));
        }
    }

    /**
     * 显示当前虚拟货币余额。
     * <p>
     * 播放查询音效并向玩家显示当前余额。
     *
     * @param player 服务器玩家
     */
    private void showCurrentBalance(ServerPlayer player) {
        int currentBalance = ViScriptShopUtil.getMoney(player);

        // 播放查询音效
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 1.0F, 1.0F
        );

        // 显示余额信息
        player.sendSystemMessage(Component.translatable("message.shipping_box.dimensional_pouch.current_balance", currentBalance), true);

    }

    /**
     * 开始余额动画。
     * <p>
     * 记录起始余额、总价值与转换次数，由动画处理器逐帧推进。
     *
     * @param player         服务器玩家
     * @param startBalance   转换开始时的余额
     * @param totalValue     转换物品的总价值
     * @param exchangeAmount 实际转换金额
     */
    private void startBalanceAnimation(ServerPlayer player, int startBalance, int totalValue, int exchangeAmount) {
        AnimationState state = new AnimationState(startBalance, totalValue, exchangeAmount);
        animationStates.put(player.getUUID(), state);
    }

    /**
     * 获取物品对应的硬币价值。
     * <p>
     * 按硬币种类返回对应的虚拟货币价值，非硬币物品返回 0。
     *
     * @param item 待判断的物品
     * @return 硬币价值，非硬币返回 0
     */
    private int getCoinValue(Item item) {
        if (item == ModItems.COPPER_CREEPER_COIN.get()) return 1;
        if (item == ModItems.IRON_CREEPER_COIN.get()) return 8;
        if (item == ModItems.GOLD_CREEPER_COIN.get()) return 16;
        if (item == ModItems.DIAMOND_CREEPER_COIN.get()) return 64;
        if (item == ModItems.NETHERITE_CREEPER_COIN.get()) return 512;
        if (item == ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get()) return 4096;
        if (item == ModItems.EMERALD_CREEPER_COIN.get()) return 256;
        return 0;
    }

    /**
     * 服务器刻事件处理器 - 用于更新动画。
     * <p>
     * 每个服务端刻推进一次动画进度，向玩家发送当前余额与增量消息；
     * 玩家离线或动画达到最大步数时移除对应状态。
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

                Component message = Component.translatable("message.shipping_box.viscriptshop.balance_animation",
                        currentBalance,
                        increment);

                player.sendSystemMessage(message, true);

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
     * 清理动画状态。
     * <p>
     * 清空所有玩家的动画状态记录，通常在玩家登出时调用。
     */
    public static void clearAnimationStates() {
        animationStates.clear();
    }
}