package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 运输箱方块实体类（共享存储）
 * <p>
 * 所有玩家共享同一个54格存储空间
 * 支持自动兑换功能和提示信息
 */
public class ShippingBoxBlockEntity extends BaseContainerBlockEntity {
    /** 存储物品列表 */
    private NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);

    /** 上次兑换日期 */
    private long lastExchangeDay = -1L;

    /** 物品处理器包装器 */
    private final IItemHandler itemHandler = new InvWrapper(this);

    /**
     * 构造函数
     */
    public ShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIPPING_BOX_BE.get(), pos, state);
    }

    /**
     * 获取物品存储列表
     */
    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    /**
     * 设置物品存储列表
     */
    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
        setChanged();
    }

    /**
     * 获取运输箱容器的默认显示名称
     */
    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("container.shipping_box.shipping_box");
    }

    /**
     * 创建容器菜单
     */
    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x6, id, inventory, this, 6);
    }

    /**
     * 获取容器大小
     */
    @Override
    public int getContainerSize() {
        return 54;
    }

    /**
     * 检查容器是否为空
     */
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取指定槽位的物品
     */
    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    /**
     * 从指定槽位移除指定数量的物品
     */
    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    /**
     * 移除指定槽位的物品（不更新客户端）
     */
    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    /**
     * 在指定槽位设置物品
     */
    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    /**
     * 清空容器内容
     */
    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /**
     * 每游戏刻执行的逻辑更新（每天早上6:00-6:05兑换）
     */
    public void tick() {
        if (level == null || level.isClientSide) return;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;
        long currentDay = dayTime / 24000;

        // 检查是否有物品
        boolean hasItems = false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }

        if (hasItems && timeOfDay >= 0 && timeOfDay <= 180 && lastExchangeDay < currentDay) {
            try {
                performExchange(currentDay);
                lastExchangeDay = currentDay;
                setChanged();
            } catch (Exception e) {
                ShippingBox.LOGGER.error("Exchange failed: ", e);
            }
        }
    }

    /**
     * 执行物品兑换
     */
    private void performExchange(long currentDay) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 收集所有非空物品
        List<ItemStack> itemsToProcess = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                itemsToProcess.add(stack.copy());
            }
        }

        if (itemsToProcess.isEmpty()) {
            return;
        }

        // 清空原始存储
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }

        List<ItemStack> results = new ArrayList<>();
        boolean exchanged = false;
        boolean hadSuccessfulExchange = false;

        // 应用所有可用的兑换规则
        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(itemsToProcess);

            if (rule != null) {
                int maxExchanges = getMaxExchanges(rule, itemsToProcess);

                if (maxExchanges > 0) {
                    // 执行兑换
                    for (int i = 0; i < maxExchanges; i++) {
                        itemsToProcess = ExchangeRecipeManager.consumeInputs(rule, itemsToProcess);
                    }

                    // 添加产出物品
                    ItemStack output = rule.getResultStack().copy();
                    output.setCount(rule.getOutputItem().getCount() * maxExchanges);
                    results.add(output);

                    exchanged = true;
                    hadSuccessfulExchange = true;
                }
            }
        } while (exchanged);

        // 添加剩余物品
        results.addAll(itemsToProcess);

        // 将结果放回存储
        for (int i = 0; i < Math.min(results.size(), 54); i++) {
            items.set(i, results.get(i));
        }

        // 更新兑换日期
        lastExchangeDay = currentDay;
        setChanged();

        // 播放音效和发送提示
        if (hadSuccessfulExchange) {
            serverLevel.playSound(null, worldPosition,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);

            // 向附近玩家发送成功消息
            for (Player player : serverLevel.players()) {
                double distance = player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
                if (distance <= 4096.0) { // 64格范围
                    player.displayClientMessage(
                            Component.translatable("message.shipping_box.exchange_success"),
                            true);
                }
            }
        }
    }

    /**
     * 计算基于当前物品可进行的最大兑换次数
     */
    private int getMaxExchanges(ExchangeRule rule, List<ItemStack> availableStacks) {
        int maxExchanges = Integer.MAX_VALUE;

        for (ExchangeRule.InputItem required : rule.getInputs()) {
            int totalCount = 0;
            for (ItemStack stack : availableStacks) {
                if (required.matches(stack)) {
                    totalCount += stack.getCount();
                }
            }

            int possibleExchanges = totalCount / required.getCount();
            if (possibleExchanges < maxExchanges) {
                maxExchanges = possibleExchanges;
            }
        }

        return maxExchanges;
    }

    /**
     * 保存额外数据到NBT标签
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存物品数据
        ContainerHelper.saveAllItems(tag, items, registries);

        // 保存兑换日期
        tag.putLong("LastExchangeDay", lastExchangeDay);
    }

    /**
     * 从NBT标签加载额外数据
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        // 加载物品数据
        items = NonNullList.withSize(54, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);

        // 加载兑换日期
        if (tag.contains("LastExchangeDay")) {
            lastExchangeDay = tag.getLong("LastExchangeDay");
        }
    }

    /**
     * 获取物品处理器
     */
    public IItemHandler getItemHandler() {
        return itemHandler;
    }
}
