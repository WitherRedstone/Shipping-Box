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

/**
 * 苦力怕硬币物品
 * <p>
 * 可使用的物理硬币物品，右键点击可将其价值转换为模组内置虚拟货币余额。
 * 普通右键转换1个硬币，潜行右键转换全部堆叠的硬币。
 * 每种硬币有不同的面值（铜1、铁8、金16、钻石64、绿宝石256、下界合金512、混沌符印4096）。
 * 如果配置中禁用了虚拟货币功能，则无法使用。
 */
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
