package com.chinaex123.shipping_box.item;

import com.chinaex123.shipping_box.client.tooltip.TooltipItems;
import com.chinaex123.shipping_box.config.CommonConfig;
import com.chinaex123.shipping_box.storage.PlayerBalanceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 次元钱袋物品类。
 * <p>
 * 26.2 版使用模组内置余额替代旧外部货币依赖，但保留旧版交互：
 * 右键兑换背包硬币，潜行右键兑换准星容器中的硬币。
 */
public class DimensionalPouchItem extends TooltipItems {

    public DimensionalPouchItem(Properties properties) {
        super(properties, () -> List.of(
                Component.translatable("tooltip.item.shipping_box.dimensional_pouch.oh"),
                Component.translatable("tooltip.item.shipping_box.dimensional_pouch.right_click"),
                Component.translatable("tooltip.item.shipping_box.dimensional_pouch.sneak_click")
        ));
    }

    /**
     * 右键兑换背包内硬币；潜行右键兑换准星指向容器内硬币。
     */
    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (!CommonConfig.ENABLE_VIRTUAL_CURRENCY.get()) {
                serverPlayer.sendSystemMessage(Component.translatable("message.shipping_box.virtual_currency.disabled"));
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                HitResult hitResult = player.pick(5.0D, 0.0F, false);
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof Container container) {
                        PlayerBalanceManager.convertTargetContainerCoins(serverPlayer, container);
                        return InteractionResult.SUCCESS;
                    }
                }
                serverPlayer.sendSystemMessage(Component.translatable(
                        "message.shipping_box.dimensional_pouch.no_container_coins"));
            } else {
                PlayerBalanceManager.convertInventoryCoins(serverPlayer, serverPlayer.getInventory());
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * 工具方法:获取物品对应的硬币价值。
     * 由其他系统(ExchangeRecipe / DataComponent 驱动)调用。
     */
    public static int getCoinValue(Item item) {
        if (item == null) return 0;
        return PlayerBalanceManager.getCoinValue(new ItemStack(item));
    }

    /**
     * 扫描容器中所有硬币的总价值。
     * @return 总硬币价值(不修改容器)
     */
    public static int scanContainerCoins(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            int coin = PlayerBalanceManager.getCoinValue(s);
            if (coin > 0) total += coin * s.getCount();
        }
        return total;
    }

    /**
     * 从容器中移除所有硬币(实体货币上交)。
     * @return 移除的总价值
     */
    public static int removeContainerCoins(Container container) {
        int total = 0;
        List<Integer> cleared = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            int coin = PlayerBalanceManager.getCoinValue(s);
            if (coin > 0) {
                total += coin * s.getCount();
                cleared.add(i);
            }
        }
        for (int i : cleared) container.setItem(i, ItemStack.EMPTY);
        return total;
    }

    /**
     * 从背包中移除所有硬币。
     */
    public static int removeInventoryCoins(Inventory inventory) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            int coin = PlayerBalanceManager.getCoinValue(s);
            if (coin > 0) {
                total += coin * s.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        return total;
    }
}
