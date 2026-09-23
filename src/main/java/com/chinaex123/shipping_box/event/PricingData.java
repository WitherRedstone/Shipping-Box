package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;

/**
 * 定价数据持久化类。
 * <p>
 * 保存和加载销售计数及最后销售时间。
 * 通过 SavedDataType 与 Codec 描述序列化格式，
 * 并由主世界的数据存储统一管理。
 */
public class PricingData extends SavedData {

    /** 销售计数映射的编解码器 */
    private static final Codec<Map<String, Integer>> INT_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);
    /** 上次销售游戏日映射的编解码器 */
    private static final Codec<Map<String, Long>> LONG_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG);

    /** 本数据类的编解码器 */
    public static final Codec<PricingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            INT_MAP_CODEC.fieldOf("sales_data").forGetter(p -> p.data),
            LONG_MAP_CODEC.fieldOf("sale_days").forGetter(p -> p.lastSaleDays)
    ).apply(instance, PricingData::new));

    /** 本数据类的注册类型，包含标识符、构造函数、编解码器与数据修复类型 */
    public static final SavedDataType<PricingData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "pricing_data"),
            PricingData::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    /** 物品标识符到销售计数的映射 */
    private final Map<String, Integer> data;
    /** 物品标识符到上次销售游戏日的映射 */
    private final Map<String, Long> lastSaleDays;

    /**
     * 构造空的定价数据实例。
     */
    public PricingData() {
        this.data = new HashMap<>();
        this.lastSaleDays = new HashMap<>();
    }

    /**
     * 由已有的销售数据构造定价数据实例。
     * <p>
     * 传入的映射会被复制，避免外部修改影响内部状态。
     *
     * @param data         销售计数映射
     * @param lastSaleDays 上次销售游戏日映射
     */
    public PricingData(Map<String, Integer> data, Map<String, Long> lastSaleDays) {
        this.data = new HashMap<>(data);
        this.lastSaleDays = new HashMap<>(lastSaleDays);
    }

    /**
     * 获取指定世界对应的定价数据实例。
     * <p>
     * 若尚未创建则通过数据存储自动创建。
     *
     * @param level 服务端世界
     * @return 定价数据实例
     */
    public static PricingData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * 获取销售计数映射。
     *
     * @return 物品标识符到销售计数的映射
     */
    public Map<String, Integer> getData() {
        return data;
    }

    /**
     * 设置销售计数映射。
     * <p>
     * 清空当前数据并替换为传入的映射，随后标记为脏数据。
     *
     * @param newData 新的销售计数映射
     */
    public void setData(Map<String, Integer> newData) {
        data.clear();
        data.putAll(newData);
        setDirty();
    }

    /**
     * 获取指定物品的销售计数。
     *
     * @param item 物品标识符
     * @return 销售数量，未记录时返回 0
     */
    public int getCount(String item) {
        return data.getOrDefault(item, 0);
    }

    /**
     * 增加指定物品的销售计数。
     * <p>
     * 累加指定数量并记录当前游戏日，随后标记为脏数据。
     *
     * @param item  物品标识符
     * @param count 要增加的销售数量
     */
    public void addCount(String item, int count) {
        data.merge(item, count, Integer::sum);
        recordSaleDay(item);
        setDirty();
    }

    /**
     * 重置指定物品的销售计数。
     * <p>
     * 将计数归零并清除上次销售日期记录，随后标记为脏数据。
     *
     * @param item 物品标识符
     */
    public void resetCount(String item) {
        data.put(item, 0);
        lastSaleDays.remove(item);
        setDirty();
    }

    /**
     * 记录物品的销售日期。
     * <p>
     * 将指定物品的最后销售时间更新为当前游戏日，并标记为脏数据。
     *
     * @param item 物品标识符
     */
    public void recordSaleDay(String item) {
        lastSaleDays.put(item, getCurrentGameDay());
        setDirty();
    }

    /**
     * 检查是否需要重置指定物品的销售计数。
     * <p>
     * 若从未销售过，则记录当前日期并返回 false；
     * 否则比较距上次销售经过的天数是否已达到重置天数。
     *
     * @param item     物品标识符
     * @param resetDay 重置天数
     * @return 是否需要重置
     */
    public boolean shouldResetCount(String item, int resetDay) {
        Long lastSaleDay = lastSaleDays.get(item);
        if (lastSaleDay == null) {
            recordSaleDay(item);
            return false;
        }
        long currentDay = getCurrentGameDay();
        return (currentDay - lastSaleDay) >= resetDay;
    }

    /**
     * 获取指定物品距离上次销售经过的天数。
     *
     * @param item 物品标识符
     * @return 经过的天数，如果从未销售过则返回 -1
     */
    public int getDaysSinceLastSale(String item) {
        Long lastSaleDay = lastSaleDays.get(item);
        if (lastSaleDay == null) return -1;
        return (int)(getCurrentGameDay() - lastSaleDay);
    }

    /**
     * 获取指定物品的重置剩余天数。
     *
     * @param item     物品标识符
     * @param resetDay 重置天数
     * @return 剩余天数，负数表示已经超过重置时间
     */
    public int getResetRemainingDays(String item, int resetDay) {
        int daysPassed = getDaysSinceLastSale(item);
        if (daysPassed == -1) return resetDay;
        return resetDay - daysPassed;
    }

    /**
     * 记录重置日期。
     *
     * @param itemIdentifier 物品标识符
     * @param day            重置日期
     */
    public void recordResetDay(String itemIdentifier, long day) {
        lastSaleDays.put(itemIdentifier, day);
        setDirty();
    }

    /**
     * 获取上次重置日期。
     *
     * @param itemIdentifier 物品标识符
     * @return 上次重置日期，如果没有记录则返回 null
     */
    public Long getLastResetDay(String itemIdentifier) {
        return lastSaleDays.get(itemIdentifier);
    }

    /**
     * 获取当前游戏日。
     * <p>
     * 通过 Minecraft 服务器获取主世界的当前游戏时间，并除以 24000 转换为天数。
     *
     * @return 当前游戏日数，如果服务器不可用则返回 0
     */
    private static long getCurrentGameDay() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.overworld().getLevelData().getGameTime() / 24000L;
        }
        return 0;
    }
}