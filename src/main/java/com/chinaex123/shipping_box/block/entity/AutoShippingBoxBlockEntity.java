package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.init.ModBlockEntities;
import com.chinaex123.shipping_box.client.menu.AutoShippingBoxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 自动售货箱的方块实体类。
 * <p>
 * 26.2 里物品存储继续用内部 NonNullList&lt;ItemStack&gt;（保持旧模式），
 * 能力（Capability）层用包装后的 ResourceHandler 暴露为新的 transfer API。
 * 通过 {@link AutoShippingBoxResourceHandler} 控制外部提取行为，
 * 仅允许提取已完成兑换且与原型匹配的物品。
 */
public class AutoShippingBoxBlockEntity extends BaseContainerBlockEntity implements MenuProvider {

    /** 54 槽内部物品存储 */
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
    /** 对外暴露的 transfer 资源处理器（延迟创建） */
    private ResourceHandler<ItemResource> transferHandler;

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
     * 获取物品列表。
     *
     * @return 内部物品存储列表（54 格）
     */
    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    /**
     * 设置物品列表。
     * 将传入列表的前 54 项逐一复制到内部存储。
     *
     * @param items 新的物品列表
     */
    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        for (int i = 0; i < Math.min(54, items.size()); i++) this.items.set(i, items.get(i));
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
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * 获取指定槽位的物品。
     *
     * @param slot 槽位编号
     * @return 该槽位的物品堆栈
     */
    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    /**
     * 从指定槽位移除指定数量的物品。
     * 玩家手动取出物品时不受兑换状态限制。
     *
     * @param slot  槽位编号
     * @param count 要移除的数量
     * @return 实际移除的物品堆栈（可能为空）
     */
    @Override
    public @NotNull ItemStack removeItem(int slot, int count) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.split(count);
        if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
        setChanged();
        return result;
    }

    /**
     * 从指定槽位移除所有物品且不更新。
     *
     * @param slot 槽位编号
     * @return 被移除的物品堆栈（可能为空）
     */
    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    /**
     * 设置指定槽位的物品。
     *
     * @param slot  槽位编号
     * @param stack 要设置的物品堆栈
     */
    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        setItem(slot, stack, false);
    }

    /**
     * 设置指定槽位的物品（支持事务语义）。
     * <p>
     * 非事务且非兑换过程中时，重置该槽位的兑换状态与原型记录；
     * 非事务时标记方块实体已变更。
     *
     * @param slot              槽位编号
     * @param stack             要设置的物品堆栈
     * @param insideTransaction 是否处于事务中
     */
    @Override
    public void setItem(int slot, @NotNull ItemStack stack, boolean insideTransaction) {
        items.set(slot, stack);
        if (!insideTransaction && !skipResetDuringExchange) {
            slotIsExchanged.put(slot, false);
            exchangedItemPrototype.remove(slot);
        }
        if (!insideTransaction) {
            setChanged();
        }
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
        items.clear();
        slotIsExchanged.clear();
        exchangedItemPrototype.clear();
    }

    /**
     * 获取绑定的玩家 UUID。
     *
     * @return 绑定的玩家 UUID，可能为 null
     */
    public UUID getBoundPlayerUUID() { return boundPlayerUUID; }

    /**
     * 将该交换容器绑定到指定玩家。
     *
     * @param playerUUID 要绑定的玩家 UUID，传入 null 表示解除绑定
     */
    public void bindPlayer(UUID playerUUID) { this.boundPlayerUUID = playerUUID; setChanged(); }

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

    /**
     * 检查指定槽位是否处于“已交换”状态。
     *
     * @param slot 槽位编号
     * @return 已标记为交换状态返回 true
     */
    public boolean isSlotExchanged(int slot) { return slotIsExchanged.getOrDefault(slot, false); }

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
            if (value) exchanged.add(slot);
        });
        return exchanged;
    }

    /**
     * 判断指定槽位是否允许外部提取。
     * <p>
     * 需同时满足：槽位已标记为已兑换、当前物品非空、
     * 且与记录的兑换产物原型匹配。
     *
     * @param slot         槽位编号
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
     * 获取对外暴露的 transfer 资源处理器。
     * <p>
     * 首次调用时延迟创建并缓存实例。
     *
     * @return transfer 资源处理器
     */
    public ResourceHandler<ItemResource> getTransferHandler() {
        if (transferHandler == null) {
            transferHandler = new AutoShippingBoxResourceHandler(this);
        }
        return transferHandler;
    }

    /**
     * 强制执行物品兑换。
     * 在服务端立即执行兑换操作并更新兑换日期。
     */
    public void forceExchange() {
        if (level != null && !level.isClientSide()) {
            performExchange(level.getLevelData().getGameTime() / 24000);
            lastExchangeDay = level.getLevelData().getGameTime() / 24000;
            setChanged();
        }
    }

    /**
     * 游戏刻更新方法（服务端专用）。
     * <p>
     * 检测游戏日变化，跨越到第二天时自动触发兑换；
     * 首次加载时初始化兑换日期标记，避免刚放置就立即兑换。
     */
    public void tick() {
        if (level == null || level.isClientSide()) return;
        long currentDay = level.getLevelData().getGameTime() / 24000;
        if (currentDay > lastExchangeDay && lastExchangeDay != -1L) {
            performExchange(currentDay);
        } else if (lastExchangeDay == -1L) {
            lastExchangeDay = currentDay;
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
        for (int i = 0; i < 54; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                slotsWithItems.add(i);
                itemsToProcess.add(stack.copy());
            }
        }
        if (itemsToProcess.isEmpty()) return;

        // 准备兑换数据结构
        NonNullList<ItemStack> processedItems = NonNullList.withSize(itemsToProcess.size(), ItemStack.EMPTY);
        for (int i = 0; i < itemsToProcess.size(); i++) processedItems.set(i, itemsToProcess.get(i));

        // 执行兑换逻辑，异常时静默处理避免崩溃
        try {
            ExchangeManager.performExchange(processedItems, level, worldPosition, boundPlayerUUID);
        } catch (Exception e) {
            ShippingBox.LOGGER.warn("[AutoShippingBoxBlockEntity.performExchange] 兑换物品时出错", e);
        }

        // 先清空相关槽位的兑换状态，后续按实际情况重新设置
        for (int slot : slotsWithItems) slotIsExchanged.put(slot, false);
        // 设置跳过重置标志，防止写入物品时意外清除兑换状态
        skipResetDuringExchange = true;

        // 将处理后的物品回写槽位，并判定是否发生实际变化
        for (int i = 0; i < slotsWithItems.size() && i < processedItems.size(); i++) {
            int si = slotsWithItems.get(i);
            ItemStack newStack = processedItems.get(i);
            items.set(si, newStack);
            ItemStack oldStack = itemsToProcess.get(i);
            if (!ItemStack.matches(oldStack, newStack) && !newStack.isEmpty()) {
                // 物品发生变化且非空，标记为已兑换并记录原型
                slotIsExchanged.put(si, true);
                exchangedItemPrototype.put(si, newStack.copy());
            } else {
                // 物品未变化或为空，清除兑换标记
                slotIsExchanged.put(si, false);
                exchangedItemPrototype.remove(si);
            }
        }
        // 处理被完全消耗的槽位
        for (int i = processedItems.size(); i < slotsWithItems.size(); i++) {
            int si = slotsWithItems.get(i);
            items.set(si, ItemStack.EMPTY);
            slotIsExchanged.put(si, false);
            exchangedItemPrototype.remove(si);
        }

        // 恢复标志、更新兑换日期并保存
        skipResetDuringExchange = false;
        lastExchangeDay = currentDay;
        setChanged();
    }

    /**
     * 保存方块实体的额外数据。
     * <p>
     * 保存物品库存、上次兑换日期、绑定玩家 UUID、
     * 各槽位兑换状态与已兑换物品原型。
     *
     * @param out 数据输出
     */
    @Override
    protected void saveAdditional(@NotNull ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putLong("LastExchangeDay", lastExchangeDay);
        if (boundPlayerUUID != null) {
            out.putString("BoundPlayerUUID", boundPlayerUUID.toString());
        }

        // 保存各槽位的兑换状态
        ValueOutput exchangedSlots = out.child("SlotIsExchanged");
        slotIsExchanged.forEach((slot, exchanged) -> exchangedSlots.putBoolean(String.valueOf(slot), exchanged));

        // 保存已兑换槽位的物品原型
        ValueOutput prototypes = out.child("ExchangedItemPrototype");
        exchangedItemPrototype.forEach((slot, stack) ->
                prototypes.store(String.valueOf(slot), ItemStack.OPTIONAL_CODEC, stack));
    }

    /**
     * 从存档加载方块实体的数据。
     * <p>
     * 加载物品库存、上次兑换日期、绑定玩家 UUID、
     * 各槽位兑换状态与已兑换物品原型。
     *
     * @param in 数据输入
     */
    @Override
    protected void loadAdditional(@NotNull ValueInput in) {
        super.loadAdditional(in);
        items.clear();
        ContainerHelper.loadAllItems(in, items);
        lastExchangeDay = in.getLongOr("LastExchangeDay", -1L);
        String boundPlayer = in.getStringOr("BoundPlayerUUID", "");
        boundPlayerUUID = boundPlayer.isEmpty() ? null : UUID.fromString(boundPlayer);

        // 加载各槽位的兑换状态，忽略格式错误的键
        slotIsExchanged.clear();
        in.child("SlotIsExchanged").ifPresent(slotsIn -> {
            for (String key : slotsIn.keySet()) {
                try {
                    slotIsExchanged.put(Integer.parseInt(key), slotsIn.getBooleanOr(key, false));
                } catch (NumberFormatException e) {
                    ShippingBox.LOGGER.warn("[AutoShippingBoxBlockEntity.loadAdditional] 加载槽位状态时出错，无效槽位 key: '{}'", key, e);
                }
            }
        });

        // 加载已兑换槽位的物品原型，忽略格式错误的键
        exchangedItemPrototype.clear();
        in.child("ExchangedItemPrototype").ifPresent(prototypesIn -> {
            for (String key : prototypesIn.keySet()) {
                try {
                    prototypesIn.read(key, ItemStack.OPTIONAL_CODEC)
                            .filter(stack -> !stack.isEmpty())
                            .ifPresent(stack -> exchangedItemPrototype.put(Integer.parseInt(key), stack));
                } catch (NumberFormatException e) {
                    ShippingBox.LOGGER.warn("[AutoShippingBoxBlockEntity.loadAdditional] 加载物品原型时出错，无效槽位 key: '{}'", key, e);
                }
            }
        });
    }
}