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
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 普通售货箱容器菜单
 * <p>
 * 管理普通售货箱的 GUI 交互逻辑。关键特性是使用 {@link PlayerSpecificContainer}
 * 封装每个玩家的独立物品存储。每个玩家打开普通售货箱时看到的是自己的物品，
 * 但所有玩家共享同一个方块实体。
 * 使用距离验证防止玩家在超出交互距离后操作箱子。
 * 普通售货箱菜单类
 * <p>
 * 负责管理普通售货箱的 GUI 交互逻辑，包括：
 * - 玩家独立存储（每个玩家拥有自己的 54 格存储空间）
 * - 物品槽位的布局和绑定
 * - 物品的快速移动（Shift + 点击）
 * - 玩家与方块的距离验证
 * - 打开/关闭时的音效播放
 */
public class ShippingBoxMenu extends AbstractContainerMenu {

    /** 最大交互距离（8 格，平方为 64） */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;

    /** 当前玩家的 UUID（用于加载对应的个人存储） */
    private final UUID playerUUID;

    /** 关联的方块实体 */
    private final ShippingBoxBlockEntity blockEntity;

    /** 容器对象（玩家独立存储的包装器） */
    private final Container shippingContainer;

    /** 菜单绑定的方块位置 */
    private final BlockPos menuPos;

    /** 菜单所在的游戏世界 */
    private final Level menuLevel;

