package com.chinaex123.shipping_box.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 全局玩家存储类。
 * <p>
 * 使用 SavedData 系统持久化所有玩家的售货箱物品数据。
 * 每个玩家拥有独立的 54 格存储空间，按玩家 UUID 索引，
 * 并通过主世界的数据存储统一管理。
 */
public class GlobalPlayerStorage extends SavedData {

    /** 持久化数据的注册名称 */
    private static final String STORAGE_FILE = "shipping_box_player_data";

    /** 数据工厂，用于从持久化存储中创建或加载本实例 */
    public static final Factory<GlobalPlayerStorage> FACTORY = new Factory<>(
            GlobalPlayerStorage::new,
            GlobalPlayerStorage::loadFromNBT
    );

    /** 玩家 UUID 到物品存储的映射 */
    private final Map<UUID, NonNullList<ItemStack>> playerStorageMap = new HashMap<>();

    /** 单个玩家的存储槽位数量 */
    private static final int STORAGE_SIZE = 54;

    /**
     * 构造空的全局玩家存储实例。
     */
    public GlobalPlayerStorage() {
        super();
    }

    /**
     * 获取指定服务器对应的全局玩家存储实例。
     * <p>
     * 若尚未创建则通过主世界的数据存储自动创建。
     *
     * @param server 服务器实例
     * @return 全局玩家存储实例
     */
    public static GlobalPlayerStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, STORAGE_FILE);
    }

    /**
     * 获取指定玩家的物品存储列表。
     * <p>
     * 若该玩家尚无存储记录，则创建并返回一个空存储。
     *
     * @param playerUUID 玩家 UUID
     * @return 该玩家的物品存储列表（54 格）
     */
    public NonNullList<ItemStack> getPlayerStorage(UUID playerUUID) {
        return playerStorageMap.computeIfAbsent(playerUUID,
                uuid -> NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY));
    }

    /**
     * 获取指定玩家在指定槽位的物品。
     *
     * @param slot       槽位索引
     * @param playerUUID 玩家 UUID
     * @return 该槽位的物品
     */
    public ItemStack getItem(int slot, UUID playerUUID) {
        return getPlayerStorage(playerUUID).get(slot);
    }

    /**
     * 为指定玩家在指定槽位设置物品，并标记为脏数据。
     *
     * @param slot       槽位索引
     * @param stack      要设置的物品堆
     * @param playerUUID 玩家 UUID
     */
    public void setItem(int slot, ItemStack stack, UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        storage.set(slot, stack);
        setDirty();
    }

    /**
     * 从指定玩家的指定槽位移除指定数量的物品。
     * <p>
     * 若实际移除非空物品，则标记为脏数据。
     *
     * @param slot       槽位索引
     * @param amount     移除数量
     * @param playerUUID 玩家 UUID
     * @return 被移除的物品堆
     */
    public ItemStack removeItem(int slot, int amount, UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        ItemStack result = ContainerHelper.removeItem(storage, slot, amount);
        if (!result.isEmpty()) {
            setDirty();
        }
        return result;
    }

    /**
     * 清空指定玩家的存储内容，并标记为脏数据。
     *
     * @param playerUUID 玩家 UUID
     */
    public void clearPlayerStorage(UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        storage.clear();
        setDirty();
    }

    /**
     * 获取所有已记录的玩家 UUID 集合。
     *
     * @return 玩家 UUID 集合
     */
    public Set<UUID> getAllPlayerUUIDs() {
        return new HashSet<>(playerStorageMap.keySet());
    }

    /**
     * 检查指定玩家的存储是否为空。
     *
     * @param playerUUID 玩家 UUID
     * @return 所有槽位均为空返回 true
     */
    public boolean isPlayerStorageEmpty(UUID playerUUID) {
        return getPlayerStorage(playerUUID).stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * 将全部玩家存储数据保存到 NBT 标签中。
     * <p>
     * 仅保存非空玩家存储，以减少存档体积。
     *
     * @param tag        目标 NBT 复合标签
     * @param registries 数据注册表提供者
     * @return 包含所有持久化数据的 CompoundTag 对象
     */
    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playerStoragesTag = new CompoundTag();

        for (Map.Entry<UUID, NonNullList<ItemStack>> entry : playerStorageMap.entrySet()) {
            if (!isPlayerStorageEmpty(entry.getKey())) {
                CompoundTag playerTag = new CompoundTag();
                ContainerHelper.saveAllItems(playerTag, entry.getValue(), registries);
                playerStoragesTag.put(entry.getKey().toString(), playerTag);
            }
        }

        if (!playerStoragesTag.isEmpty()) {
            tag.put("PlayerStorages", playerStoragesTag);
        }

        return tag;
    }

    /**
     * 从 NBT 标签加载全局玩家存储数据。
     * <p>
     * 逐个玩家读取其存储列表，忽略 UUID 格式错误的条目。
     *
     * @param tag        包含持久化数据的 NBT 标签
     * @param registries 数据注册表提供者
     * @return 加载后的全局玩家存储实例
     */
    public static GlobalPlayerStorage loadFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        GlobalPlayerStorage storage = new GlobalPlayerStorage();

        if (tag.contains("PlayerStorages")) {
            CompoundTag playerStoragesTag = tag.getCompound("PlayerStorages");

            for (String playerUUIDStr : playerStoragesTag.getAllKeys()) {
                try {
                    UUID playerUUID = UUID.fromString(playerUUIDStr);
                    CompoundTag playerTag = playerStoragesTag.getCompound(playerUUIDStr);
                    NonNullList<ItemStack> playerStorage = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
                    ContainerHelper.loadAllItems(playerTag, playerStorage, registries);
                    storage.playerStorageMap.put(playerUUID, playerStorage);
                } catch (IllegalArgumentException e) {
                    // 忽略无效UUID
                }
            }
        }

        return storage;
    }
}