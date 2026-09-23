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
 * 全局玩家存储类。
 * <p>
 * 使用 SavedDataType 与 Codec 描述序列化格式，持久化所有玩家的售货箱物品数据。
 * 每个玩家拥有独立的 54 格存储空间，按玩家 UUID 索引，
 * 并由服务端世界的数据存储统一管理。
 */
public class GlobalPlayerStorage extends SavedData {

    /** 单个玩家的存储槽位数量 */
    public static final int STORAGE_SIZE = 54;

    /**
     * 物品存储包装记录，驱动 ItemStack 编解码器。
     *
     * @param items 存储的物品堆
     */
    public record ItemStorage(ItemStack items) {

        /** 包装记录的编解码器，基于 ItemStack.OPTIONAL_CODEC 映射 */
        public static final Codec<ItemStorage> CODEC = ItemStack.OPTIONAL_CODEC.xmap(
                ItemStorage::new, ItemStorage::items);
    }

    /** 本数据类的编解码器，以字符串 UUID 到物品列表的映射为序列化格式 */
    public static final Codec<GlobalPlayerStorage> CODEC = Codec.unboundedMap(
            Codec.STRING,
            ItemStorage.CODEC.listOf()
    ).xmap(
            (Map<String, List<ItemStorage>> map) -> {
                Map<UUID, List<ItemStack>> m = new LinkedHashMap<>();
                map.forEach((strUUID, list) ->
                        m.put(UUID.fromString(strUUID),
                                list.stream().map(ItemStorage::items).toList()));
                return new GlobalPlayerStorage(m);
            },
            (GlobalPlayerStorage s) -> {
                Map<String, List<ItemStorage>> out = new LinkedHashMap<>();
                s.storageMap.forEach((uuid, list) ->
                        out.put(uuid.toString(),
                                list.stream().map(ItemStorage::new).toList()));
                return out;
            }
    );

    /** 本数据类的注册类型，包含标识符、构造函数、编解码器与数据修复类型 */
    public static final SavedDataType<GlobalPlayerStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("shipping_box", "global_player_storage"),
            GlobalPlayerStorage::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    /** 玩家 UUID 到物品存储列表的映射 */
    private final Map<UUID, List<ItemStack>> storageMap;

    /**
     * 构造空的全局玩家存储实例。
     */
    public GlobalPlayerStorage() {
        this.storageMap = new HashMap<>();
    }

    /**
     * 由已有的存储映射构造全局玩家存储实例。
     * <p>
     * 传入的映射会被复制，避免外部修改影响内部状态。
     *
     * @param storageMap 玩家 UUID 到物品存储列表的映射
     */
    public GlobalPlayerStorage(Map<UUID, List<ItemStack>> storageMap) {
        this.storageMap = new HashMap<>(storageMap);
    }

    /**
     * 获取指定世界对应的全局玩家存储实例。
     * <p>
     * 若尚未创建则通过数据存储自动创建。
     *
     * @param level 服务端世界
     * @return 全局玩家存储实例
     */
    public static GlobalPlayerStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * 获取指定玩家的物品存储列表。
     * <p>
     * 若该玩家尚无存储记录，则创建并返回一个空存储。
     * 返回结果统一为 54 格（或与已有记录等长的）NonNullList。
     *
     * @param playerUUID 玩家 UUID
     * @return 该玩家的物品存储列表
     */
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
        getPlayerStorage(playerUUID).set(slot, stack);
        setDirty();
    }

    /**
     * 从指定玩家的指定槽位移除指定数量的物品。
     * <p>
     * 槽位为空时直接返回空物品堆；移除后若槽位清空则置为空，
     * 并标记为脏数据。
     *
     * @param slot       槽位索引
     * @param amount     移除数量
     * @param playerUUID 玩家 UUID
     * @return 被移除的物品堆
     */
    public ItemStack removeItem(int slot, int amount, UUID playerUUID) {
        NonNullList<ItemStack> storage = getPlayerStorage(playerUUID);
        ItemStack stack = storage.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.split(amount);
        if (stack.isEmpty()) storage.set(slot, ItemStack.EMPTY);
        setDirty();
        return result;
    }

    /**
     * 清空指定玩家的存储内容，并标记为脏数据。
     *
     * @param playerUUID 玩家 UUID
     */
    public void clearPlayerStorage(UUID playerUUID) {
        storageMap.remove(playerUUID);
        setDirty();
    }

    /**
     * 获取所有已记录的玩家 UUID 集合。
     *
     * @return 玩家 UUID 集合
     */
    public Set<UUID> getAllPlayerUUIDs() {
        return new HashSet<>(storageMap.keySet());
    }

    /**
     * 检查指定玩家的存储是否为空。
     * <p>
     * 若该玩家无存储记录，同样视为空。
     *
     * @param playerUUID 玩家 UUID
     * @return 所有槽位均为空返回 true
     */
    public boolean isPlayerStorageEmpty(UUID playerUUID) {
        List<ItemStack> storage = storageMap.get(playerUUID);
        return storage == null || storage.stream().allMatch(ItemStack::isEmpty);
    }

}