package com.chinaex123.shipping_box.event;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 定价数据持久化类。
 * <p>
 * 保存和加载销售计数及最后销售时间。
 * 通过 Minecraft 的 SavedData 机制持久化到主世界，
 * 并在数据变更时标记为脏数据触发自动保存。
 */
public class PricingData extends SavedData {

    /** 数据工厂，用于从持久化存储中创建或加载本实例 */
    public static final SavedData.Factory<PricingData> FACTORY = new SavedData.Factory<>(
            PricingData::new,
            (tag, provider) -> loadFromNBT(tag)
    );

    /** 物品标识符到销售计数的映射 */
    private final Map<String, Integer> data = new HashMap<>();
    /** 物品标识符到上次销售游戏日的映射 */
    private Map<String, Long> lastSaleDays = new HashMap<>();

    /**
     * 构造空的定价数据实例。
     */
    public PricingData() {
        super();
    }

    /**
     * 从 NBT 标签加载定价数据实例。
     * <p>
     * 创建新的 PricingData 实例并从提供的 NBT 标签中加载持久化数据。
     * 这是 SavedData 系统要求实现的静态工厂方法，用于反序列化保存的数据。
     *
     * @param tag 包含持久化数据的 CompoundTag 对象
     * @return 加载了数据的 PricingData 实例
     */
    public static PricingData loadFromNBT(CompoundTag tag) {
        PricingData pricingData = new PricingData();
        pricingData.loadDataFromNBT(tag);
        return pricingData;
    }

    /**
     * 将定价数据保存到 NBT 标签中。
     * <p>
     * 实现 SavedData 抽象方法，将内存中的销售数据和游戏日数据序列化到
     * NBT 标签中，以便进行持久化存储。数据分为两个部分存储：
     * sales_data（销售计数）和 sale_days（上次销售日期）。
     *
     * @param tag      目标 NBT 复合标签，用于存储序列化后的数据
     * @param provider 数据注册表提供者，用于处理复杂数据类型的序列化
     * @return 包含所有持久化数据的 CompoundTag 对象
     */
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        CompoundTag dataTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            dataTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("sales_data", dataTag);

        // 保存游戏日数据
        CompoundTag dayTag = new CompoundTag();
        for (Map.Entry<String, Long> entry : lastSaleDays.entrySet()) {
            dayTag.putLong(entry.getKey(), entry.getValue());
        }
        tag.put("sale_days", dayTag);

        return tag;
    }

    /**
     * 从 NBT 标签加载数据到内存中。
     * <p>
     * 从提供的 NBT 标签中反序列化销售数据和游戏日数据，分别加载到
     * 对应的数据结构中。支持增量加载，只加载标签中存在的数据部分。
     *
     * @param tag 包含持久化数据的 CompoundTag 对象
     */
    public void loadDataFromNBT(CompoundTag tag) {
        // 加载销售数据
        if (tag.contains("sales_data")) {
            CompoundTag dataTag = tag.getCompound("sales_data");
            for (String key : dataTag.getAllKeys()) {
                data.put(key, dataTag.getInt(key));
            }
        }

        // 加载游戏日数据
        if (tag.contains("sale_days")) {
            CompoundTag dayTag = tag.getCompound("sale_days");
            for (String key : dayTag.getAllKeys()) {
                lastSaleDays.put(key, dayTag.getLong(key));
            }
        }
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
     * 增加指定物品的销售计数。
     * <p>
     * 将指定物品的销售数量增加指定值，并记录当前游戏日作为最新销售时间。
     * 操作完成后标记数据为脏状态，触发自动保存机制。
     *
     * @param item  物品标识符，用于定位特定物品的销售数据
     * @param count 要增加的销售数量
     */
    public void addCount(String item, int count) {
        int current = data.getOrDefault(item, 0);
        data.put(item, current + count);
        recordSaleDay(item); // 记录销售日期
        setDirty(); // 标记为脏数据
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
        lastSaleDays.remove(item); // 清除上次销售日期
        setDirty();
    }

    /**
     * 获取指定物品的销售计数。
     * <p>
     * 返回指定物品当前的销售数量，如果该物品尚未有任何销售记录，
     * 则返回默认值 0。
     *
     * @param item 物品标识符，用于查找对应的销售数据
     * @return 指定物品的销售数量，未记录时返回 0
     */
    public int getCount(String item) {
        return data.getOrDefault(item, 0);
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

        // 如果从未销售过，不需要重置，但要记录当前日期
        if (lastSaleDay == null) {
            recordSaleDay(item);
            return false;
        }

        long currentDay = getCurrentGameDay();
        long daysPassed = currentDay - lastSaleDay;

        return daysPassed >= resetDay;
    }

    /**
     * 记录物品的销售日期。
     * <p>
     * 将指定物品的最后销售时间更新为当前游戏日，用于后续的重置时间计算
     * 和销售间隔判断。
     *
     * @param item 物品标识符，用于记录该物品的销售时间
     */
    public void recordSaleDay(String item) {
        long currentDay = getCurrentGameDay();
        lastSaleDays.put(item, currentDay);
    }

    /**
     * 获取当前游戏日。
     * <p>
     * 通过 Minecraft 服务器获取主世界的当前游戏时间，并将其转换为游戏日数。
     * 游戏中一天等于 24000 个游戏刻（ticks），通过除法运算得到当前是第几天。
     *
     * @return 当前游戏日数，如果服务器不可用则返回 0
     */
    private long getCurrentGameDay() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.overworld().getDayTime() / 24000L; // 24000 ticks = 1 day
        }
        return 0;
    }

    /**
     * 获取指定物品距离上次销售经过的天数。
     *
     * @param item 物品标识符
     * @return 经过的天数，如果从未销售过则返回 -1
     */
    public int getDaysSinceLastSale(String item) {
        Long lastSaleDay = lastSaleDays.get(item);
        if (lastSaleDay == null) {
            return -1; // 从未销售过
        }

        long currentDay = getCurrentGameDay();
        return (int)(currentDay - lastSaleDay);
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
        if (daysPassed == -1) {
            return resetDay; // 从未销售过，剩余完整周期
        }
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
}