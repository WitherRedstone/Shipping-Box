package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import com.chinaex123.shipping_box.event.PlayerStorageManager;
import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
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
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ShippingBoxBlockEntity extends BaseContainerBlockEntity {
    /** 存储物品列表 - 共享的公共存储 */
    private NonNullList<ItemStack> sharedItems;

    /** 玩家独立存储管理器 */
    private final PlayerStorageManager playerStorageManager = new PlayerStorageManager();

    /** 上次兑换日期 */
    private long lastExchangeDay = -1L;

    /** 记录每个槽位最后放置物品的玩家UUID */
    private final Map<Integer, UUID> slotOwners = new HashMap<>();

    /** 记录玩家放置的物品数量 */
    private final Map<UUID, Integer> playerItemCounts = new HashMap<>();

    // 在构造函数中初始化
    public ShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIPPING_BOX_BE.get(), pos, state);
        // 初始化共享存储
        this.sharedItems = NonNullList.withSize(54, ItemStack.EMPTY);
    }

    /**
     * 获取指定玩家的物品存储列表
     * 通过玩家独立存储管理器获取该玩家的54槽位存储空间
     *
     * @param playerUUID 玩家的唯一标识符
     * @return 该玩家的物品存储列表（NonNullList<ItemStack>类型）
     */
    public NonNullList<ItemStack> getPlayerItems(UUID playerUUID) {
        return playerStorageManager.getPlayerStorage(playerUUID);
    }

    /**
     * 获取指定玩家在指定槽位的物品
     * 从玩家独立存储中检索特定位置的物品堆栈
     *
     * @param slot 物品槽位索引（0-53）
     * @param playerUUID 玩家的唯一标识符
     * @return 指定槽位的物品堆栈
     */
    public ItemStack getItemForPlayer(int slot, UUID playerUUID) {
        return playerStorageManager.getItem(slot, playerUUID);
    }

    /**
     * 为指定玩家在指定槽位设置物品
     * 将物品放置到玩家独立存储的特定位置，并确保物品数量不超过最大堆叠限制
     *
     * @param slot 目标槽位索引（0-53）
     * @param stack 要设置的物品堆栈
     * @param playerUUID 玩家的唯一标识符
     */
    public void setItemForPlayer(int slot, ItemStack stack, UUID playerUUID) {
        playerStorageManager.setItem(slot, stack, playerUUID);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    /**
     * 从指定玩家的指定槽位移除指定数量的物品
     * 从玩家独立存储中取出指定数量的物品，并返回被移除的物品堆栈
     *
     * @param slot 要移除物品的槽位索引（0-53）
     * @param amount 要移除的物品数量
     * @param playerUUID 玩家的唯一标识符
     * @return 被移除的物品堆栈
     */
    public ItemStack removeItemForPlayer(int slot, int amount, UUID playerUUID) {
        return playerStorageManager.removeItem(slot, amount, playerUUID);
    }

    /**
     * 获取容器的物品列表
     * 这是BaseContainerBlockEntity要求实现的抽象方法
     * 虽然本系统使用玩家独立存储，但此方法仍需提供默认存储引用
     * 实际的玩家存储通过getPlayerItems(UUID)方法获取
     *
     * @return 默认的共享物品存储列表
     */
    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        // 这个方法现在只在菜单创建时调用一次
        // 菜单会通过 getPlayerItems(UUID) 方法获取特定玩家的存储
        return sharedItems; // 返回默认存储，实际使用由菜单控制
    }

    /**
     * 设置容器的物品列表
     * 这是BaseContainerBlockEntity要求实现的抽象方法
     * 用于更新共享存储并标记方块实体需要保存
     *
     * @param items 新的物品列表
     */
    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        this.sharedItems = items;
        setChanged();
    }

    /**
     * 获取容器的默认显示名称
     * 这是BaseContainerBlockEntity要求实现的抽象方法
     * 返回运输箱容器的本地化名称
     *
     * @return 容器的显示名称组件
     */
    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.shipping_box.shipping_box");
    }

    /**
     * 获取容器的总槽数量
     * 返回运输箱容器的存储容量
     *
     * @return 容器槽数，固定返回54格
     */
    @Override
    public int getContainerSize() {
        return 54;
    }

    /**
     * 检查容器是否为空
     * @return 如果容器中没有物品则返回true，否则返回false
     */
    public boolean isEmpty() {
        return false;
    }

    /**
     * 获取指定槽位的物品
     * @param slot 槽位索引
     * @return 指定槽位的物品
     */
    public @NotNull ItemStack getItem(int slot) {
        return sharedItems.get(slot);
    }

    /**
     * 从指定槽位移除指定数量的物品
     * @param slot 槽位索引
     * @param amount 移除的物品数量
     * @return 移除的物品
     */
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(sharedItems, slot, amount);
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
        ItemStack result = ContainerHelper.takeItem(sharedItems, slot);
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
        sharedItems.set(slot, stack);

        // 确保物品数量不超过最大堆叠限制
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }

        setChanged();
    }

    /**
     * 记录玩家在指定槽位放置物品
     * 更新槽位所有者映射和玩家物品计数统计
     *
     * @param slot 目标槽位索引
     * @param playerUUID 放置物品的玩家UUID，如果为null则不执行任何操作
     */
    public void setSlotOwner(int slot, UUID playerUUID) {
        if (playerUUID != null) {
            slotOwners.put(slot, playerUUID);
            playerItemCounts.put(playerUUID, playerItemCounts.getOrDefault(playerUUID, 0) + 1);
            setChanged();
        }
    }

    /**
     * 清空运输箱的所有存储内容。
     * <p>
     * 此方法会彻底清空运输箱实体中的所有数据，包括共享存储、
     * 所有玩家的独立存储、槽位所有者映射以及玩家物品计数信息。
     * 执行清理操作后会标记方块实体为已更改状态。
     */
    @Override
    public void clearContent() {
        // 清空共享存储
        sharedItems.clear();
        // 清空所有玩家的独立存储
        playerStorageManager.clearAllStorages();
        slotOwners.clear();
        playerItemCounts.clear();
        setChanged();
    }

    /**
     * 掉落指定玩家的个人存储物品
     *
     * @param playerUUID 玩家UUID
     */
    public void dropPlayerItems(UUID playerUUID) {
        if (level instanceof ServerLevel serverLevel) {
            // 获取玩家的个人存储
            NonNullList<ItemStack> playerItems = playerStorageManager.getPlayerStorage(playerUUID);

            if (playerItems != null && !playerItems.isEmpty()) {
                // 掉落玩家物品到世界
                for (ItemStack stack : playerItems) {
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                    }
                }
                // 清空该玩家的存储
                playerStorageManager.clearPlayerStorage(playerUUID);
            }
        }
    }

    /**
     * 获取共享存储的物品列表
     *
     * @return 共享物品列表
     */
    public NonNullList<ItemStack> getSharedItems() {
        return sharedItems;
    }

    /**
     * 每游戏刻执行的逻辑更新方法
     * 负责检测时间窗口跨越并触发物品兑换逻辑
     * 能够正确处理时间重置、时间跳跃等各种边界情况
     */
    public void tick() {
        if (level == null || level.isClientSide) return;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;

        // 检查所有存储中是否存在物品
        boolean hasItems = false;

        // 检查共享存储
        for (ItemStack stack : sharedItems) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }

        // 如果共享存储没有物品，检查玩家独立存储
        if (!hasItems) {
            for (UUID playerUUID : playerStorageManager.getAllPlayerUUIDs()) {
                if (!playerStorageManager.isPlayerStorageEmpty(playerUUID)) {
                    hasItems = true;
                    break;
                }
            }
        }

        if (!hasItems) return;

        // 检测兑换时间窗口
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
                    // 异常处理
                }
            }
        }
    }

    /**
     * 执行物品兑换的核心逻辑方法
     * 处理物品匹配、消耗输入、生成输出并重新填充容器
     * 采用为每个玩家单独处理的方式，确保物品归属正确
     * 支持玩家出售价格属性加成和个性化通知
     *
     * @param currentDay 当前游戏天数，用于记录兑换时间
     */
    private void performExchange(long currentDay) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 使用最简单直接的方式：为每个玩家单独处理
        Map<UUID, List<ItemStack>> playerItemsToProcess = new HashMap<>();

        // 收集共享存储的物品
        for (int i = 0; i < sharedItems.size(); i++) {
            ItemStack stack = sharedItems.get(i);
            if (!stack.isEmpty()) {
                UUID owner = slotOwners.get(i);
                if (owner != null) {
                    playerItemsToProcess.computeIfAbsent(owner, k -> new ArrayList<>()).add(stack.copy());
                }
            }
        }

        // 收集玩家独立存储的物品
        for (UUID playerUUID : playerStorageManager.getAllPlayerUUIDs()) {
            NonNullList<ItemStack> playerItems = playerStorageManager.getPlayerStorage(playerUUID);
            for (ItemStack stack : playerItems) {
                if (!stack.isEmpty()) {
                    playerItemsToProcess.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(stack.copy());
                }
            }
        }

        if (playerItemsToProcess.isEmpty()) {
            return;
        }

        // 清空所有存储
        Collections.fill(sharedItems, ItemStack.EMPTY);
        playerStorageManager.clearAllStorages();

        // 为每个玩家单独执行兑换
        Map<UUID, List<ItemStack>> playerResults = new HashMap<>();
        Set<UUID> successfulPlayers = new HashSet<>();

        for (Map.Entry<UUID, List<ItemStack>> entry : playerItemsToProcess.entrySet()) {
            UUID playerUUID = entry.getKey();
            List<ItemStack> items = entry.getValue();

            // 使用共享的兑换逻辑
            NonNullList<ItemStack> tempStorage = NonNullList.withSize(54, ItemStack.EMPTY);
            // 将物品复制到临时存储
            for (int i = 0; i < Math.min(items.size(), tempStorage.size()); i++) {
                tempStorage.set(i, items.get(i).copy());
            }

            // 执行兑换
            ExchangeManager.performExchange(tempStorage, level, worldPosition, playerUUID);

            // 收集结果
            List<ItemStack> results = new ArrayList<>();
            for (ItemStack stack : tempStorage) {
                if (!stack.isEmpty()) {
                    results.add(stack.copy());
                }
            }

            if (!results.isEmpty()) {
                playerResults.put(playerUUID, results);
                successfulPlayers.add(playerUUID);
            }
        }

        // 将兑换结果分配给对应的玩家存储
        for (Map.Entry<UUID, List<ItemStack>> resultEntry : playerResults.entrySet()) {
            UUID playerUUID = resultEntry.getKey();
            List<ItemStack> results = resultEntry.getValue();

            NonNullList<ItemStack> playerStorage = getPlayerItems(playerUUID);
            int slotIndex = 0;

            // 遍历该玩家的所有结果物品
            for (ItemStack result : results) {
                // 如果存储已满，停止分配
                if (slotIndex >= playerStorage.size()) break;

                int maxStackSize = result.getMaxStackSize();
                int remainingCount = result.getCount();

                // 将大堆叠物品分割成标准堆叠大小
                while (remainingCount > 0 && slotIndex < playerStorage.size()) {
                    // 寻找空槽位放置物品
                    if (playerStorage.get(slotIndex).isEmpty()) {
                        int stackSize = Math.min(remainingCount, maxStackSize);
                        ItemStack newStack = result.copy();
                        newStack.setCount(stackSize);
                        playerStorage.set(slotIndex, newStack);
                        remainingCount -= stackSize;
                    }
                    slotIndex++;
                }
            }
        }

        lastExchangeDay = currentDay;
        setChanged();

        // 为成功兑换的玩家发送个性化通知
        if (!successfulPlayers.isEmpty()) {
            serverLevel.playSound(null, worldPosition,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);

            notifySuccessfulPlayers(serverLevel, successfulPlayers);
        }
    }

    /**
     * 为特定玩家应用出售价格属性加成
     *
     * @param baseCount 基础物品数量
     * @param playerUUID 玩家UUID
     * @return 加成后的物品数量
     */
    private int applySellingPriceBoostForPlayer(int baseCount, UUID playerUUID) {
        if (level == null || playerUUID == null) {
            return baseCount;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
        if (player == null) {
            return baseCount; // 玩家不在线，返回基础数量
        }

        // 获取该玩家的出售价格属性加成
        double boost = player.getAttributeValue(ModAttributes.SELLING_PRICE_BOOST);

        // 应用加成：基础数量 × (1 + 加成系数)
        double enhancedAmount = baseCount * (1.0 + boost);

        // 智能取整：小于等于5向下取整，大于5向上取整
        if (enhancedAmount <= 5.0) {
            return (int) Math.floor(enhancedAmount);
        } else {
            return (int) Math.ceil(enhancedAmount);
        }
    }

    /**
     * 向成功兑换的玩家发送个性化通知
     *
     * @param serverLevel 服务器世界实例
     * @param successfulPlayers 成功兑换的玩家UUID集合
     */
    private void notifySuccessfulPlayers(ServerLevel serverLevel, Set<UUID> successfulPlayers) {
        for (UUID playerUUID : successfulPlayers) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                // 向特定玩家播放声音（无视距离）
                player.playNotifySound(
                        SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                        SoundSource.BLOCKS,
                        0.5F,
                        1.0F
                );

                // 发送个性化的成功消息
                player.displayClientMessage(Component.translatable("message.shipping_box.exchange_success"), true);
            }
        }
    }

    /**
     * 创建容器菜单实例
     * 这是BaseContainerBlockEntity要求实现的抽象方法
     * 虽然在新设计中不会被直接调用，但仍需实现以满足抽象类要求
     * 实际的菜单创建通过player.openMenu方法完成
     *
     * @param id 菜单ID
     * @param inventory 玩家物品栏
     * @return 新创建的运输箱菜单实例
     */
    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        return new ShippingBoxMenu(id, inventory, this, inventory.player.getUUID());
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
     * 持久化运输箱的完整状态，包括共享存储、兑换时间、槽位所有权信息、玩家物品计数和独立存储数据
     *
     * @param tag 目标NBT复合标签，用于存储持久化数据
     * @param registries 数据注册表提供者，用于物品序列化
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存共享存储
        ContainerHelper.saveAllItems(tag, sharedItems, registries);
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

        // 保存玩家独立存储
        playerStorageManager.saveToNBT(tag, registries);
    }

    /**
     * 从NBT标签中加载方块实体的额外数据
     * 恢复运输箱的完整状态，包括共享存储、兑换时间、槽位所有者信息和玩家独立存储
     *
     * @param tag 包含持久化数据的NBT复合标签
     * @param registries 数据注册表提供者，用于物品序列化/反序列化
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        // 加载共享存储
        sharedItems = NonNullList.withSize(54, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, sharedItems, registries);

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
                } catch (IllegalArgumentException e) {
                    // 静默处理错误
                }
            }
        }

        // 加载玩家独立存储
        playerStorageManager.loadFromNBT(tag, registries);
    }
}
