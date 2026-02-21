package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.IContainerFactory;

public class ShippingBoxMenu extends ChestMenu {

    private final BlockPos blockPos;
    private final Level level;

    // 添加静态变量来存储位置信息
    private static BlockPos storedPos = null;
    private static Level storedLevel = null;

    public ShippingBoxMenu(int id, Inventory playerInventory, ShippingBoxBlockEntity blockEntity) {
        super(MenuType.GENERIC_9x6, id, playerInventory, blockEntity, 6);
        this.blockPos = blockEntity.getBlockPos();
        this.level = blockEntity.getLevel();
        storedPos = blockEntity.getBlockPos();
        storedLevel = blockEntity.getLevel();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        // 播放木桶关闭声音
        if (storedLevel != null && !storedLevel.isClientSide && player instanceof ServerPlayer) {
            storedLevel.playSound(
                    null,
                    storedPos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.barrel.close")),
                    SoundSource.BLOCKS,
                    0.5F,
                    storedLevel.random.nextFloat() * 0.1F + 0.9F
            );
        }

        // 清除当前玩家记录
        ShippingBoxBlockEntity.clearCurrentPlayer();
    }

    public static class Factory implements IContainerFactory<ShippingBoxMenu> {
        @Override
        public ShippingBoxMenu create(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
            BlockPos pos = data.readBlockPos();
            Level level = playerInventory.player.level();
            ShippingBoxBlockEntity blockEntity = (ShippingBoxBlockEntity) level.getBlockEntity(pos);
            return new ShippingBoxMenu(windowId, playerInventory, blockEntity);
        }
    }
}
