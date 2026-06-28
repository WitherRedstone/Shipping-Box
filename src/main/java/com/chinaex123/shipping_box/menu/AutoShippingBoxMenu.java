package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.client.gui.ShippingBoxLayout;
import com.chinaex123.shipping_box.init.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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

/**
 * 自动售货箱菜单类
 * <p>
 * 负责管理自动售货箱的 GUI 交互逻辑，包括：
 * - 物品槽位的布局和绑定
 * - 物品的快速移动（Shift + 点击）
 * - 玩家与方块的距离验证
 * - 打开/关闭时的音效播放
 */
public class AutoShippingBoxMenu extends AbstractContainerMenu {

    /** 最大交互距离（8 格，平方为 64） */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;

    /** 关联的方块实体 */
    private final AutoShippingBoxBlockEntity blockEntity;

    /** 容器对象 */
    private final Container shippingContainer;

    /** 菜单绑定的方块位置 */
    private final BlockPos menuPos;

    /** 菜单所在的游戏世界 */
    private final Level menuLevel;

    /**
     * 构造函数（通过网络缓冲区创建）
     * <p>
     * 用于客户端接收服务端发送的菜单数据时创建菜单实例。
     * 从缓冲区读取方块位置，然后查找对应的方块实体。
     *
     * @param id               菜单 ID
     * @param playerInventory  玩家物品栏
     * @param buf              网络缓冲区（包含方块位置数据）
     */
    public AutoShippingBoxMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenuTypes.AUTO_SHIPPING_BOX.get(), id);
        // 从缓冲区读取方块位置
        this.menuPos = buf.readBlockPos();
        // 根据位置查找方块实体
        this.blockEntity = findBlockEntity(menuPos);
        // 获取世界引用
        this.menuLevel = blockEntity != null ? blockEntity.getLevel() : Minecraft.getInstance().level;
        // 创建容器
        this.shippingContainer = new SimpleContainer(54);
        // 添加所有槽位
        addAllSlots(playerInventory);
    }

    /**
     * 构造函数（服务端创建）
     * <p>
     * 在服务端打开菜单时使用，直接引用方块实体。
     *
     * @param id               菜单 ID
     * @param playerInventory  玩家物品栏
     * @param blockEntity      自动售货箱方块实体
     */
    public AutoShippingBoxMenu(int id, Inventory playerInventory, AutoShippingBoxBlockEntity blockEntity) {
        super(ModMenuTypes.AUTO_SHIPPING_BOX.get(), id);
        this.blockEntity = blockEntity;
        this.menuPos = blockEntity.getBlockPos();
        this.menuLevel = blockEntity.getLevel();
        // 直接使用方块实体作为容器（它实现了 Container 接口）
        this.shippingContainer = blockEntity;
        // 通知容器开始被使用
        this.shippingContainer.startOpen(playerInventory.player);
        // 添加所有槽位
        addAllSlots(playerInventory);
    }

    /**
     * 根据方块位置查找自动售货箱方块实体
     * <p>
     * 仅在客户端使用，用于从网络缓冲区读取位置后查找对应的方块实体。
     *
     * @param pos 方块位置
     * @return 找到的方块实体，如果不存在则返回 null
     */
    private AutoShippingBoxBlockEntity findBlockEntity(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AutoShippingBoxBlockEntity abe) return abe;
        }
        return null;
    }

    /**
     * 添加所有槽位到菜单
     * <p>
     * 包括：
     * 1. 售货箱存储区域（9×6 = 54 格）
     * 2. 玩家背包（9×3 = 27 格）
     * 3. 玩家快捷栏（9×1 = 9 格）
     *
     * @param playerInventory 玩家物品栏
     */
    private void addAllSlots(Inventory playerInventory) {
        // ========== 售货箱存储区域 (54 格) ==========
        for (int row = 0; row < ShippingBoxLayout.CHEST_ROWS; row++) {
            for (int col = 0; col < ShippingBoxLayout.CHEST_COLS; col++) {
                this.addSlot(new Slot(this.shippingContainer,
                        col + row * ShippingBoxLayout.CHEST_COLS, // 槽位索引
                        ShippingBoxLayout.CHEST_START_X + col * ShippingBoxLayout.SLOT_STEP, // X 坐标
                        ShippingBoxLayout.CHEST_START_Y + row * ShippingBoxLayout.SLOT_STEP  // Y 坐标
                ));
            }
        }

        // ========== 玩家背包 (27 格) ==========
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory,
                        col + row * 9 + 9, // 背包槽位索引（9-35）
                        ShippingBoxLayout.PLAYER_INV_START_X + col * ShippingBoxLayout.SLOT_STEP,
                        ShippingBoxLayout.PLAYER_INV_START_Y + row * ShippingBoxLayout.SLOT_STEP
                ));
            }
        }

        // ========== 玩家快捷栏 (9 格) ==========
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory,
                    col, // 快捷栏槽位索引（0-8）
                    ShippingBoxLayout.HOTBAR_START_X + col * ShippingBoxLayout.SLOT_STEP,
                    ShippingBoxLayout.HOTBAR_START_Y
            ));
        }
    }

    /**
     * 检查玩家是否仍然可以访问此菜单
     * <p>
     * 验证条件：
     * 1. 方块实体存在且未被移除
     * 2. 世界引用有效且匹配
     * 3. 方块位置匹配
     * 4. 玩家距离方块不超过 8 格
     *
     * @param player 要检查的玩家
     * @return true 表示菜单仍然有效，false 表示应关闭菜单
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        // 检查方块实体是否存在且有效
        if (blockEntity == null || blockEntity.isRemoved() || menuLevel == null || player.level() != menuLevel) {
            return false;
        }
        // 检查方块位置是否一致
        if (blockEntity.getLevel() != menuLevel || !menuPos.equals(blockEntity.getBlockPos())) {
            return false;
        }
        // 检查方块实体是否仍然存在
        if (menuLevel.getBlockEntity(menuPos) != blockEntity) {
            return false;
        }
        // 检查玩家距离是否在 8 格以内
        return player.distanceToSqr(menuPos.getX() + 0.5D, menuPos.getY() + 0.5D, menuPos.getZ() + 0.5D)
                <= MAX_INTERACTION_DISTANCE_SQR;
    }

    /**
     * 快速移动物品（Shift + 点击）
     * <p>
     * 处理逻辑：
     * - 从售货箱移到玩家背包（index < 54 → 移动到 54-89）
     * - 从玩家背包移到售货箱（index ≥ 54 → 移动到 0-53）
     *
     * @param player 执行操作的玩家
     * @param index  被点击的槽位索引
     * @return 移动后的物品堆栈（空表示移动失败）
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack itemstack = slot.getItem();
        ItemStack itemstack1 = itemstack.copy();

        // ========== 从售货箱移到玩家背包 ==========
        if (index < 54) {
            // 尝试移动到玩家背包 (54-89)
            if (!this.moveItemStackTo(itemstack, 54, 90, true))
                return ItemStack.EMPTY;
        }
        // ========== 从玩家背包移到售货箱 ==========
        else {
            // 尝试移动到售货箱 (0-53)
            if (!this.moveItemStackTo(itemstack, 0, 54, false))
                return ItemStack.EMPTY;
        }

        // 更新槽位状态
        if (itemstack.isEmpty())
            slot.setByPlayer(ItemStack.EMPTY);
        else
            slot.setChanged();

        return itemstack1;
    }

    /**
     * 菜单被关闭/移除时的回调
     * <p>
     * 执行清理操作：
     * 1. 通知容器停止使用
     * 2. 播放关闭音效（服务端）
     *
     * @param player 关闭菜单的玩家
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        // 通知容器停止使用
        this.shippingContainer.stopOpen(player);

        // 服务端播放关闭音效
        if (menuLevel != null && !menuLevel.isClientSide && player instanceof ServerPlayer) {
            menuLevel.playSound(null, menuPos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.barrel.close")),
                    SoundSource.BLOCKS,
                    0.5F,
                    menuLevel.random.nextFloat() * 0.1F + 0.9F
            );
        }
    }

    /**
     * 获取关联的方块实体
     *
     * @return 自动售货箱方块实体
     */
    public AutoShippingBoxBlockEntity getBlockEntity() {
        return blockEntity;
    }
}