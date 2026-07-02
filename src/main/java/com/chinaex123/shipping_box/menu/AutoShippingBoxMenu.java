package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.client.gui.ShippingBoxLayout;
import com.chinaex123.shipping_box.init.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/** Automated shipping box menu. */
public class AutoShippingBoxMenu extends AbstractContainerMenu {

    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;

    private final AutoShippingBoxBlockEntity blockEntity;
    private final Container shippingContainer;
    private final BlockPos menuPos;
    private final Level menuLevel;

    public AutoShippingBoxMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenuTypes.AUTO_SHIPPING_BOX.get(), id);
        this.menuPos = buf.readBlockPos();
        this.blockEntity = findBlockEntity(menuPos);
        this.menuLevel = blockEntity != null ? blockEntity.getLevel() : Minecraft.getInstance().level;
        this.shippingContainer = new SimpleContainer(54);
        addAllSlots(playerInventory);
    }

    public AutoShippingBoxMenu(int id, Inventory playerInventory, AutoShippingBoxBlockEntity blockEntity) {
        super(ModMenuTypes.AUTO_SHIPPING_BOX.get(), id);
        this.blockEntity = blockEntity;
        this.menuPos = blockEntity.getBlockPos();
        this.menuLevel = blockEntity.getLevel();
        this.shippingContainer = blockEntity;
        this.shippingContainer.startOpen(playerInventory.player);
        addAllSlots(playerInventory);
    }

    private AutoShippingBoxBlockEntity findBlockEntity(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AutoShippingBoxBlockEntity abe) return abe;
        }
        return null;
    }

    private void addAllSlots(Inventory playerInventory) {
        for (int row = 0; row < ShippingBoxLayout.CHEST_ROWS; row++) {
            for (int col = 0; col < ShippingBoxLayout.CHEST_COLS; col++) {
                this.addSlot(new Slot(this.shippingContainer,
                        col + row * ShippingBoxLayout.CHEST_COLS,
                        ShippingBoxLayout.CHEST_START_X + col * ShippingBoxLayout.SLOT_STEP,
                        ShippingBoxLayout.CHEST_START_Y + row * ShippingBoxLayout.SLOT_STEP));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        ShippingBoxLayout.PLAYER_INV_START_X + col * ShippingBoxLayout.SLOT_STEP,
                        ShippingBoxLayout.PLAYER_INV_START_Y + row * ShippingBoxLayout.SLOT_STEP));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    ShippingBoxLayout.HOTBAR_START_X + col * ShippingBoxLayout.SLOT_STEP,
                    ShippingBoxLayout.HOTBAR_START_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.isRemoved() || menuLevel == null || player.level() != menuLevel) {
            return false;
        }
        if (blockEntity.getLevel() != menuLevel || !menuPos.equals(blockEntity.getBlockPos())) {
            return false;
        }
        if (menuLevel.getBlockEntity(menuPos) != blockEntity) {
            return false;
        }
        return player.distanceToSqr(menuPos.getX() + 0.5D, menuPos.getY() + 0.5D, menuPos.getZ() + 0.5D) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack itemstack = slot.getItem();
        ItemStack itemstack1 = itemstack.copy();
        if (index < 54) {
            if (!this.moveItemStackTo(itemstack, 54, 90, true))
                return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(itemstack, 0, 54, false))
                return ItemStack.EMPTY;
        }
        if (itemstack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return itemstack1;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.shippingContainer.stopOpen(player);
        if (menuLevel != null && !menuLevel.isClientSide() && player instanceof ServerPlayer) {
            menuLevel.playSound(null, menuPos,
                    SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace("block.barrel.close")),
                    SoundSource.BLOCKS, 0.5F,
                    menuLevel.getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }

    public AutoShippingBoxBlockEntity getBlockEntity() { return blockEntity; }
}
