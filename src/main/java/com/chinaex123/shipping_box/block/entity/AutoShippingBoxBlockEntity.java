package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.init.ModBlockEntities;
import com.chinaex123.shipping_box.client.menu.AutoShippingBoxMenu;
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
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 自动售货箱的方块实体类。
 * <p>
 * 负责管理自动售货箱的物品存储、兑换逻辑和与玩家的交互界面。
 * 通过自定义物品处理器控制提取行为，确保只有已完成兑换且与原型匹配的物品
 * 才能被管道提取；同时维护兑换状态与物品原型记录，防止新放入的物品被误判为兑换产物。
 */
public class AutoShippingBoxBlockEntity extends BaseContainerBlockEntity implements MenuProvider {

    /** 54 槽内部物品存储（本地缓存） */
    private final NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);

    /** 绑定的玩家 UUID（只有该玩家可以操作此容器） */
    private UUID boundPlayerUUID;

    /** 上次兑换日期（用于判断是否到新的一天） */
    private long lastExchangeDay = -1L;

    /** 记录每个槽位是否已兑换完成 */
    private final Map<Integer, Boolean> slotIsExchanged = new HashMap<>();
    /** 记录每个已兑换槽位的物品原型（用于验证身份） */
    private final Map<Integer, ItemStack> exchangedItemPrototype = new HashMap<>();

    /** 兑换过程中跳过重置状态的标志 */
    private boolean skipResetDuringExchange = false;

    /**
     * 自定义物品处理器，用于控制物品的输入、输出和兑换状态。
     * <p>
     * 提取时通过 {@link #canExternalExtract} 校验是否为兑换产物，
     * 插入时仅在槽位原本无已兑换物品时才清除兑换标记。
     */
    private final ItemStackHandler itemHandler = new ItemStackHandler(54) {

        /**
         * 检查指定槽位是否允许放入物品。
         *
         * @param slot  槽位索引
         * @param stack 要放入的物品堆
         * @return 始终返回 true，表示所有槽位都允许放入任何物品
         */
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
        }

        /**
         * 从指定槽位提取物品。
         * <p>
         * 提取前校验该槽位是否允许外部提取（已兑换且与原型匹配）。
         *
         * @param slot     槽位索引
         * @param amount   要提取的物品数量
         * @param simulate 是否模拟提取
         * @return 提取到的物品堆，未通过校验时返回 ItemStack.EMPTY
         */
        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack current = getStackInSlot(slot);
            if (!canExternalExtract(slot, current)) {
                return ItemStack.EMPTY;
            }
            return super.extractItem(slot, amount, simulate);
        }

        /**
         * 向指定槽位插入物品。
         * <p>
         * 仅在槽位原本没有已兑换物品时清除兑换标记与原型记录，
         * 以保护正在被提取的兑换产物不被误判。
         *
         * @param slot     槽位索引
         * @param stack    要插入的物品堆
         * @param simulate 是否模拟插入
         * @return 未能插入的剩余物品堆
         */
        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            ItemStack result = super.insertItem(slot, stack, simulate);
            if (!simulate) {
                boolean hadExchanged = slotIsExchanged.getOrDefault(slot, false);
                if (!hadExchanged) {
                    slotIsExchanged.put(slot, false);
                    exchangedItemPrototype.remove(slot);
                }
            }
            return result;
        }

        /**
         * 当槽位内容发生变化时调用，标记方块实体需要保存数据。
         *
         * @param slot 发生变化的槽位索引
         */
        @Override
        protected void onContentsChanged(int slot) {
            AutoShippingBoxBlockEntity.this.setChanged();
        }

        /**
         * 直接在指定槽位设置物品堆。
         * <p>
         * 非兑换过程中时重置该槽位的兑换状态与原型记录。
         *
         * @param slot  槽位索引
         * @param stack 要设置的物品堆
         */
        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            super.setStackInSlot(slot, stack);
            if (!skipResetDuringExchange) {
                slotIsExchanged.put(slot, false);
                exchangedItemPrototype.remove(slot);
            }
        }
    };

    /**
     * 自动售货箱方块实体构造函数。
     *
     * @param pos   方块在游戏中的位置坐标
     * @param state 方块的当前状态信息
     */
    public AutoShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTOMATED_SHIPPING_BOX.get(), pos, state);
    }

    /**
     * 同步内部物品存储与物品处理器的状态。
     * <p>
     * 将 itemHandler 中的所有槽位物品复制到 items 列表中，保持两者数据一致。
     */
    private void syncItems() {
        for (int i = 0; i < 54; i++) {
            items.set(i, itemHandler.getStackInSlot(i));
        }
    }

    /**
     * 获取物品列表。
     * 同步物品处理器状态后返回内部物品存储列表。
     *
     * @return 物品列表（54 格）
     */
    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        syncItems();
        return items;
    }

    /**
     * 设置物品列表。
     * 将传入列表的前 54 项同步到内部存储与物品处理器中。
     *
     * @param items 新的物品列表
     */
    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        for (int i = 0; i < Math.min(54, items.size()); i++) {
            this.items.set(i, items.get(i));
            itemHandler.setStackInSlot(i, items.get(i));
        }
    }

    /**
     * 获取容器的默认显示名称。
     *
     * @return 本地化名称组件
     */
    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.shipping_box.auto_shipping_box");
    }

    /**
     * 创建容器菜单。
     *
     * @param id              菜单 ID
     * @param playerInventory 玩家物品栏
     * @return 自动售货箱菜单实例
     */
    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory playerInventory) {
        return new AutoShippingBoxMenu(id, playerInventory, this);
    }

    /**
     * 获取物品处理器（用于能力系统）。
     *
     * @return ItemStackHandler 实例
     */
    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    /**
     * 获取能力处理器（兼容能力系统）。
     *
     * @return IItemHandler 实例
     */
    public IItemHandler getCapabilityHandler() {
        return itemHandler;
    }

    /**
     * 获取绑定的玩家 UUID。
     *
     * @return 绑定的玩家 UUID，可能为 null
     */
    public UUID getBoundPlayerUUID() {
        return boundPlayerUUID;
    }

    /**
     * 判断指定槽位是否允许外部提取。
     * <p>
     * 需同时满足：槽位已标记为已兑换、当前物品非空、
     * 且与记录的兑换产物原型匹配。
     *
     * @param slot         槽位索引
     * @param currentStack 当前槽位物品
     * @return 允许外部提取返回 true
     */
    public boolean canExternalExtract(int slot, ItemStack currentStack) {
        if (!slotIsExchanged.getOrDefault(slot, false) || currentStack.isEmpty()) {
            return false;
        }
        ItemStack prototype = exchangedItemPrototype.get(slot);
        return prototype != null && ItemStack.isSameItemSameComponents(currentStack, prototype);
    }

    /**
     * 强制执行物品兑换。
     * 在服务端立即执行兑换操作并更新兑换日期。
     */
    public void forceExchange() {
        if (level != null && !level.isClientSide) {
            long currentDay = level.getDayTime() / 24000;
            performExchange(currentDay);
            lastExchangeDay = currentDay;
            setChanged();
        }
    }

    /**
     * 执行物品兑换的核心方法。
     * <p>
     * 工作流程：收集非空槽位物品 → 调用兑换管理器计算 →
     * 回写处理结果并更新兑换状态与原型记录 → 处理被完全消耗的槽位 → 保存数据。
     *
     * @param currentDay 当前的游戏日
     */
    private void performExchange(long currentDay) {
        // 收集所有非空槽位的物品及其索引
        List<Integer> slotsWithItems = new ArrayList<>();
        List<ItemStack> itemsToProcess = new ArrayList<>();

        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                slotsWithItems.add(i);
                itemsToProcess.add(stack.copy());
            }
        }

        if (itemsToProcess.isEmpty()) {
            return;
        }

        // 准备兑换数据结构
        NonNullList<ItemStack> processedItems = NonNullList.withSize(itemsToProcess.size(), ItemStack.EMPTY);
        for (int i = 0; i < itemsToProcess.size(); i++) {
            processedItems.set(i, itemsToProcess.get(i));
        }

        // 执行兑换逻辑，异常时记录警告日志
        try {
            ExchangeManager.performExchange(processedItems, level, worldPosition, boundPlayerUUID);
        } catch (Exception e) {
            ShippingBox.LOGGER.warn("[AutoShippingBoxBlockEntity.performExchange] 兑换物品时出错", e);
        }

        // 先清空相关槽位的兑换状态，后续按实际情况重新设置
        for (int slot : slotsWithItems) {
            slotIsExchanged.put(slot, false);
        }

        // 设置跳过重置标志，防止写入物品时意外清除兑换状态
        skipResetDuringExchange = true;

        // 将处理后的物品回写槽位，并判定是否发生实际变化
        for (int i = 0; i < slotsWithItems.size() && i < processedItems.size(); i++) {
            int slotIndex = slotsWithItems.get(i);
            ItemStack newStack = processedItems.get(i);

            itemHandler.setStackInSlot(slotIndex, newStack);
            items.set(slotIndex, newStack);

            ItemStack oldStack = itemsToProcess.get(i);
            if (!ItemStack.matches(oldStack, newStack) && !newStack.isEmpty()) {
                // 物品发生变化且非空，标记为已兑换并记录原型
                slotIsExchanged.put(slotIndex, true);
                exchangedItemPrototype.put(slotIndex, newStack.copy());
            } else {
                // 物品未变化或为空，清除兑换标记
                slotIsExchanged.put(slotIndex, false);
                exchangedItemPrototype.remove(slotIndex);
            }
        }

        // 处理被完全消耗的槽位
        for (int i = processedItems.size(); i < slotsWithItems.size(); i++) {
            int slotIndex = slotsWithItems.get(i);
            itemHandler.setStackInSlot(slotIndex, ItemStack.EMPTY);
            items.set(slotIndex, ItemStack.EMPTY);
            slotIsExchanged.put(slotIndex, false);
            exchangedItemPrototype.remove(slotIndex);
        }

        // 恢复标志、更新兑换日期并保存
        skipResetDuringExchange = false;
        lastExchangeDay = currentDay;
        setChanged();
    }

    /**
     * 游戏刻更新方法（服务端专用）。
     * <p>
     * 检测游戏日变化，跨越到第二天时自动触发兑换；
     * 首次加载时初始化兑换日期标记，避免刚放置就立即兑换。
     */
    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }
        long currentDay = level.getDayTime() / 24000;
        if (currentDay > lastExchangeDay && lastExchangeDay != -1L) {
            performExchange(currentDay);
        } else if (lastExchangeDay == -1L) {
            lastExchangeDay = currentDay;
        }
    }

    /**
     * 保存方块实体的额外数据。
     * <p>
     * 保存物品库存、上次兑换日期、各槽位兑换状态、
     * 已兑换物品原型以及绑定的玩家 UUID。
     *
     * @param tag        要写入的 NBT 标签
     * @param registries 注册表提供者，用于序列化物品
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存物品库存数据
        tag.put("Inventory", itemHandler.serializeNBT(registries));
        tag.putLong("LastExchangeDay", lastExchangeDay);

        // 保存各槽位的兑换状态
        CompoundTag exchangedTag = new CompoundTag();
        for (Map.Entry<Integer, Boolean> entry : slotIsExchanged.entrySet()) {
            exchangedTag.putBoolean(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("SlotExchanged", exchangedTag);

        // 保存已兑换槽位的物品原型，用于身份验证
        CompoundTag prototypeTag = new CompoundTag();
        for (Map.Entry<Integer, ItemStack> entry : exchangedItemPrototype.entrySet()) {
            ItemStack stack = entry.getValue();
            if (stack != null && !stack.isEmpty()) {
                prototypeTag.put(String.valueOf(entry.getKey()), stack.save(registries));
            }
        }
        tag.put("ExchangedItemPrototype", prototypeTag);

        // 如果绑定了玩家，保存玩家 UUID
        if (boundPlayerUUID != null) {
            tag.putString("BoundPlayer", boundPlayerUUID.toString());
        }
    }

    /**
     * 从 NBT 标签加载方块实体的数据。
     * <p>
     * 加载物品库存、上次兑换日期、槽位兑换状态、
     * 已兑换物品原型以及绑定的玩家 UUID。
     *
     * @param tag        包含数据的 NBT 标签
     * @param registries 注册表提供者，用于反序列化物品
     */
    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        // 加载物品库存数据
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        }

        lastExchangeDay = tag.contains("LastExchangeDay") ? tag.getLong("LastExchangeDay") : -1L;

        slotIsExchanged.clear();
        exchangedItemPrototype.clear();

        // 加载槽位兑换状态，忽略格式错误的键
        if (tag.contains("SlotExchanged")) {
            CompoundTag exchangedTag = tag.getCompound("SlotExchanged");
            for (String key : exchangedTag.getAllKeys()) {
                try {
                    slotIsExchanged.put(Integer.parseInt(key), exchangedTag.getBoolean(key));
                } catch (NumberFormatException ignored) {}
            }
        }

        // 加载已兑换槽位的物品原型，忽略格式错误的键
        if (tag.contains("ExchangedItemPrototype")) {
            CompoundTag prototypeTag = tag.getCompound("ExchangedItemPrototype");
            for (String key : prototypeTag.getAllKeys()) {
                try {
                    ItemStack stack = ItemStack.parseOptional(registries, prototypeTag.getCompound(key));
                    if (!stack.isEmpty()) {
                        exchangedItemPrototype.put(Integer.parseInt(key), stack);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 加载绑定的玩家 UUID
        String boundPlayer = tag.getString("BoundPlayer");
        boundPlayerUUID = boundPlayer.isEmpty() ? null : UUID.fromString(boundPlayer);

        // 同步内部存储并重建缺失的原型记录
        syncItems();
        rebuildMissingExchangePrototypes();
    }

    /**
     * 重建缺失的兑换物品原型记录。
     * <p>
     * 清理无效的兑换状态，并为已兑换但缺少原型记录的槽位补充当前物品副本，
     * 确保数据一致性。
     */
    private void rebuildMissingExchangePrototypes() {
        Iterator<Map.Entry<Integer, Boolean>> iterator = slotIsExchanged.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Boolean> entry = iterator.next();
            int slot = entry.getKey();

            // 槽位越界或未标记为已兑换，移除原型记录；越界时同时移除状态记录
            if (slot < 0 || slot >= itemHandler.getSlots() || !entry.getValue()) {
                exchangedItemPrototype.remove(slot);
                if (slot < 0 || slot >= itemHandler.getSlots()) {
                    iterator.remove();
                }
                continue;
            }

            // 获取当前槽位的物品
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                // 槽位为空，清除兑换状态和原型
                exchangedItemPrototype.remove(slot);
                iterator.remove();
            } else if (!exchangedItemPrototype.containsKey(slot)) {
                // 缺少原型记录，补充当前物品的副本
                exchangedItemPrototype.put(slot, stack.copy());
            }
        }
    }

    /**
     * 获取容器大小。
     *
     * @return 固定返回 54 格
     */
    @Override
    public int getContainerSize() {
        return 54;
    }

    /**
     * 检查容器是否为空。
     *
     * @return 所有槽位均为空返回 true
     */
    @Override
    public boolean isEmpty() {
        for (int i = 0; i < 54; i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取指定槽位的物品。
     *
     * @param slot 槽位编号
     * @return 该槽位的物品堆栈
     */
    @Override
    public @NotNull ItemStack getItem(int slot) {
        return itemHandler.getStackInSlot(slot);
    }

    /**
     * 从指定槽位移除指定数量的物品。
     * <p>
     * 玩家手动取出物品时不受兑换状态限制，物品被取完时重置兑换状态。
     *
     * @param slot  槽位编号
     * @param count 要移除的物品数量
     * @return 实际移除的物品堆栈（可能为空）
     */
    @Override
    public @NotNull ItemStack removeItem(int slot, int count) {
        ItemStack stack = itemHandler.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = stack.split(count);

        // 临时设置跳过标志，避免写入时误清除兑换状态
        boolean prev = skipResetDuringExchange;
        skipResetDuringExchange = true;
        itemHandler.setStackInSlot(slot, stack);
        skipResetDuringExchange = prev;

        items.set(slot, stack);

        // 如果物品被取完，重置兑换状态
        if (stack.isEmpty()) {
            slotIsExchanged.put(slot, false);
            exchangedItemPrototype.remove(slot);
        }

        return result;
    }

    /**
     * 从指定槽位移除所有物品且不更新。
     * <p>
     * 玩家手动取出物品时不受兑换状态限制，移除后重置兑换状态。
     *
     * @param slot 槽位编号
     * @return 被移除的物品堆栈（可能为空）
     */
    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = itemHandler.getStackInSlot(slot);

        // 临时设置跳过标志，避免写入时误清除兑换状态
        boolean prev = skipResetDuringExchange;
        skipResetDuringExchange = true;
        itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
        skipResetDuringExchange = prev;

        items.set(slot, ItemStack.EMPTY);
        slotIsExchanged.put(slot, false);
        exchangedItemPrototype.remove(slot);

        return stack;
    }

    /**
     * 设置指定槽位的物品。
     * 同时更新物品处理器与内部存储。
     *
     * @param slot  槽位编号
     * @param stack 要设置的物品堆栈
     */
    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        itemHandler.setStackInSlot(slot, stack);
        items.set(slot, stack);
    }

    /**
     * 检查指定槽位是否允许放入指定物品。
     *
     * @param index 槽位索引
     * @param stack 待检查的物品堆
     * @return 始终返回 true，允许放入任何物品
     */
    @Override
    public boolean canPlaceItem(int index, @NotNull ItemStack stack) {
        return true;
    }

    /**
     * 清空所有内容。
     * 清空物品存储与兑换状态记录。
     */
    @Override
    public void clearContent() {
        // 临时设置跳过标志，避免逐个清空时反复重置兑换状态
        boolean prev = skipResetDuringExchange;
        skipResetDuringExchange = true;
        for (int i = 0; i < 54; i++) {
            itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            items.set(i, ItemStack.EMPTY);
        }
        skipResetDuringExchange = prev;
        slotIsExchanged.clear();
        exchangedItemPrototype.clear();
    }

    /**
     * 检查指定槽位是否处于“已交换”状态。
     *
     * @param slot 槽位编号
     * @return 已标记为交换状态返回 true
     */
    public boolean isSlotExchanged(int slot) {
        return slotIsExchanged.getOrDefault(slot, false);
    }

    /**
     * 获取所有已标记为“已交换”的槽位编号集合。
     * <p>
     * 返回的是新建集合，对其修改不会影响内部记录。
     *
     * @return 已交换槽位编号集合
     */
    public Set<Integer> getExchangedSlots() {
        Set<Integer> exchanged = new HashSet<>();
        slotIsExchanged.forEach((slot, value) -> {
            if (value) {
                exchanged.add(slot);
            }
        });
        return exchanged;
    }

    /**
     * 将该交换容器绑定到指定玩家。
     * 绑定后只有该玩家可以操作此容器。
     *
     * @param playerUUID 要绑定的玩家 UUID，传入 null 表示解除绑定
     */
    public void bindPlayer(UUID playerUUID) {
        this.boundPlayerUUID = playerUUID;
        setChanged();
    }

    /**
     * 检查指定玩家是否有权限访问此交换容器。
     * <p>
     * 未绑定任何玩家时允许所有人访问，已绑定时仅允许绑定者本人访问。
     *
     * @param player 要检查权限的玩家对象
     * @return 允许访问返回 true
     */
    public boolean canPlayerAccess(Player player) {
        return boundPlayerUUID == null || boundPlayerUUID.equals(player.getUUID());
    }
}