    /**
     * 构造函数（通过网络缓冲区创建）
     * <p>
     * 用于客户端接收服务端发送的菜单数据时创建菜单实例。
     * 从缓冲区读取玩家 UUID 和方块位置，然后查找对应的方块实体。
     *
     * @param id 菜单 ID
     * @param playerInventory 玩家物品栏
     * @param buf 网络缓冲区（包含玩家 UUID 和方块位置）
     */
    public ShippingBoxMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenuTypes.SHIPPING_BOX.get(), id);
        this.menuPos = buf.readBlockPos();
        this.playerUUID = buf.readUUID();
        this.blockEntity = findBlockEntity(playerInventory.player.level(), menuPos);
        this.menuLevel = blockEntity != null ? blockEntity.getLevel() : playerInventory.player.level();
        // 从缓冲区读取玩家 UUID（用于标识哪个玩家的存储）
        this.playerUUID = buf.readUUID();
        // 从缓冲区读取方块位置
        this.menuPos = buf.readBlockPos();
        // 根据位置查找方块实体
        this.blockEntity = findBlockEntity(menuPos);
        // 获取世界引用
        this.menuLevel = blockEntity != null ? blockEntity.getLevel() : Minecraft.getInstance().level;
        // 创建容器（客户端使用）
        this.shippingContainer = new SimpleContainer(54);
        // 添加所有槽位
        addAllSlots(playerInventory);
    }

    /**
     * 构造函数（服务端创建）
     * <p>
     * 在服务端打开菜单时使用，直接引用方块实体和玩家 UUID。
     *
     * @param id 菜单 ID
     * @param playerInventory 玩家物品栏
     * @param blockEntity 普通售货箱方块实体
     * @param playerUUID 当前玩家的 UUID
     */
    public ShippingBoxMenu(int id, Inventory playerInventory, ShippingBoxBlockEntity blockEntity, UUID playerUUID) {
        super(ModMenuTypes.SHIPPING_BOX.get(), id);
        this.playerUUID = playerUUID;
        this.blockEntity = blockEntity;
        this.menuPos = blockEntity.getBlockPos();
        this.menuLevel = blockEntity.getLevel();
        // 使用 PlayerSpecificContainer 包装玩家独立存储
        this.shippingContainer = new PlayerSpecificContainer(blockEntity, playerUUID);
        // 通知容器开始被使用
        this.shippingContainer.startOpen(playerInventory.player);
        // 添加所有槽位
        addAllSlots(playerInventory);
    }

    private ShippingBoxBlockEntity findBlockEntity(Level level, BlockPos pos) {
    /**
     * 根据方块位置查找普通售货箱方块实体
     * <p>
     * 仅在客户端使用，用于从网络缓冲区读取位置后查找对应的方块实体。
     *
     * @param pos 方块位置
     * @return 找到的方块实体，如果不存在则返回 null
     */
    private ShippingBoxBlockEntity findBlockEntity(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShippingBoxBlockEntity sbe) return sbe;
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
     * 玩家独立存储容器包装器
     * <p>
     * 实现 Container 接口，将方块实体的玩家独立存储操作包装为标准的容器接口。
     * 所有操作都会通过 playerUUID 路由到对应的玩家存储空间。
     *
     * @param blockEntity 普通售货箱方块实体
     * @param playerUUID  当前玩家的 UUID
     */
    private record PlayerSpecificContainer(ShippingBoxBlockEntity blockEntity, UUID playerUUID) implements Container {

        /** 获取容器大小（固定 54 格） */
        @Override
        public int getContainerSize() {
            return 54;
        }

        /** 检查容器是否为空 */
        @Override
        public boolean isEmpty() {
            return blockEntity.getPlayerItems(playerUUID).stream().allMatch(ItemStack::isEmpty);
        }

        /** 获取指定槽位的物品 */
        @Override
        public @NotNull ItemStack getItem(int slot) {
            return blockEntity.getItemForPlayer(slot, playerUUID);
        }

        /** 从指定槽位移除指定数量的物品 */
        @Override
        public @NotNull ItemStack removeItem(int slot, int amount) {
            return blockEntity.removeItemForPlayer(slot, amount, playerUUID);
        }

        /** 从指定槽位移除物品（不更新） */
        @Override
        public @NotNull ItemStack removeItemNoUpdate(int slot) {
            return blockEntity.removeItemForPlayer(slot, 1, playerUUID);
        }

        /** 在指定槽位设置物品 */
        @Override
        public void setItem(int slot, ItemStack stack) {
            blockEntity.setItemForPlayer(slot, stack, playerUUID);
        }

        /** 标记容器已变更 */
        @Override
        public void setChanged() {
            blockEntity.setChanged();
        }

        /** 检查玩家是否仍然可以访问此容器 */
        @Override
        public boolean stillValid(Player player) {
            return isBlockEntityValid(blockEntity, player);
        }

        /** 清空容器内容 */
        @Override
        public void clearContent() {
            blockEntity.getPlayerItems(playerUUID).clear();
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
    public boolean stillValid(Player player) {
        return isBlockEntityValid(blockEntity, player)
                && menuLevel == blockEntity.getLevel()
                && menuPos.equals(blockEntity.getBlockPos());
    }

    /**
     * 检查方块实体是否有效且玩家在范围内
     *
     * @param blockEntity 方块实体
     * @param player      玩家
     * @return true 表示有效
     */
    private static boolean isBlockEntityValid(ShippingBoxBlockEntity blockEntity, Player player) {
        // 检查方块实体是否存在且有效
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() == null
                || player.level() != blockEntity.getLevel()) {
            return false;
        }
        // 检查方块实体是否仍然存在
        BlockPos pos = blockEntity.getBlockPos();
        if (player.level().getBlockEntity(pos) != blockEntity) {
            return false;
        }
        // 检查玩家距离是否在 8 格以内
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
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
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
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
    public void removed(Player player) {
        super.removed(player);
        // 通知容器停止使用
        this.shippingContainer.stopOpen(player);
        if (menuLevel != null && !menuLevel.isClientSide() && player instanceof ServerPlayer) {
            menuLevel.playSound(null, menuPos,
                    SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace("block.barrel.close")),
                    SoundSource.BLOCKS, 0.5F,
                    menuLevel.getRandom().nextFloat() * 0.1F + 0.9F);

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
     * 获取当前玩家的 UUID
     *
     * @return 玩家 UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * 获取关联的方块实体
     *
     * @return 普通售货箱方块实体
     */
    public ShippingBoxBlockEntity getBlockEntity() {
        return blockEntity;
    }
}