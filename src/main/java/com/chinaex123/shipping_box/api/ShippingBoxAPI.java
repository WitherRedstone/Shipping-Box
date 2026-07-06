package com.chinaex123.shipping_box.api;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** 售货箱通用 API **/
public class ShippingBoxAPI {
    
    // 存储兑换事件处理器列表
    private static final List<Consumer<ExchangeEvent>> EXCHANGE_EVENT_LISTENERS = new ArrayList<>();

    /** 兑换事件数据类 **/
    public static class ExchangeEvent {
        private final BlockEntity box;
        private final Level level;
        private final NonNullList<ItemStack> inputs;
        private final NonNullList<ItemStack> outputs;
        private final int virtualCurrency;
        private final ExchangeRule rule;
        private boolean canceled = false;
        
        public ExchangeEvent(BlockEntity box, Level level, 
                           NonNullList<ItemStack> inputs, 
                           NonNullList<ItemStack> outputs,
                           int virtualCurrency,
                           ExchangeRule rule) {
            this.box = box;
            this.level = level;
            this.inputs = inputs;
            this.outputs = outputs;
            this.virtualCurrency = virtualCurrency;
            this.rule = rule;
        }
        
        /** 获取售货箱方块实体 **/
        public BlockEntity getBox() {
            return box;
        }
        
        /** 获取世界实例 **/
        public Level getLevel() {
            return level;
        }
        
        /** 获取输入物品列表 **/
        public NonNullList<ItemStack> getInputs() {
            return inputs;
        }
        
        /** 获取输出物品列表 **/
        public NonNullList<ItemStack> getOutputs() {
            return outputs;
        }
        
        /** 获取虚拟货币数量 **/
        public int getVirtualCurrency() {
            return virtualCurrency;
        }
        
        /** 获取匹配的兑换规则 **/
        public ExchangeRule getRule() {
            return rule;
        }
        
        /** 取消此次兑换 **/
        public void cancel() {
            this.canceled = true;
        }
        
        /** 检查是否被取消 **/
        public boolean isCanceled() {
            return canceled;
        }
    }
    
    /**
     * 注册兑换事件监听器
     * @param listener 事件监听器回调
     */
    public static void registerExchangeListener(Consumer<ExchangeEvent> listener) {
        EXCHANGE_EVENT_LISTENERS.add(listener);
    }
    
    /**
     * 移除兑换事件监听器
     * @param listener 要移除的监听器
     */
    public static void unregisterExchangeListener(Consumer<ExchangeEvent> listener) {
        EXCHANGE_EVENT_LISTENERS.remove(listener);
    }
    
