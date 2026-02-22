package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ShippingBoxBlockEntity extends BaseContainerBlockEntity {
    /** 存储物品列表 */
    private NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);

    /** 上次兑换日期 */
    private long lastExchangeDay = -1L;

    /** 记录每个槽位最后放置物品的玩家UUID */
    private final Map<Integer, UUID> slotOwners = new HashMap<>();

    /** 记录玩家放置的物品数量 */
    private final Map<UUID, Integer> playerItemCounts = new HashMap<>();

    // 添加静态变量记录当前操作玩家
    private static final ThreadLocal<ServerPlayer> currentPlayer = new ThreadLocal<>();

    /**
     * 设置当前操作玩家（由外部调用）
     */
    public static void setCurrentPlayer(ServerPlayer player) {
        currentPlayer.set(player);
    }

    /**
     * 清除当前操作玩家
     */
    public static void clearCurrentPlayer() {
        currentPlayer.remove();
    }

    public ShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIPPING_BOX_BE.get(), pos, state);
    }

    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        this.items = items;
        setChanged();
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("container.shipping_box.shipping_box");
    }

    /**
     * 创建容器菜单
     */
    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        // 使用自定义的GUI而不是原版GUI
        return new ShippingBoxMenu(id, inventory, this);
    }

    @Override
    /**
     * 获取容器的总槽数量
     * @return 容器槽数，固定返回54（6行9列的标准大型箱子大小）
     */
    public int getContainerSize() {
        return 54;
    }

    /**
     * 检查容器是否为空
     * @return 如果容器中没有物品则返回true，否则返回false
     */
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
     * @param slot 槽位索引
     * @return 指定槽位的物品
     */
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    /**
     * 从指定槽位移除指定数量的物品
     * @param slot 槽位索引
     * @param amount 移除的物品数量
     * @return 移除的物品
     */
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            slotOwners.remove(slot);
            setChanged();
        }
        return result;
    }

    /**
     * 从指定槽位移除物品但不触发容器更新
     * 与removeItem方法不同，此方法不会调用setChanged()来标记容器已更改
     * 主要用于内部操作或批量处理时避免不必要的更新
     *
     * @param slot 要移除物品的槽位索引
     * @return 从槽位中移除的物品堆栈，如果槽位为空则返回空堆栈
     */
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        slotOwners.remove(slot);
        return result;
    }

    /**
     * 在指定槽位设置物品堆栈
     * 此方法会处理物品堆叠数量限制，并记录放置物品的玩家信息
     * 当物品被修改时会标记容器需要保存
     *
     * @param slot 目标槽位索引
     * @param stack 要设置的物品堆栈
     */
    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        ItemStack oldStack = items.get(slot);
        items.set(slot, stack);

        // 确保物品数量不超过最大堆叠限制
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }

        // 记录玩家信息
        if (level != null && !level.isClientSide && !ItemStack.matches(oldStack, stack)) {
            ServerPlayer player = currentPlayer.get();
            if (player != null) {
                setSlotOwner(slot, player.getUUID());
            }
        }

        setChanged();
    }

    /**
     * 记录玩家在指定槽位放置物品
     */
    public void setSlotOwner(int slot, UUID playerUUID) {
        if (playerUUID != null) {
            slotOwners.put(slot, playerUUID);
            playerItemCounts.put(playerUUID, playerItemCounts.getOrDefault(playerUUID, 0) + 1);
            setChanged();
        }
    }

    @Override
    public void clearContent() {
        items.clear();
        slotOwners.clear();
        playerItemCounts.clear();
        setChanged();
    }

    /**
     * 每游戏刻执行的逻辑更新方法
     * 负责检测时间窗口跨越并触发物品兑换逻辑
     * 能够正确处理时间重置、时间跳跃等各种边界情况
     * <p>
     * 该方法通过直接计算时间差来判断是否应该执行兑换，
     * 避免了使用成员变量来跟踪状态，提高了代码的简洁性和可靠性
     */
    public void tick() {
        // 使用局部变量和计算来替代成员变量
        long currentDayTime = level != null ? level.getDayTime() : 0;
        long currentTimeOfDay = currentDayTime % 24000;

        if (level == null || level.isClientSide) return;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;

        // 检查容器中是否存在物品
        boolean hasItems = false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }

        if (!hasItems) return;

        // 通过计算来检测时间窗口跨越，而不依赖成员变量

        // 情况1：检测是否刚进入兑换时间窗口
        // 通过检查当前是否在兑换窗口内，且容器中有物品
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
     * 执行物品兑换的核心逻辑方法
     * 处理物品匹配、消耗输入、生成输出并重新填充容器
     *
     * @param currentDay 当前游戏天数，用于记录兑换时间
     */
    private void performExchange(long currentDay) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 收集所有非空物品用于处理
        List<ItemStack> itemsToProcess = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                itemsToProcess.add(stack.copy());
            }
        }

        if (itemsToProcess.isEmpty()) {
            return;
        }

        // 清空所有槽位
        items.replaceAll(ignored -> ItemStack.EMPTY);

        List<ItemStack> results = new ArrayList<>();
        boolean exchanged = false;
        boolean hadSuccessfulExchange = false;

        // 循环匹配和执行兑换规则直到无法继续
        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(itemsToProcess);

            if (rule != null) {
                int maxExchanges = getMaxExchanges(rule, itemsToProcess);

                if (maxExchanges > 0) {
                    // 消耗指定次数的输入物品
                    for (int i = 0; i < maxExchanges; i++) {
                        itemsToProcess = ExchangeRecipeManager.consumeInputs(rule, itemsToProcess);
                    }

                    // 生成对应数量的输出物品
                    ItemStack output = rule.getOutputItem().getResultStack().copy();
                    output.setCount(rule.getOutputItem().getCount() * maxExchanges);
                    results.add(output);

                    exchanged = true;
                    hadSuccessfulExchange = true;
                }
            }
        } while (exchanged);

        // 将未处理的物品添加到结果列表
        results.addAll(itemsToProcess);

        // 按最大堆叠数重新分配物品到容器槽位
        int slotIndex = 0;
        for (ItemStack stack : results) {
            if (stack.isEmpty() || slotIndex >= 54) break;

            int maxStackSize = stack.getMaxStackSize();
            int remainingCount = stack.getCount();

            // 将大堆叠分割成多个标准堆叠
            while (remainingCount > 0 && slotIndex < 54) {
                int stackSize = Math.min(remainingCount, maxStackSize);
                ItemStack newStack = stack.copy();
                newStack.setCount(stackSize);
                items.set(slotIndex, newStack);

                remainingCount -= stackSize;
                slotIndex++;
            }
        }

        lastExchangeDay = currentDay;
        setChanged();

        // 兑换成功时播放音效并通知相关玩家
        if (hadSuccessfulExchange) {
            serverLevel.playSound(null, worldPosition,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);

            notifyPlayersOfSuccess(serverLevel);
        }
    }

    /**
     * 向参与物品放置的玩家发送兑换成功通知
     * 包括播放音效和发送成功消息，并清理相关记录
     *
     * @param serverLevel 服务器世界实例，用于获取玩家列表
     */
    private void notifyPlayersOfSuccess(ServerLevel serverLevel) {
        Set<UUID> playerUUIDs = new HashSet<>(slotOwners.values());

        if (playerUUIDs.isEmpty()) {
            return;
        }

        // 遍历所有参与玩家并发送通知
        for (UUID playerUUID : playerUUIDs) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                // 向特定玩家播放声音（无视距离）
                player.playNotifySound(
                        SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                        SoundSource.BLOCKS,
                        0.5F,
                        1.0F
                );

                // 发送成功消息（无视距离）
                ShippingBoxNetworking.sendSuccessMessage(player);
            }
        }

        // 清除记录
        slotOwners.clear();
        playerItemCounts.clear();
    }

    /**
     * 计算指定兑换规则可以执行的最大兑换次数
     * 通过检查每种输入物品的可用数量来确定限制因素
     *
     * @param rule 兑换规则，包含所需的输入物品列表
     * @param availableStacks 当前可用的物品堆列表
     * @return 可以执行的最大兑换次数，如果无法兑换则返回0
     */
    private int getMaxExchanges(ExchangeRule rule, List<ItemStack> availableStacks) {
        int maxExchanges = Integer.MAX_VALUE;

        // 遍历每个必需的输入物品
        for (ExchangeRule.InputItem required : rule.getInputs()) {
            int totalCount = 0;
            // 统计匹配该输入要求的物品总数
            for (ItemStack stack : availableStacks) {
                if (required.matches(stack)) {
                    totalCount += stack.getCount();
                }
            }

            // 计算基于当前输入物品可进行的兑换次数
            int possibleExchanges = totalCount / required.getCount();
            if (possibleExchanges < maxExchanges) {
                maxExchanges = possibleExchanges;
            }
        }

        return maxExchanges;
    }

    /**
     * 将方块实体的额外数据保存到NBT标签中
     * 包括物品库存、上次兑换日期、槽位所有者信息和玩家物品计数
     *
     * @param tag NBT复合标签，用于存储序列化数据
     * @param registries 数据组件注册表提供者
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("LastExchangeDay", lastExchangeDay);

        // 保存槽位所有者信息
        CompoundTag ownersTag = new CompoundTag();
        for (Map.Entry<Integer, UUID> entry : slotOwners.entrySet()) {
            ownersTag.putString(String.valueOf(entry.getKey()), entry.getValue().toString());
        }
        tag.put("SlotOwners", ownersTag);

        // 保存玩家物品计数
        CompoundTag countsTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : playerItemCounts.entrySet()) {
            countsTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("PlayerCounts", countsTag);
    }

    /**
     * 从NBT标签中加载方块实体的额外数据
     * 包括物品库存、上次兑换日期、槽位所有者信息和玩家物品计数
     *
     * @param tag 包含序列化数据的NBT复合标签
     * @param registries 数据组件注册表提供者
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(54, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);

        if (tag.contains("LastExchangeDay")) {
            lastExchangeDay = tag.getLong("LastExchangeDay");
        }

        // 加载槽位所有者信息
        if (tag.contains("SlotOwners")) {
            CompoundTag ownersTag = tag.getCompound("SlotOwners");
            for (String key : ownersTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    UUID uuid = UUID.fromString(ownersTag.getString(key));
                    slotOwners.put(slot, uuid);
                } catch (NumberFormatException e) {
                    // 静默处理解析错误
                } catch (IllegalArgumentException e) {
                    // 静默处理UUID错误
                }
            }
        }

        // 加载玩家物品计数
        if (tag.contains("PlayerCounts")) {
            CompoundTag countsTag = tag.getCompound("PlayerCounts");
            for (String key : countsTag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int count = countsTag.getInt(key);
                    playerItemCounts.put(uuid, count);
                } catch (IllegalArgumentException e) {
                    // 静默处理错误
                }
            }
        }
    }
}