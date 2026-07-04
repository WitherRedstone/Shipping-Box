package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.chinaex123.shipping_box.client.gui.ShippingBoxLayout;
import com.chinaex123.shipping_box.init.ModMenuTypes;
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

import java.util.UUID;

/**
 * 普通售货箱容器菜单
 * <p>
 * 管理普通售货箱的 GUI 交互逻辑。关键特性是使用 {@link PlayerSpecificContainer}
 * 封装每个玩家的独立物品存储。每个玩家打开普通售货箱时看到的是自己的物品，
 * 但所有玩家共享同一个方块实体。
 * 使用距离验证防止玩家在超出交互距离后操作箱子。
 */
public class ShippingBoxMenu extends AbstractContainerMenu {

    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;

    private final UUID playerUUID;
    private final ShippingBoxBlockEntity blockEntity;
    private final Container shippingContainer;
    private final BlockPos menuPos;
    private final Level menuLevel;

    public ShippingBoxMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenuTypes.SHIPPING_BOX.get(), id);
        this.menuPos = buf.readBlockPos();
        this.playerUUID = buf.readUUID();
        this.blockEntity = findBlockEntity(playerInventory.player.level(), menuPos);
        this.menuLevel = blockEntity != null ? blockEntity.getLevel() : playerInventory.player.level();
        this.shippingContainer = new SimpleContainer(54);
        addAllSlots(playerInventory);
    }

    public ShippingBoxMenu(int id, Inventory playerInventory, ShippingBoxBlockEntity blockEntity, UUID playerUUID) {
        super(ModMenuTypes.SHIPPING_BOX.get(), id);
        this.playerUUID = playerUUID;
        this.blockEntity = blockEntity;
        this.menuPos = blockEntity.getBlockPos();
        this.menuLevel = blockEntity.getLevel();
        this.shippingContainer = new PlayerSpecificContainer(blockEntity, playerUUID);
        this.shippingContainer.startOpen(playerInventory.player);
        addAllSlots(playerInventory);
    }

    private ShippingBoxBlockEntity findBlockEntity(Level level, BlockPos pos) {
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShippingBoxBlockEntity sbe) return sbe;
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

    private record PlayerSpecificContainer(ShippingBoxBlockEntity blockEntity, UUID playerUUID) implements Container {
        @Override public int getContainerSize() { return 54; }
        @Override public boolean isEmpty() {
            return blockEntity.getPlayerItems(playerUUID).stream().allMatch(ItemStack::isEmpty);
        }
        @Override public ItemStack getItem(int slot) {
            return blockEntity.getItemForPlayer(slot, playerUUID);
        }
        @Override public ItemStack removeItem(int slot, int amount) {
            return blockEntity.removeItemForPlayer(slot, amount, playerUUID);
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            return blockEntity.removeItemForPlayer(slot, 1, playerUUID);
        }
        @Override public void setItem(int slot, ItemStack stack) {
            blockEntity.setItemForPlayer(slot, stack, playerUUID);
        }
        @Override public void setChanged() { blockEntity.setChanged(); }
        @Override public boolean stillValid(Player player) { return isBlockEntityValid(blockEntity, player); }
        @Override public void clearContent() { blockEntity.getPlayerItems(playerUUID).clear(); }
    }

    @Override
    public boolean stillValid(Player player) {
        return isBlockEntityValid(blockEntity, player) && menuLevel == blockEntity.getLevel() && menuPos.equals(blockEntity.getBlockPos());
    }

    private static boolean isBlockEntityValid(ShippingBoxBlockEntity blockEntity, Player player) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() == null || player.level() != blockEntity.getLevel()) {
            return false;
        }
        BlockPos pos = blockEntity.getBlockPos();
        if (player.level().getBlockEntity(pos) != blockEntity) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack itemstack = slot.getItem();
        ItemStack itemstack1 = itemstack.copy();
        if (index < 54) {
            if (!this.moveItemStackTo(itemstack, 54, 90, true))
                return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(itemstack, 0, 54, false)) {
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

    public UUID getPlayerUUID() { return playerUUID; }
    public ShippingBoxBlockEntity getBlockEntity() { return blockEntity; }
}