    /**
     * 触发兑换事件（内部使用）
     * @param box 售货箱
     * @param level 世界
     * @param inputs 输入物品
     * @param outputs 输出物品
     * @param currency 虚拟货币
     * @param rule 兑换规则
     * @return 如果事件被取消返回 true
     */
    public static boolean onExchange(BlockEntity box, Level level, 
                                    NonNullList<ItemStack> inputs,
                                    NonNullList<ItemStack> outputs,
                                    int currency,
                                    ExchangeRule rule) {
        if (EXCHANGE_EVENT_LISTENERS.isEmpty()) {
            return false;
        }
        
        ExchangeEvent event = new ExchangeEvent(box, level, inputs, outputs, currency, rule);
        
        for (Consumer<ExchangeEvent> listener : EXCHANGE_EVENT_LISTENERS) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // 忽略单个监听器的错误
            }
        }
        
        return event.isCanceled();
    }

    /**
     * 检查指定槽位是否包含已兑换的物品（仅自动售货箱有效）
     * @param box 售货箱方块实体
     * @param slot 槽位索引
     * @return 如果该槽位物品已兑换返回 true
     */
    public static boolean isSlotExchanged(BlockEntity box, int slot) {
        if (!(box instanceof AutoShippingBoxBlockEntity autoBox)) {
            return false;
        }
        if (slot < 0 || slot >= autoBox.getContainerSize()) {
            return false;
        }
        return autoBox.isSlotExchanged(slot);
    }

    /**
     * 获取所有已兑换的槽位（仅自动售货箱有效）
     * @param box 售货箱方块实体
     * @return 已兑换槽位的集合
     */
    public static Set<Integer> getExchangedSlots(BlockEntity box) {
        if (!(box instanceof AutoShippingBoxBlockEntity autoBox)) {
            return Set.of();
        }
        return autoBox.getExchangedSlots();
    }
    
    /**
     * 获取售货箱中的物品数量（总槽位数）
     * @param box 售货箱方块实体
     * @return 槽位总数
     */
    public static int getSlotCount(BlockEntity box) {
        if (box == null) {
            return 0;
        }
        
        try {
            if (box instanceof net.minecraft.world.Container container) {
                return container.getContainerSize();
            }
        } catch (Exception e) {
            // 不支持的方块实体类型
        }
        
        return 0;
    }
    
    /**
     * 获取指定槽位的物品
     * @param box 售货箱方块实体
     * @param slot 槽位索引
     * @return 物品堆（空物品堆如果槽位无效或不支持）
     */
    public static ItemStack getItemInSlot(BlockEntity box, int slot) {
        if (box == null) {
            return ItemStack.EMPTY;
        }
        
        try {
            if (box instanceof net.minecraft.world.Container container) {
                if (slot >= 0 && slot < container.getContainerSize()) {
                    return container.getItem(slot).copy();
                }
            }
        } catch (Exception e) {
            // 不支持的方块实体类型
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * 获取所有物品
     * @param box 售货箱方块实体
     * @return 所有物品的副本列表
     */
    public static NonNullList<ItemStack> getAllItems(BlockEntity box) {
        if (box == null) {
            return NonNullList.create();
        }
        
        try {
            if (box instanceof net.minecraft.world.Container container) {
                NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
                for (int i = 0; i < container.getContainerSize(); i++) {
                    items.set(i, container.getItem(i).copy());
                }
                return items;
            }
        } catch (Exception e) {
            // 不支持的方块实体类型
        }
        
        return NonNullList.create();
    }
    
    /**
     * 获取售货箱绑定的玩家 UUID（仅自动售货箱有效）
     * @param box 售货箱方块实体
     * @return 玩家 UUID（未绑定返回 null）
     */
    public static UUID getBoundPlayerUUID(BlockEntity box) {
        if (!(box instanceof AutoShippingBoxBlockEntity autoBox)) {
            return null;
        }
        return autoBox.getBoundPlayerUUID();
    }
    
    /**
     * 检查售货箱是否有绑定玩家（仅自动售货箱有效）
     * @param box 售货箱方块实体
     * @return 已绑定返回 true
     */
    public static boolean hasBoundPlayer(BlockEntity box) {
        return getBoundPlayerUUID(box) != null;
    }

    /**
     * 向指定槽位添加物品
     * @param box 售货箱方块实体
     * @param slot 槽位索引
     * @param stack 要添加的物品堆
     * @return 实际添加的数量
     */
    public static int addItemToSlot(BlockEntity box, int slot, ItemStack stack) {
        if (box == null || stack.isEmpty()) {
            return 0;
        }
        
        try {
            if (box instanceof net.minecraft.world.Container container) {
                if (slot < 0 || slot >= container.getContainerSize()) {
                    return 0;
                }
                
                ItemStack existing = container.getItem(slot);
                int canAdd = Math.min(stack.getCount(), stack.getMaxStackSize() - existing.getCount());
                
                if (canAdd > 0) {
                    if (existing.isEmpty()) {
                        container.setItem(slot, stack.copyWithCount(canAdd));
                    } else {
                        existing.grow(canAdd);
                        container.setItem(slot, existing);
                    }
                    box.setChanged();
                    return canAdd;
                }
            }
        } catch (Exception e) {
            // 不支持的方块实体类型
        }
        
        return 0;
    }
    
    /**
     * 从指定槽位移除物品
     * @param box 售货箱方块实体
     * @param slot 槽位索引
     * @param count 要移除的数量
     * @return 实际移除的物品堆
     */
    public static ItemStack removeItemFromSlot(BlockEntity box, int slot, int count) {
        if (box == null) {
            return ItemStack.EMPTY;
        }
        
        try {
            if (box instanceof net.minecraft.world.Container container) {
                if (slot < 0 || slot >= container.getContainerSize()) {
                    return ItemStack.EMPTY;
                }
                
                ItemStack existing = container.getItem(slot);
                if (existing.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                
                int toRemove = Math.min(count, existing.getCount());
                ItemStack result = existing.copyWithCount(toRemove);
                existing.shrink(toRemove);
                
                if (existing.isEmpty()) {
                    container.setItem(slot, ItemStack.EMPTY);
                } else {
                    container.setItem(slot, existing);
                }
                box.setChanged();
                
                return result;
            }
        } catch (Exception e) {
            // 不支持的方块实体类型
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * 清空指定槽位
     * @param box 售货箱方块实体
     * @param slot 槽位索引
     * @return 被清空的物品堆
     */
    public static ItemStack clearSlot(BlockEntity box, int slot) {
        if (box == null) {
            return ItemStack.EMPTY;
        }
        
        try {
            if (box instanceof net.minecraft.world.Container container) {
                if (slot < 0 || slot >= container.getContainerSize()) {
                    return ItemStack.EMPTY;
                }
                
                ItemStack existing = container.getItem(slot).copy();
                container.setItem(slot, ItemStack.EMPTY);
                box.setChanged();
                return existing;
            }
        } catch (Exception e) {
            // 不支持的方块实体类型
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * 清空所有槽位
     * @param box 售货箱方块实体
     * @return 被清空的所有物品
     */
    public static NonNullList<ItemStack> clearAllSlots(BlockEntity box) {
        if (box == null) {
            return NonNullList.create();
        }
        
        try {
            if (box instanceof net.minecraft.world.Container container) {
                NonNullList<ItemStack> items = NonNullList.create();
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack item = container.getItem(i);
                    if (!item.isEmpty()) {
                        items.add(item.copy());
                        container.setItem(i, ItemStack.EMPTY);
                    }
                }
                box.setChanged();
                return items;
            }
        } catch (Exception e) {
            // 不支持的方块实体类型
        }
        
        return NonNullList.create();
    }

    /**
     * 获取售货箱的位置
     * @param box 售货箱方块实体
     * @return 方块位置
     */
    public static BlockPos getPosition(BlockEntity box) {
        if (box == null) {
            return BlockPos.ZERO;
        }
        return box.getBlockPos();
    }
    
    /**
     * 检查两个售货箱是否在同一位置
     * @param box1 第一个售货箱
     * @param box2 第二个售货箱
     * @return 位置相同返回 true
     */
    public static boolean isSamePosition(BlockEntity box1, BlockEntity box2) {
        if (box1 == null || box2 == null) {
            return false;
        }
        return box1.getBlockPos().equals(box2.getBlockPos());
    }

    /**
     * 检查方块实体是否是售货箱（普通或自动）
     * @param box 方块实体
     * @return 是售货箱返回 true
     */
    public static boolean isShippingBox(BlockEntity box) {
        if (box == null) {
            return false;
        }
        
        // 检查是否是容器
        if (!(box instanceof net.minecraft.world.Container)) {
            return false;
        }
        
        // 检查类名是否包含 shipping_box
        String className = box.getClass().getName();
        return className.contains("shipping_box");
    }
    
    /**
     * 检查是否是自动售货箱
     * @param box 方块实体
     * @return 是自动售货箱返回 true
     */
    public static boolean isAutoShippingBox(BlockEntity box) {
        return box instanceof AutoShippingBoxBlockEntity;
    }
}
