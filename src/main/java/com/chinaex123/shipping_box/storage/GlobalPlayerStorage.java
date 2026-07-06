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
 * 全局玩家存储类；
 * 使用 SavedData 系统持久化所有玩家的售货箱物品数据
 */
public class GlobalPlayerStorage extends SavedData {
    private static final String STORAGE_FILE = "shipping_box_player_data";

    public static final Factory<GlobalPlayerStorage> FACTORY = new Factory<>(
            GlobalPlayerStorage::new,
            GlobalPlayerStorage::loadFromNBT
    );

    private final Map<UUID, NonNullList<ItemStack>> playerStorageMap = new HashMap<>();
    private static final int STORAGE_SIZE = 54;

    public GlobalPlayerStorage() {
        super();
    }

    public static GlobalPlayerStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, STORAGE_FILE);
    }

    public NonNullList<ItemStack> getPlayerStorage(UUID playerUUID) {
        return playerStorageMap.computeIfAbsent(playerUUID,
                uuid -> NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY));
    }

    public ItemStack getItem(int slot, UUID playerUUID) {
        return getPlayerStorage(playerUUID).get(slot);
    }

    public void setItem(int slot, ItemStack stack, UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        storage.set(slot, stack);
        setDirty();
    }

    public ItemStack removeItem(int slot, int amount, UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        ItemStack result = ContainerHelper.removeItem(storage, slot, amount);
        if (!result.isEmpty()) {
            setDirty();
        }
        return result;
    }

    public void clearPlayerStorage(UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        storage.clear();
        setDirty();
    }

    public Set<UUID> getAllPlayerUUIDs() {
        return new HashSet<>(playerStorageMap.keySet());
    }

    public boolean isPlayerStorageEmpty(UUID playerUUID) {
        return getPlayerStorage(playerUUID).stream().allMatch(ItemStack::isEmpty);
    }

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
