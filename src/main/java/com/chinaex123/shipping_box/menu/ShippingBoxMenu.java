package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class ShippingBoxMenu extends ChestMenu {
    private final UUID playerUUID;
    private final ShippingBoxBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final Level level;

    // 静态变量用于音效
    private static BlockPos storedPos = null;
    private static Level storedLevel = null;

    /**
     * 运输箱菜单构造函数
     * 初始化菜单并建立与方块实体的连接
     *
     * @param id 菜单ID
     * @param playerInventory 玩家物品栏
     * @param blockEntity 关联的运输箱方块实体
     * @param playerUUID 当前玩家的唯一标识符
     */
    public ShippingBoxMenu(int id, Inventory playerInventory, ShippingBoxBlockEntity blockEntity, UUID playerUUID) {
        super(MenuType.GENERIC_9x6, id, playerInventory,
                new PlayerSpecificContainer(blockEntity, playerUUID), 6);
        this.playerUUID = playerUUID;
        this.blockEntity = blockEntity;
        this.blockPos = blockEntity.getBlockPos();
        this.level = blockEntity.getLevel();
        storedPos = blockEntity.getBlockPos();
        storedLevel = blockEntity.getLevel();
    }

    /**
     * 玩家专属容器包装类
     * 为特定玩家提供对运输箱存储的访问接口
     * 实现了Minecraft的Container接口，将玩家UUID与方块实体操作绑定
     */
    private record PlayerSpecificContainer(ShippingBoxBlockEntity blockEntity, UUID playerUUID) implements Container {

        /**
         * 获取容器的总槽数量
         *
         * @return 容器槽数，固定返回54格
         */
        @Override
        public int getContainerSize() {
            return 54;
        }

        /**
         * 检查容器是否为空
         * 通过检查玩家所有存储槽位是否都为空来判断
         *
         * @return 如果所有槽位都为空则返回true，否则返回false
         */
        @Override
        public boolean isEmpty() {
            return blockEntity.getPlayerItems(playerUUID).stream()
                    .allMatch(ItemStack::isEmpty);
        }

        /**
         * 获取指定槽位的物品
         *
         * @param slot 槽位索引
         * @return 指定槽位的物品堆栈
         */
        @Override
        public ItemStack getItem(int slot) {
            return blockEntity.getItemForPlayer(slot, playerUUID);
        }

        /**
         * 从指定槽位移除指定数量的物品
         *
         * @param slot 槽位索引
         * @param amount 要移除的物品数量
         * @return 被移除的物品堆栈
         */
        @Override
        public ItemStack removeItem(int slot, int amount) {
            return blockEntity.removeItemForPlayer(slot, amount, playerUUID);
        }

        /**
         * 从指定槽位移除物品但不触发容器更新
         *
         * @param slot 槽位索引
         * @return 被移除的物品堆栈
         */
        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return blockEntity.removeItemForPlayer(slot, 1, playerUUID);
        }

        /**
         * 在指定槽位设置物品堆栈
         *
         * @param slot 目标槽位索引
         * @param stack 要设置的物品堆栈
         */
        @Override
        public void setItem(int slot, ItemStack stack) {
            blockEntity.setItemForPlayer(slot, stack, playerUUID);
        }

        /**
         * 标记容器内容已更改，需要保存
         */
        @Override
        public void setChanged() {
            blockEntity.setChanged();
        }

        /**
         * 检查菜单对指定玩家是否仍然有效
         *
         * @param player 目标玩家
         * @return 总是返回true，表示菜单始终有效
         */
        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        /**
         * 清空容器内的所有内容
         */
        @Override
        public void clearContent() {
            blockEntity.getPlayerItems(playerUUID).clear();
        }
    }


    /**
     * 快速移动物品堆栈的核心逻辑方法
     * 处理玩家Shift点击物品时的快速移动操作
     * 支持运输箱存储与玩家物品栏之间的双向传输
     *
     * @param player 操作的玩家对象
     * @param index 被操作的槽位索引
     * @return 移动后的物品堆栈副本，如果操作失败则返回空堆栈
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack itemstack = slot.getItem();
        ItemStack itemstack1 = itemstack.copy();

        // 如果点击的是运输箱存储区域(0-53槽位)
        if (index < 54) {
            // 尝试将物品移动到玩家物品栏(54-末尾)
            if (!this.moveItemStackTo(itemstack, 54, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        }
        // 如果点击的是玩家物品栏区域(54-末尾)
        else if (!this.moveItemStackTo(itemstack, 0, 54, false)) {
            // 尝试将物品移动到运输箱存储区域(0-53)
            return ItemStack.EMPTY;
        }

        // 更新槽位状态
        if (itemstack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return itemstack1;
    }

    /**
     * 菜单关闭时的回调方法
     * 处理菜单移除逻辑并在服务端播放运输箱关闭音效
     *
     * @param player 关闭菜单的玩家对象
     */
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

    /**
     * 获取当前菜单关联的玩家UUID
     *
     * @return 玩家的唯一标识符
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * 获取当前菜单关联的运输箱方块实体
     *
     * @return 关联的运输箱方块实体实例
     */
    public ShippingBoxBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
