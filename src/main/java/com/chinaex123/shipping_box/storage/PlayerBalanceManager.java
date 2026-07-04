package com.chinaex123.shipping_box.storage;

import com.chinaex123.shipping_box.config.CommonConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家余额管理器（服务端）
 * <p>
 * 提供虚拟货币余额的查询、增加、硬币转换等功能。
 * 管理余额变化时的动画效果，在服务端游戏刻中驱动动画进度更新。
 * 包含硬币价值识别和容器/背包扫描的静态工具方法。
 */
public final class PlayerBalanceManager {
    private static final Map<UUID, AnimationState> ANIMATIONS = new HashMap<>();

    private PlayerBalanceManager() {}

    public static int getBalance(ServerPlayer player) {
        return PlayerBalanceData.get((ServerLevel) player.level()).getBalance(player.getUUID());
    }

    public static int addBalance(ServerPlayer player, int amount) {
        if (!CommonConfig.ENABLE_VIRTUAL_CURRENCY.get()) {
            return getBalance(player);
        }
        if (amount <= 0) {
            return getBalance(player);
        }
        int startBalance = getBalance(player);
        int newBalance = PlayerBalanceData.get((ServerLevel) player.level()).addBalance(player.getUUID(), amount);
        startBalanceAnimation(player, startBalance, amount);
        return newBalance;
    }

    public static void convertInventoryCoins(ServerPlayer player, Inventory inventory) {
        if (!CommonConfig.ENABLE_VIRTUAL_CURRENCY.get()) {
            player.sendSystemMessage(Component.translatable("message.shipping_box.virtual_currency.disabled"));
            return;
        }
        int total = removeContainerCoins(inventory);
        if (total > 0) {
            addBalance(player, total);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.shipping_box.dimensional_pouch.current_balance",
                    getBalance(player)));
        }
    }

    public static void convertTargetContainerCoins(ServerPlayer player, Container container) {
        if (!CommonConfig.ENABLE_VIRTUAL_CURRENCY.get()) {
            player.sendSystemMessage(Component.translatable("message.shipping_box.virtual_currency.disabled"));
            return;
        }
        int total = removeContainerCoins(container);
        if (total > 0) {
            addBalance(player, total);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.shipping_box.dimensional_pouch.no_container_coins"));
        }
    }

    public static int getCoinValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        net.minecraft.resources.Identifier id =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"shipping_box".equals(id.getNamespace())) return 0;
        return switch (id.getPath()) {
            case "copper_creeper_coin" -> 1;
            case "iron_creeper_coin" -> 8;
            case "gold_creeper_coin" -> 16;
            case "diamond_creeper_coin" -> 64;
            case "emerald_creeper_coin" -> 256;
            case "netherite_creeper_coin" -> 512;
            case "symbols_chaos_creeper_coin" -> 4096;
            default -> 0;
        };
    }

    public static int scanContainerCoins(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            total += getCoinValue(stack) * stack.getCount();
        }
        return total;
    }

    public static int removeContainerCoins(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            int value = getCoinValue(stack);
            if (value > 0) {
                total += value * stack.getCount();
                container.setItem(i, ItemStack.EMPTY);
            }
        }
        return total;
    }

    public static void spawnSuccessParticles(ServerPlayer player) {
        ((ServerLevel) player.level()).sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                12, 0.5D, 0.5D, 0.5D, 0.5D);
    }

    private static void startBalanceAnimation(ServerPlayer player, int startBalance, int amount) {
        ANIMATIONS.put(player.getUUID(), new AnimationState(startBalance, amount));
    }

    public static void tickAnimations(net.minecraft.server.MinecraftServer server) {
        if (!CommonConfig.ENABLE_VIRTUAL_CURRENCY.get()) {
            ANIMATIONS.clear();
            return;
        }
        Iterator<Map.Entry<UUID, AnimationState>> iterator = ANIMATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AnimationState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            AnimationState state = entry.getValue();
            int currentBalance = state.startBalance + (int) ((state.amount / 20.0D) * state.currentStep);
            player.sendOverlayMessage(Component.translatable(
                    "message.shipping_box.virtual_currency.balance_animation",
                    currentBalance,
                    state.amount));
            state.currentStep++;
            if (state.currentStep > state.maxSteps) {
                iterator.remove();
            }
        }
    }

    private static final class AnimationState {
        private final int startBalance;
        private final int amount;
        private final int maxSteps = 20;
        private int currentStep;

        private AnimationState(int startBalance, int amount) {
            this.startBalance = startBalance;
            this.amount = amount;
        }
    }
}
