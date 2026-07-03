package com.chinaex123.shipping_box.item;

import com.chinaex123.shipping_box.client.tooltip.TooltipItems;
import com.chinaex123.shipping_box.config.CommonConfig;
import com.chinaex123.shipping_box.storage.PlayerBalanceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

/** Physical coin item that converts into the mod's internal balance on use. */
public class CreeperCoinItem extends TooltipItems {
    private final int coinValue;

    public CreeperCoinItem(Properties properties, int coinValue, Supplier<List<Component>> tooltipSupplier) {
        super(properties, tooltipSupplier);
        this.coinValue = coinValue;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        if (!CommonConfig.ENABLE_VIRTUAL_CURRENCY.get()) {
            serverPlayer.sendSystemMessage(Component.translatable("message.shipping_box.virtual_currency.disabled"));
            return InteractionResult.CONSUME;
        }

        int exchangeAmount = player.isShiftKeyDown() ? stack.getCount() : 1;
        int totalValue = coinValue * exchangeAmount;
        PlayerBalanceManager.addBalance(serverPlayer, totalValue);
        stack.shrink(exchangeAmount);
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        PlayerBalanceManager.spawnSuccessParticles(serverPlayer);
        return InteractionResult.CONSUME;
    }

    public int getCoinValue() {
        return coinValue;
    }
}
