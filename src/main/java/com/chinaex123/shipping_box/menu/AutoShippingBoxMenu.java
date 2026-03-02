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
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class AutoShippingBoxMenu extends ChestMenu {

    private record ItemHandlerContainer(ItemStackHandler itemHandler) implements Container {

        /**
         * 获取容器的总槽数量
         * <p>
         * 此方法返回自动售货箱菜单容器的存储容量，
         * 通过委托给底层的物品处理器来获取实际的槽位数量。
         *
         * @return 容器的槽数量，等于物品处理器的槽位数
         */
        @Override
        public int getContainerSize() {
            return itemHandler.getSlots();
        }

        /**
         * 检查容器是否为空
         * <p>
         * 遍历容器中的所有槽位，检查是否存在非空物品堆栈。
         * 如果所有槽位都为空，则返回true，否则返回false。
         *
         * @return 如果容器中没有物品则返回true，否则返回false
         */
        @Override
        public boolean isEmpty() {
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                if (!itemHandler.getStackInSlot(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * 获取指定槽位的物品堆栈
         * <p>
         * 通过委托给底层的物品处理器来获取指定索引位置的物品堆栈。
         *
         * @param slot 槽位索引
         * @return 指定槽位的物品堆栈
         */
        @Override
        public ItemStack getItem(int slot) {
            return itemHandler.getStackInSlot(slot);
        }

        /**
         * 从指定槽位移除指定数量的物品
         * <p>
         * 通过委托给底层的物品处理器来提取指定数量的物品，
         * 不进行模拟操作（实际移除物品）。
         *
         * @param slot 要移除物品的槽位索引
         * @param count 要移除的物品数量
         * @return 被移除的物品堆栈
         */
        @Override
        public ItemStack removeItem(int slot, int count) {
            return itemHandler.extractItem(slot, count, false);
        }

        /**
         * 从指定槽位移除物品但不触发容器更新
         * <p>
         * 与removeItem方法不同，此方法不会调用setChanged()来标记容器已更改。
         * 主要用于内部操作或批量处理时避免不必要的更新。
         *
         * @param slot 要移除物品的槽位索引
         * @return 从槽位中移除的物品堆栈，如果槽位为空则返回空堆栈
         */
        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
            return stack;
        }

        /**
         * 在指定槽位设置物品堆栈
         * <p>
         * 通过委托给底层的物品处理器来在指定位置放置物品堆栈。
         *
         * @param slot 目标槽位索引
         * @param stack 要设置的物品堆栈
         */
        @Override
        public void setItem(int slot, ItemStack stack) {
            itemHandler.setStackInSlot(slot, stack);
        }

        /**
         * 标记容器内容已更改
         * <p>
         * 此方法为空实现，因为ItemStackHandler会自动处理容器状态的变更标记。
         * 不需要手动调用setChanged()来标记容器已更改。
         */
        @Override
        public void setChanged() {
            // ItemStackHandler会自动处理
        }

        /**
         * 检查玩家是否仍然可以访问此容器
         * <p>
         * 此方法用于验证玩家是否有权限继续使用容器菜单。
         * 当前实现总是返回true，但可以根据需要添加距离检查或其他验证逻辑。
         *
         * @param player 要验证的玩家实体
         * @return 如果玩家可以继续访问容器则返回true，否则返回false
         */
        @Override
        public boolean stillValid(Player player) {
            return true; // 或者添加适当的距离检查
        }

        /**
         * 清空容器的所有内容
         * <p>
         * 此方法将容器中所有槽位的物品堆栈设置为空堆栈，
         * 实现容器内容的完全清空操作。
         */
        @Override
        public void clearContent() {
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }


    private final AutoShippingBoxBlockEntity blockEntity;
    private static BlockPos storedPos = null;
    private static Level storedLevel = null;

    public AutoShippingBoxMenu(int id, Inventory playerInventory, AutoShippingBoxBlockEntity blockEntity) {
        super(MenuType.GENERIC_9x6, id, playerInventory, new ItemHandlerContainer(blockEntity.getItemHandler()), 6);
        this.blockEntity = blockEntity;
        storedPos = blockEntity.getBlockPos();
        storedLevel = blockEntity.getLevel();
    }

    /**
     * 当菜单被移除时调用的回调方法
     * <p>
     * 此方法在玩家关闭容器菜单时执行清理操作，
     * 包括播放容器关闭音效等后续处理。
     *
     * @param player 关闭菜单的玩家实体
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
}
