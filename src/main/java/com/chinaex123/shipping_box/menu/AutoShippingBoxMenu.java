package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

public class AutoShippingBoxMenu extends ChestMenu {

    private final AutoShippingBoxBlockEntity blockEntity;
    private static BlockPos storedPos = null;
    private static Level storedLevel = null;

    public AutoShippingBoxMenu(int id, Inventory playerInventory, AutoShippingBoxBlockEntity blockEntity) {
        super(MenuType.GENERIC_9x6, id, playerInventory, blockEntity, 6);
        this.blockEntity = blockEntity;
        storedPos = blockEntity.getBlockPos();
        storedLevel = blockEntity.getLevel();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        // 播放关闭音效
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
    }
}
