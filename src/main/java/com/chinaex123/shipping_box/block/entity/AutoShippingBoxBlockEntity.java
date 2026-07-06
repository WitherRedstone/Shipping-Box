package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.init.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 自动售货箱的方块实体类；
 * 负责管理自动售货箱的物品存储、兑换逻辑和与玩家的交互界面
 */
public class AutoShippingBoxBlockEntity extends BaseContainerBlockEntity implements MenuProvider {

    /** 物品存储列表（本地缓存） */
    private NonNullList<ItemStack> items;

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
     * 自定义物品处理器，用于控制物品的输入、输出和兑换状态
     * 核心功能：
     * 1. 只允许提取已完成兑换的物品
     * 2. 通过物品原型验证来区分兑换产物和新放入的物品
     * 3. 智能管理兑换状态标记，防止误判
     */
    private final ItemStackHandler itemHandler = new ItemStackHandler(54) {
        /**
         * 检查指定槽位是否允许放入物品
         * @param slot 槽位索引
         * @param stack 要放入的物品堆
         * @return 始终返回 true，表示所有槽位都允许放入任何物品
         */
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
        }

        /**
         * 从指定槽位提取物品
         * 提取条件（必须同时满足）：
         * 1. 槽位的兑换状态为 true（已兑换）
         * 2. 当前物品与兑换时记录的原型物品匹配（排除新放入的物品）
         * 
         * @param slot 槽位索引
         * @param amount 要提取的物品数量
         * @param simulate 是否模拟提取（true=不实际提取，false=实际提取）
         * @return 提取到的物品堆，如果无法提取则返回 ItemStack.EMPTY
         */
        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            // 检查槽位是否标记为已兑换状态
            boolean isExchanged = slotIsExchanged.getOrDefault(slot, false);
            
            if (!isExchanged) {
                return ItemStack.EMPTY; // 槽位未兑换，拒绝提取
            }
            
            // 获取当前槽位的物品和兑换时记录的原型物品
            ItemStack currentStack = getStackInSlot(slot);
            ItemStack prototype = exchangedItemPrototype.get(slot);
            
            if (prototype != null && !currentStack.isEmpty()) {
                // 验证当前物品是否与兑换产物原型匹配（类型和组件相同，不比较数量）
                if (!ItemStack.isSameItemSameComponents(currentStack, prototype)) {
                    // 物品不匹配，说明是新放入的物品而非兑换产物，拒绝提取
                    return ItemStack.EMPTY;
                }
            } else if (prototype == null) {
                // 没有原型记录，无法验证物品身份，拒绝提取
                return ItemStack.EMPTY;
            }
            
