package com.chinaex123.shipping_box.event;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup.Provider;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 玩家独立存储管理器
 * 负责管理每个玩家的独立物品存储，提供统一的访问接口
 */
public class PlayerStorageManager {
    /** 每个玩家独立的物品存储映射 */
    private final Map<UUID, NonNullList<ItemStack>> playerStorageMap = new HashMap<>();

    /** 默认存储大小 */
    private static final int DEFAULT_STORAGE_SIZE = 54;

    /**
     * 获取指定玩家的物品存储列表
     * 如果该玩家尚无存储列表，则创建一个新的空存储列表
     *
     * @param playerUUID 玩家的唯一标识符
     * @return 该玩家的物品存储列表
     */
    public NonNullList<ItemStack> getPlayerStorage(UUID playerUUID) {
        return playerStorageMap.computeIfAbsent(playerUUID,
                uuid -> NonNullList.withSize(DEFAULT_STORAGE_SIZE, ItemStack.EMPTY));
    }

    /**
     * 获取指定玩家在指定槽位的物品
     *
     * @param slot 物品槽位索引
     * @param playerUUID 玩家的唯一标识符
     * @return 指定槽位的物品堆栈
     */
    public ItemStack getItem(int slot, UUID playerUUID) {
        return getPlayerStorage(playerUUID).get(slot);
    }

    /**
     * 为指定玩家在指定槽位设置物品
     *
     * @param slot 目标槽位索引
     * @param stack 要设置的物品堆栈
     * @param playerUUID 玩家的唯一标识符
     */
    public void setItem(int slot, ItemStack stack, UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        storage.set(slot, stack);
    }

    /**
     * 从指定玩家的指定槽位移除指定数量的物品
     *
     * @param slot 要移除物品的槽位索引
     * @param amount 要移除的物品数量
     * @param playerUUID 玩家的唯一标识符
     * @return 被移除的物品堆栈
     */
    public ItemStack removeItem(int slot, int amount, UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        return ContainerHelper.removeItem(storage, slot, amount);
    }

    /**
     * 清空指定玩家的存储
     *
     * @param playerUUID 玩家的唯一标识符
     */
    public void clearPlayerStorage(UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        storage.clear();
    }

    /**
     * 清空所有玩家的存储
     */
    public void clearAllStorages() {
        for (NonNullList<ItemStack> storage : playerStorageMap.values()) {
            storage.clear();
        }
        playerStorageMap.clear();
    }

    /**
     * 获取所有有存储数据的玩家UUID
     *
     * @return 玩家UUID集合
     */
    public Set<UUID> getAllPlayerUUIDs() {
        return new HashSet<>(playerStorageMap.keySet());
    }

    /**
     * 检查指定玩家的存储是否为空
     *
     * @param playerUUID 玩家的唯一标识符
     * @return 如果存储中没有物品则返回true，否则返回false
     */
    public boolean isPlayerStorageEmpty(UUID playerUUID) {
        return getPlayerStorage(playerUUID).stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * 获取存储中的玩家数量
     *
     * @return 当前有存储数据的玩家数量
     */
    public int getPlayerCount() {
        return playerStorageMap.size();
    }

    /**
     保存所有玩家存储数据到NBT标签
     *
     * @param tag 目标NBT复合标签
     * @param registries 数据注册表提供者
     */
    public void saveToNBT(CompoundTag tag, @NotNull Provider registries) {
        CompoundTag playerStoragesTag = new CompoundTag();

        for (Map.Entry<UUID, NonNullList<ItemStack>> entry : playerStorageMap.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            ContainerHelper.saveAllItems(playerTag, entry.getValue(), registries);
            playerStoragesTag.put(entry.getKey().toString(), playerTag);
        }

        tag.put("PlayerStorages", playerStoragesTag);
    }

    /**
     * 从NBT标签加载所有玩家存储数据
     *
     * @param tag 包含数据的NBT复合标签
     * @param registries 数据注册表提供者
     */
    public void loadFromNBT(CompoundTag tag, @NotNull Provider registries) {
        playerStorageMap.clear();

        if (tag.contains("PlayerStorages")) {
            CompoundTag playerStoragesTag = tag.getCompound("PlayerStorages");

            for (String playerUUIDStr : playerStoragesTag.getAllKeys()) {
                try {
                    UUID playerUUID = UUID.fromString(playerUUIDStr);
                    CompoundTag playerTag = playerStoragesTag.getCompound(playerUUIDStr);
                    NonNullList<ItemStack> playerStorage = NonNullList.withSize(DEFAULT_STORAGE_SIZE, ItemStack.EMPTY);
                    ContainerHelper.loadAllItems(playerTag, playerStorage, registries);
                    playerStorageMap.put(playerUUID, playerStorage);
                } catch (IllegalArgumentException e) {
                    // 静默处理UUID错误
                }
            }
        }
    }
}
