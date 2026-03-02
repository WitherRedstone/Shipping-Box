package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.menu.AutoShippingBoxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AutoShippingBoxBlockEntity extends BlockEntity implements MenuProvider {

    private final ItemStackHandler itemHandler = new ItemStackHandler(54) {
        /**
         * 当容器内容发生变化时调用的回调方法
         * <p>
         * 此方法在物品栏中某个槽位的内容发生改变时被触发，
         * 用于标记方块实体的状态已发生变化，需要保存到磁盘。
         *
         * @param slot 发生变化的槽位索引
         */
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    // 能力提供者
    private final Lazy<IItemHandler> itemHandlerLazy = Lazy.of(() -> itemHandler);

    /** 绑定的玩家UUID */
    private UUID boundPlayerUUID;

    /** 上次兑换日期 */
    private long lastExchangeDay = -1L;

    /** 记录每个槽位是否包含兑换后的物品 */
    private final Map<Integer, Boolean> slotIsExchanged = new HashMap<>();

    public AutoShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTOMATED_SHIPPING_BOX.get(), pos, state);
    }

    public UUID getBoundPlayerUUID() {
        return boundPlayerUUID;
    }

    /**
     * 执行自动售货箱的物品兑换逻辑
     * <p>
     * 该方法负责收集自动售货箱中的物品，调用兑换管理器进行处理，
     * 并将处理结果写回物品存储系统。
     *
     * @param currentDay 当前游戏日，用于记录上次兑换时间
     */
    private void performExchange(long currentDay) {
        // 收集所有非空槽位的物品
        List<ItemStack> itemsToProcess = new ArrayList<>();
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                itemsToProcess.add(stack.copy());
            }
        }

        if (itemsToProcess.isEmpty()) {
            return;
        }

        // 执行兑换
        NonNullList<ItemStack> processedItems = NonNullList.withSize(54, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(itemsToProcess.size(), processedItems.size()); i++) {
            processedItems.set(i, itemsToProcess.get(i));
        }

        try {
            ExchangeManager.performExchange(processedItems, level, worldPosition, boundPlayerUUID);
        } catch (Exception e) {
            // 静默处理异常
        }

        // 关键：将处理结果写回ItemStackHandler
        for (int i = 0; i < processedItems.size() && i < itemHandler.getSlots(); i++) {
            ItemStack oldStack = itemHandler.getStackInSlot(i);
            ItemStack newStack = processedItems.get(i);

            if (!ItemStack.matches(oldStack, newStack)) {
                itemHandler.setStackInSlot(i, newStack);
            }
        }

        // 清空剩余槽位
        for (int i = processedItems.size(); i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        // 标记所有槽位为已兑换状态
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                slotIsExchanged.put(i, true);
            }
        }

        lastExchangeDay = currentDay;
        setChanged();
    }

    /**
     * 将自动售货箱方块实体的额外数据保存到NBT标签中
     * <p>
     * 此方法负责序列化自动售货箱的所有持久化数据，
     * 包括物品库存、兑换状态、时间记录和玩家绑定信息。
     *
     * @param tag NBT复合标签，用于存储序列化数据
     * @param registries 注册表提供者，用于物品序列化
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存ItemStackHandler的数据
        tag.put("Inventory", itemHandler.serializeNBT(registries));

        // 保存上次兑换日期
        tag.putLong("LastExchangeDay", lastExchangeDay);

        // 保存各槽位的兑换状态标记
        CompoundTag exchangedTag = new CompoundTag();
        for (Map.Entry<Integer, Boolean> entry : slotIsExchanged.entrySet()) {
            exchangedTag.putBoolean(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("SlotExchanged", exchangedTag);

        // 保存绑定玩家的UUID（如果存在）
        if (boundPlayerUUID != null) {
            tag.putString("BoundPlayer", boundPlayerUUID.toString());
        }
    }

    /**
     * 从NBT标签中加载自动售货箱方块实体的额外数据
     * <p>
     * 此方法负责反序列化自动售货箱的所有持久化数据，
     * 包括物品库存、兑换状态、时间记录和玩家绑定信息。
     *
     * @param tag 包含序列化数据的NBT复合标签
     * @param registries 注册表提供者，用于物品反序列化
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        // 加载ItemStackHandler的数据
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        }

        // 加载上次兑换日期
        if (tag.contains("LastExchangeDay")) {
            lastExchangeDay = tag.getLong("LastExchangeDay");
        }

        // 加载各槽位的兑换状态标记
        if (tag.contains("SlotExchanged")) {
            CompoundTag exchangedTag = tag.getCompound("SlotExchanged");
            for (String key : exchangedTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    boolean isExchanged = exchangedTag.getBoolean(key);
                    slotIsExchanged.put(slot, isExchanged);
                } catch (NumberFormatException e) {
                    // 静默处理错误
                }
            }
        }

        // 加载绑定玩家的UUID
        if (tag.contains("BoundPlayer")) {
            try {
                boundPlayerUUID = UUID.fromString(tag.getString("BoundPlayer"));
            } catch (IllegalArgumentException e) {
                boundPlayerUUID = null;
            }
        }
    }

    /**
     * 清空自动售货箱的所有内容
     * <p>
     * 此方法将清空物品存储处理器中的所有槽位，
     * 重置所有槽位的兑换状态标记，并标记方块实体已更改。
     */
    public void clearContent() {
        // 清空所有物品槽位
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            itemHandler.setStackInSlot(i, ItemStack.EMPTY);
        }

        // 清空兑换状态记录
        slotIsExchanged.clear();

        // 标记方块实体已更改
        setChanged();
    }

    /**
     * 获取自动售货箱的容器大小
     */
    public int getContainerSize() {
        return itemHandler.getSlots();
    }

    /**
     * 检查自动售货箱是否为空
     */
    public boolean isEmpty() {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 绑定玩家到自动售货箱
     */
    public void bindPlayer(UUID playerUUID) {
        this.boundPlayerUUID = playerUUID;
        setChanged();
    }

    /**
     * 检查玩家是否可以访问自动售货箱
     */
    public boolean canPlayerAccess(Player player) {
        return boundPlayerUUID == null || player.getUUID().equals(boundPlayerUUID);
    }

    /**
     * 自动售货箱的周期性更新方法
     */
    public void tick() {
        if (level == null || level.isClientSide) return;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;

        // 检查容器中是否存在物品
        boolean hasItems = false;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }

        if (!hasItems) {
            return;
        }

        // 检测兑换时间窗口（每日0-180游戏刻）
        if (timeOfDay >= 0 && timeOfDay <= 180) {
            // 检查是否距离上次兑换已经过去了一天
            long timeSinceLastExchange = dayTime - (lastExchangeDay * 24000);

            // 如果距离上次兑换超过一天，或者这是第一次兑换
            if (timeSinceLastExchange >= 24000 || lastExchangeDay == -1L) {
                try {
                    performExchange(dayTime / 24000);
                    lastExchangeDay = dayTime / 24000;
                    setChanged();
                } catch (Exception e) {
                    // 异常处理保持简洁
                }
            }
            // 处理时间重置的特殊情况
            else if (timeSinceLastExchange < 0) {
                // 时间被重置到过去，强制进行兑换
                try {
                    performExchange(dayTime / 24000);
                    lastExchangeDay = dayTime / 24000;
                    setChanged();
                } catch (Exception e) {
                    // 异常处理保持简洁
                }
            }
        }
    }

    /**
     * 获取自动售货箱方块的显示名称
     * <p>
     * 此方法返回自动售货箱方块在游戏界面中显示的本地化名称。
     *
     * @return 自动售货箱的本地化显示名称组件
     */
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.shipping_box.auto_shipping_box");
    }

    /**
     * 创建自动售货箱的容器菜单
     * <p>
     * 此方法负责为自动售货箱创建对应的GUI菜单实例，
     * 供玩家与方块实体进行交互。
     *
     * @param id 容器ID，用于网络同步
     * @param inventory 玩家物品栏
     * @param player 操作菜单的玩家实体
     * @return 新创建的自动售货箱菜单实例
     */
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AutoShippingBoxMenu(id, inventory, this);
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    // 提供公共访问方法供外部使用
    public IItemHandler getCapabilityHandler() {
        return itemHandlerLazy.get();
    }

    /**
     * 从方块实体获取物品处理能力
     * <p>
     * 此静态工具方法用于安全地将通用方块实体转换为自动售货箱实体，
     * 并返回其物品处理能力接口，便于外部系统访问物品存储功能。
     *
     * @param blockEntity 要转换的方块实体
     * @return 自动售货箱的物品处理能力接口，如果不是自动售货箱则返回null
     */
    public static IItemHandler getItemHandlerFromBlockEntity(BlockEntity blockEntity) {
        if (blockEntity instanceof AutoShippingBoxBlockEntity autoBox) {
            return autoBox.getCapabilityHandler();
        }
        return null;
    }
}
