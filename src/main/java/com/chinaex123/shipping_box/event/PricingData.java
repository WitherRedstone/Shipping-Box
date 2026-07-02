package com.chinaex123.shipping_box.event;

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
 * 定价数据持久化类 — 26.2 SavedData + SavedDataType + Codec。
 *
 * 1.21.1 用 SavedData.Factory + save(CompoundTag, Provider) + loadFromNBT(...)。
 * MC 26.2 改为:
 *   - 静态 SavedDataType<T> 字段提供"工厂 + Codec + 唯一 ID"
 *   - save/load 由 Codec 自动处理
 *   - 不再需要 override save(...) 静态方法
 */
public class PricingData extends SavedData {

    public static final String MOD_ID = "shipping_box";

    public static final SavedDataType<PricingData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MOD_ID, "pricing_data"),
            PricingData::new,
            () -> CODEC,
            DataFixTypes.LEVEL
    );

    private static final Codec<Map<String, Integer>> INT_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final Codec<Map<String, Long>> LONG_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG);

    public static final Codec<PricingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            INT_MAP_CODEC.fieldOf("sales_data").forGetter(p -> p.data),
            LONG_MAP_CODEC.fieldOf("sale_days").forGetter(p -> p.lastSaleDays)
    ).apply(instance, PricingData::new));

    private final Map<String, Integer> data;
    private final Map<String, Long> lastSaleDays;

    public PricingData() {
        this.data = new HashMap<>();
        this.lastSaleDays = new HashMap<>();
    }

    public PricingData(Map<String, Integer> data, Map<String, Long> lastSaleDays) {
        this.data = new HashMap<>(data);
        this.lastSaleDays = new HashMap<>(lastSaleDays);
    }

    public static PricingData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<String, Integer> getData() {
        return data;
    }

    public void setData(Map<String, Integer> newData) {
        data.clear();
        data.putAll(newData);
        setDirty();
    }

    public int getCount(String item) {
        return data.getOrDefault(item, 0);
    }

    public void addCount(String item, int count) {
        data.merge(item, count, Integer::sum);
        recordSaleDay(item);
        setDirty();
    }

    public void resetCount(String item) {
        data.put(item, 0);
        lastSaleDays.remove(item);
        setDirty();
    }

    public void recordSaleDay(String item) {
        lastSaleDays.put(item, getCurrentGameDay());
        setDirty();
    }

    public boolean shouldResetCount(String item, int resetDay) {
        Long lastSaleDay = lastSaleDays.get(item);
        if (lastSaleDay == null) {
            recordSaleDay(item);
            return false;
        }
        long currentDay = getCurrentGameDay();
        return (currentDay - lastSaleDay) >= resetDay;
    }

    public int getDaysSinceLastSale(String item) {
        Long lastSaleDay = lastSaleDays.get(item);
        if (lastSaleDay == null) return -1;
        return (int)(getCurrentGameDay() - lastSaleDay);
    }

    public int getResetRemainingDays(String item, int resetDay) {
        int daysPassed = getDaysSinceLastSale(item);
        if (daysPassed == -1) return resetDay;
        return resetDay - daysPassed;
    }

    public void recordResetDay(String itemIdentifier, long day) {
        lastSaleDays.put(itemIdentifier, day);
        setDirty();
    }

    public Long getLastResetDay(String itemIdentifier) {
        return lastSaleDays.get(itemIdentifier);
    }

    private static long getCurrentGameDay() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // MC 26.2: no Level.getDayTime(); use LevelData.getGameTime()
            return server.overworld().getLevelData().getGameTime() / 24000L;
        }
        return 0;
    }
}