            // 所有验证通过，允许提取
            return super.extractItem(slot, amount, simulate);
        }

        /**
         * 向指定槽位插入物品
         * 插入逻辑：
         * - 如果槽位原本没有已兑换物品：清除兑换标记和原型记录（新物品放入）
         * - 如果槽位原本有已兑换物品：保持标记不变（保护兑换产物不被误判）
         * 
         * @param slot 槽位索引
         * @param stack 要插入的物品堆
         * @param simulate 是否模拟插入（true=不实际插入，false=实际插入）
         * @return 未能插入的剩余物品堆
         */
        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // 执行实际的插入操作
            ItemStack result = super.insertItem(slot, stack, simulate);
            
            // 当实际插入物品时（非模拟状态）
            if (!simulate) {
                // 检查插入前槽位是否已有已兑换的物品
                boolean hadExchangedItem = slotIsExchanged.getOrDefault(slot, false);
                
                // 只有当槽位原本没有已兑换物品时，才清除兑换标记和原型记录
                // 这样可以保护正在被提取的兑换产物不被误认为是新物品
                if (!hadExchangedItem) {
                    slotIsExchanged.put(slot, false);
                    exchangedItemPrototype.remove(slot);
                }
                // 如果原本有已兑换物品，保持状态不变，等待管道提取
            }
            
            return result;
        }

        /**
         * 当槽位内容发生变化时调用
         * 用于标记方块实体需要保存数据
         * 
         * @param slot 发生变化的槽位索引
         */
        @Override
        protected void onContentsChanged(int slot) {
            AutoShippingBoxBlockEntity.this.setChanged();
        }

        /**
         * 直接在指定槽位设置物品堆
         * 
         * @param slot 槽位索引
         * @param stack 要设置的物品堆
         */
        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            // 调用父类方法设置物品
            super.setStackInSlot(slot, stack);
            
            // 只有在不是兑换过程中时才重置兑换状态
            // 在兑换过程中设置物品时，需要保持兑换状态由 performExchange 方法控制
            if (!skipResetDuringExchange) {
                slotIsExchanged.put(slot, false);
            }
        }
    };

    /**
     * 自动售货箱方块实体构造函数
     * 初始化物品存储、兑换状态和同步机制
     *
     * @param pos 方块在游戏中的位置坐标
     * @param state 方块的当前状态信息
     */
    public AutoShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTOMATED_SHIPPING_BOX.get(), pos, state);
        this.items = NonNullList.withSize(54, ItemStack.EMPTY);
        syncItems();
    }

    /**
     * 同步内部物品存储与物品处理器的状态
     * <p>
     * 将 itemHandler 中的所有槽位物品复制到 items 列表中，保持两者数据一致
     */
    private void syncItems() {
        for (int i = 0; i < 54; i++) {
            items.set(i, itemHandler.getStackInSlot(i));
        }
    }

    /**
     * 获取物品列表
     * 同步物品处理器状态后返回内部物品存储列表
     *
     * @return 物品列表（54格）
     */
    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        syncItems();
        return items;
    }

    /**
     * 设置物品列表
     * 将传入的物品列表同步到内部存储和物品处理器中
     *
     * @param items 新的物品列表
     */
    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        this.items = items;
        for (int i = 0; i < 54 && i < items.size(); i++) {
            itemHandler.setStackInSlot(i, items.get(i));
        }
    }

    /**
     * 获取容器的默认显示名称
     *
     * @return 本地化名称组件
     */
    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.shipping_box.auto_shipping_box");
    }

    /**
     * 创建容器菜单
     * 为玩家打开自动售货箱界面时创建对应的菜单实例
     *
     * @param id 菜单ID
     * @param playerInventory  玩家物品栏
     * @return 自动售货箱菜单实例
     */
    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory playerInventory) {
        return new AutoShippingBoxMenu(id, playerInventory, this);
    }

    /**
     * 获取物品处理器（用于能力系统）
     *
     * @return ItemStackHandler 实例
     */
    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    /**
     * 获取能力处理器（兼容 Forge 能力系统）
     *
     * @return IItemHandler 实例
     */
    public IItemHandler getCapabilityHandler() {
        return itemHandler;
    }

    /**
     * 获取绑定的玩家 UUID
     *
     * @return 绑定的玩家 UUID，可能为 null
     */
    public UUID getBoundPlayerUUID() {
        return boundPlayerUUID;
    }

    /**
     * 强制执行物品兑换
     * 在服务端立即执行兑换操作并更新兑换日期
     */
    public void forceExchange() {
        if (level != null && !level.isClientSide) {
            performExchange(level.getDayTime() / 24000);
            lastExchangeDay = level.getDayTime() / 24000;
            setChanged(); // 标记数据已变更
        }
    }

    /**
     * 执行物品兑换的核心方法
     * <p>
     * 工作流程：
     * 1. 收集所有非空槽位的物品
     * 2. 调用 ExchangeManager 进行兑换计算
     * 3. 更新兑换状态和物品原型记录
     * 4. 标记已兑换的槽位供管道提取
     * 
     * @param currentDay 当前的游戏日（用于判断是否到了兑换时间）
     */
    private void performExchange(long currentDay) {
        // ========== 第一阶段：收集待处理的物品 ==========
        // 记录哪些槽位有物品以及它们的原始索引
        List<Integer> slotsWithItems = new ArrayList<>();
        List<ItemStack> itemsToProcess = new ArrayList<>();
        
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                slotsWithItems.add(i);
                // 复制物品堆，避免后续操作影响原始数据
                itemsToProcess.add(stack.copy());
            }
        }

        // 如果没有物品需要处理，直接返回
        if (itemsToProcess.isEmpty()) {
            return;
        }

        // ========== 第二阶段：准备兑换数据结构 ==========
        // 创建已处理物品列表（初始为空）
        NonNullList<ItemStack> processedItems = NonNullList.withSize(itemsToProcess.size(), ItemStack.EMPTY);
        for (int i = 0; i < itemsToProcess.size(); i++) {
            processedItems.set(i, itemsToProcess.get(i));
        }

        // ========== 第三阶段：执行兑换逻辑 ==========
        try {
            // 调用兑换管理器进行实际兑换计算
            // 此方法会修改 processedItems 中的物品
            ExchangeManager.performExchange(processedItems, level, worldPosition, boundPlayerUUID);
        } catch (Exception e) {
            // 静默处理异常，防止兑换错误导致崩溃
        }

        // ========== 第四阶段：更新兑换轮次和状态 ==========
        // 先清空所有相关槽位的兑换状态（后续会根据实际情况重新设置）
        for (int slot : slotsWithItems) {
            slotIsExchanged.put(slot, false);
        }

        // 设置跳过重置标志，防止 setStackInSlot 意外清除兑换状态
        skipResetDuringExchange = true;
        
        // ========== 第五阶段：将处理后的物品放回槽位 ==========
        for (int i = 0; i < slotsWithItems.size() && i < processedItems.size(); i++) {
            int slotIndex = slotsWithItems.get(i);
            ItemStack newStack = processedItems.get(i);
            
            // 安全地设置物品到槽位（不会重置兑换状态）
            itemHandler.setStackInSlot(slotIndex, newStack);
            
            // 判断物品是否发生了实际变化
            ItemStack oldStack = itemsToProcess.get(i);
            if (!ItemStack.matches(oldStack, newStack) && !newStack.isEmpty()) {
                // 物品发生变化且不为空，标记为已兑换
                slotIsExchanged.put(slotIndex, true);
                // 记录兑换产物的原型，用于后续验证物品身份
                exchangedItemPrototype.put(slotIndex, newStack.copy());
            } else {
                // 物品未变化或为空，清除兑换标记
                slotIsExchanged.put(slotIndex, false);
                exchangedItemPrototype.remove(slotIndex);
            }
        }

        // ========== 第六阶段：处理被完全消耗的槽位 ==========
        // 当 processedItems 比原始物品列表短时，说明有些物品被完全消耗了
        for (int i = processedItems.size(); i < slotsWithItems.size(); i++) {
            int slotIndex = slotsWithItems.get(i);
            // 清空这些槽位
            itemHandler.setStackInSlot(slotIndex, ItemStack.EMPTY);
            slotIsExchanged.put(slotIndex, false);
            exchangedItemPrototype.remove(slotIndex);
        }

        // ========== 第七阶段：恢复状态并保存数据 ==========
        // 恢复跳过重置标志，允许正常重置兑换状态
        skipResetDuringExchange = false;

        // 更新上次兑换日期
        lastExchangeDay = currentDay;
        
        // 标记方块实体已更改，需要保存到磁盘
        setChanged();
        
        // 同步内部存储与物品处理器
        syncItems();
    }

    /**
     * 游戏刻更新方法（服务端专用）
     * <p>
     * 主要功能：
     * 1. 检测游戏时间的变化（以天为单位）
     * 2. 当跨越到第二天时自动触发兑换
     * 3. 初始化首次加载时的日期标记
     * <p>
     * 调用时机：每个游戏刻由方块实体的更新逻辑调用
     * 运行侧：仅在服务端执行，客户端不处理兑换逻辑
     */
    public void tick() {
        // 确保只在服务端执行
        if (level != null && !level.isClientSide) {
            // 计算当前是第几天（Minecraft 中一天 = 24000 tick）
            long currentDay = level.getDayTime() / 24000;
            
            // 如果当前日期晚于上次兑换日期且已初始化过，则触发兑换
            if (currentDay > lastExchangeDay && lastExchangeDay != -1L) {
                // 执行每日自动兑换
                performExchange(currentDay);
            } else if (lastExchangeDay == -1L) {
                // 首次加载或刚放置时，初始化兑换日期标记
                // 避免刚放置就立即触发兑换
                lastExchangeDay = currentDay;
            }
        }
    }

    /**
     * 保存方块实体的额外数据到 NBT 标签
     * <p>
     * 保存的数据包括：
     * - 物品库存（54 个槽位的物品）
     * - 上次兑换日期
     * - 每个槽位的兑换状态
     * - 绑定的玩家 UUID（如果有）
     * 
     * @param tag 要写入的 NBT 标签
     * @param registries 注册表提供者，用于序列化物品
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        // 调用父类保存基础数据
        super.saveAdditional(tag, registries);
        
        // 保存物品库存数据
        tag.put("Inventory", itemHandler.serializeNBT(registries));
        
        // 保存上次兑换的日期标记
        tag.putLong("LastExchangeDay", lastExchangeDay);
        
        // 保存每个槽位的兑换状态（哪些槽位已兑换完成）
        CompoundTag exchangedTag = new CompoundTag();
        for (Map.Entry<Integer, Boolean> entry : slotIsExchanged.entrySet()) {
            exchangedTag.putBoolean(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("SlotExchanged", exchangedTag);

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
     * 从 NBT 标签加载方块实体的数据
     * <p>
     * 加载的数据包括：
     * - 物品库存
     * - 上次兑换日期
     * - 槽位兑换状态
     * - 绑定的玩家 UUID
     * 
     * @param tag 包含数据的 NBT 标签
     * @param registries 注册表提供者，用于反序列化物品
     */
    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        // 调用父类加载基础数据
        super.loadAdditional(tag, registries);
        
        // 加载物品库存数据
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        
        // 加载上次兑换日期
        if (tag.contains("LastExchangeDay")) {
            lastExchangeDay = tag.getLong("LastExchangeDay");
        }
        
        // 加载槽位兑换状态
        slotIsExchanged.clear();
        exchangedItemPrototype.clear();

        if (tag.contains("SlotExchanged")) {
            CompoundTag exchangedTag = tag.getCompound("SlotExchanged");
            for (String key : exchangedTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    boolean isExchanged = exchangedTag.getBoolean(key);
                    slotIsExchanged.put(slot, isExchanged);
                } catch (NumberFormatException e) {
                    // 忽略格式错误的键值，继续处理其他数据
                }
            }
        }
        
        // 加载绑定的玩家 UUID
        if (tag.contains("ExchangedItemPrototype")) {
            CompoundTag prototypeTag = tag.getCompound("ExchangedItemPrototype");
            for (String key : prototypeTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack stack = ItemStack.parseOptional(registries, prototypeTag.getCompound(key));
                    if (!stack.isEmpty()) {
                        exchangedItemPrototype.put(slot, stack);
                    }
                } catch (NumberFormatException e) {
                    // Ignore malformed slot keys.
                }
            }
        }

        if (tag.contains("BoundPlayer")) {
            boundPlayerUUID = UUID.fromString(tag.getString("BoundPlayer"));
        }
        
        // 同步内部存储与物品处理器
        syncItems();
        rebuildMissingExchangePrototypes();
    }

    /**
     * 重建缺失的兑换物品原型记录
     * 清理无效的兑换状态并为已兑换槽位补充原型数据，确保数据一致性
     */
    private void rebuildMissingExchangePrototypes() {
        for (Iterator<Map.Entry<Integer, Boolean>> iterator = slotIsExchanged.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Integer, Boolean> entry = iterator.next();
            int slot = entry.getKey();

            // 检查槽位是否有效且标记为已兑换
            if (!entry.getValue() || slot < 0 || slot >= itemHandler.getSlots()) {
                exchangedItemPrototype.remove(slot);
                if (entry.getValue()) {
                    iterator.remove(); // 移除无效的兑换状态
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
     * 获取容器大小
     *
     * @return 固定返回 54 格
     */
    @Override
    public int getContainerSize() {
        return 54;
    }

    /**
     * 检查容器是否为空
     *
     * @return 如果所有槽位都为空则返回 true，否则返回 false
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
     * 获取指定槽位的物品
     *
     * @param slot 槽位编号
     * @return 该槽位的物品堆栈
     */
    @Override
    public @NotNull ItemStack getItem(int slot) {
        return itemHandler.getStackInSlot(slot);
    }

    /**
     * 从指定槽位移除指定数量的物品
     * 玩家手动取出物品时不受兑换状态限制，物品被取完时重置兑换状态
     *
     * @param slot  槽位编号
     * @param count 要移除的物品数量
     * @return 实际移除的物品堆栈（可能为空）
     */
    @Override
    public @NotNull ItemStack removeItem(int slot, int count) {
        // 玩家手动取出物品时，不受兑换状态限制
        ItemStack stack = itemHandler.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 从物品堆中分离出指定数量
        ItemStack result = stack.split(count);
        // 更新实际物品栏
        itemHandler.setStackInSlot(slot, stack);
        // 同步更新本地缓存
        items.set(slot, stack);

        // 如果物品被取完，重置兑换状态
        if (stack.isEmpty()) {
            slotIsExchanged.put(slot, false);
            exchangedItemPrototype.remove(slot);
        }

        return result;
    }

    /**
     * 从指定槽位移除所有物品且不更新
     * 玩家手动取出物品时不受兑换状态限制，移除后重置兑换状态
     *
     * @param slot 槽位编号
     * @return 被移除的物品堆栈（可能为空）
     */
    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        // 玩家手动取出物品时，不受兑换状态限制
        ItemStack stack = itemHandler.getStackInSlot(slot);
        itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
        items.set(slot, ItemStack.EMPTY);
        
        // 重置兑换状态
        slotIsExchanged.put(slot, false);
        exchangedItemPrototype.remove(slot);
        
        return stack;
    }

    /**
     * 设置指定槽位的物品
     * 同时更新物品处理器和内部存储，并重置该槽位的兑换状态
     *
     * @param slot  要设置物品的槽位编号
     * @param stack 要放入的物品堆栈（可以为空，表示清空该槽位）
     * @throws IllegalArgumentException 如果 slot 无效（由 itemHandler 或 items 抛出）
     */
    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        itemHandler.setStackInSlot(slot, stack);
        items.set(slot, stack);
        // 当物品被放入时，重置兑换状态
        slotIsExchanged.put(slot, false);
        exchangedItemPrototype.remove(slot);
    }

    @Override
    public boolean canPlaceItem(int index, @NotNull ItemStack stack) {
        return true;
    }

    /**
     * 清空所有内容
     * 清空所有槽位的物品和兑换状态记录
     */
    @Override
    public void clearContent() {
        for (int i = 0; i < 54; i++) {
            itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            items.set(i, ItemStack.EMPTY);
        }
        slotIsExchanged.clear();
        exchangedItemPrototype.clear();
    }

    /**
     * 检查指定槽位是否处于“已交换”状态。
     *
     * @param slot 槽位编号
     * @return true 表示该槽位已标记为交换状态，false 表示未标记（或槽位不存在于记录中）
     */
    public boolean isSlotExchanged(int slot) {
        return slotIsExchanged.getOrDefault(slot, false);
    }

    /**
     * 获取所有已标记为“已交换”的槽位编号集合。
     * 返回的是 Map 的键集视图，对返回集合的修改会直接影响原 Map。
     *
     * @return 所有已交换槽位的编号集合（不可保证顺序）
     */
    public Set<Integer> getExchangedSlots() {
        return slotIsExchanged.keySet();
    }

    /**
     * 将该交换容器绑定到指定玩家。
     * 绑定后，只有该玩家可以操作此容器（如打开界面、进行交换等）。
     *
     * @param playerUUID 要绑定的玩家 UUID，传入 null 表示解除绑定（允许任何人访问）
     */
    public void bindPlayer(UUID playerUUID) {
        this.boundPlayerUUID = playerUUID;
        setChanged();
    }

    /**
     * 检查指定玩家是否有权限访问此交换容器。
     * <p>
     * 访问规则：
     * 1. 如果未绑定任何玩家（boundPlayerUUID == null），则所有玩家均可访问
     * 2. 如果已绑定玩家，则只有绑定者本人可以访问
     *
     * @param player 要检查权限的玩家对象
     * @return true 表示允许访问，false 表示拒绝访问
     */
    public boolean canPlayerAccess(Player player) {
        if (boundPlayerUUID == null) {
            return true; // 未绑定：允许任何人访问
        }
        return boundPlayerUUID.equals(player.getUUID()); // 已绑定，仅允许绑定者访问
    }
}