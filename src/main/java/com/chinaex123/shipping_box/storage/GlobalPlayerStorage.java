package com.chinaex123.shipping_box.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * 全局玩家存储类 — 26.2 SavedData + Codec。
 *
 * 保存售货箱的背包数据。每个玩家 UUID → NonNullList<ItemStack>(54 槽)。
 */
public class GlobalPlayerStorage extends SavedData {

    public static final int STORAGE_SIZE = 54;

    /** 包装记录,驱动 ItemStack Codec */
    public record ItemStorage(ItemStack items) {
        public static final Codec<ItemStorage> CODEC = ItemStack.OPTIONAL_CODEC.xmap(
                ItemStorage::new, ItemStorage::items);
    }

    public static final Codec<GlobalPlayerStorage> CODEC = Codec.unboundedMap(
            Codec.STRING,
            ItemStorage.CODEC.listOf()
    ).xmap(
            map -> {
                Map<UUID, List<ItemStack>> m = new LinkedHashMap<>();
                map.forEach((strUUID, list) ->
                        m.put(UUID.fromString(strUUID), list.stream().map(ItemStorage::new).toList()));
                return new GlobalPlayerStorage(m);
            },
            s -> {
                Map<String, List<ItemStorage>> out = new LinkedHashMap<>();
                s.storageMap.forEach((uuid, list) ->
                        out.put(uuid.toString(), list.stream().map(ItemStorage::new).toList()));
                return out;
            }
    );

    public static final SavedDataType<GlobalPlayerStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("shipping_box", "global_player_storage"),
            GlobalPlayerStorage::new,
            () -> CODEC,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, List<ItemStack>> storageMap;

    public GlobalPlayerStorage() {
        this.storageMap = new HashMap<>();
    }

    public GlobalPlayerStorage(Map<UUID, List<ItemStack>> storageMap) {
        this.storageMap = new HashMap<>(storageMap);
    }

    public static GlobalPlayerStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public NonNullList<ItemStack> getPlayerStorage(UUID playerUUID) {
        List<ItemStack> list = storageMap.computeIfAbsent(playerUUID, id -> {
            NonNullList<ItemStack> nl = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
            return new ArrayList<>(nl);
        });
        if (list instanceof NonNullList<ItemStack> nl) return nl;
        NonNullList<ItemStack> nl = NonNullList.withSize(Math.max(STORAGE_SIZE, list.size()), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) nl.set(i, list.get(i));
        storageMap.put(playerUUID, new ArrayList<>(nl));
        return nl;
    }

    public ItemStack getItem(int slot, UUID playerUUID) {
        return getPlayerStorage(playerUUID).get(slot);
    }

    public void setItem(int slot, ItemStack stack, UUID playerUUID) {
        getPlayerStorage(playerUUID).set(slot, stack);
        setDirty();
    }

    public ItemStack removeItem(int slot, int amount, UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        ItemStack stack = storage.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.split(amount);
        if (stack.isEmpty()) storage.set(slot, ItemStack.EMPTY);
        setDirty();
        return result;
    }

    public void clearPlayerStorage(UUID playerUUID) {
        storageMap.remove(playerUUID);
        setDirty();
    }

    public Set<UUID> getAllPlayerUUIDs() {
        return new HashSet<>(storageMap.keySet());
    }

    public boolean isPlayerStorageEmpty(UUID playerUUID) {
        List<ItemStack> storage = storageMap.get(playerUUID);
        return storage == null || storage.stream().allMatch(ItemStack::isEmpty);
    }

}
