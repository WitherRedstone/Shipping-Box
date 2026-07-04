package com.chinaex123.shipping_box.storage;

import com.chinaex123.shipping_box.ShippingBox;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家余额持久化数据类 — 26.2 SavedData + Codec 适配。
 * <p>
 * 使用 Minecraft 26.2 的 SavedDataType + Codec 机制存储和管理
 * 玩家虚拟货币余额数据。数据以 UUID→Integer 的映射形式持久化，
 * 支持自动保存和加载。替代了旧版的外部商店依赖。
 * <p>
 * 使用示例：{@code PlayerBalanceData.get(serverLevel).addBalance(playerUUID, amount)}
 */
public class PlayerBalanceData extends SavedData {
    private static final Codec<Map<String, Integer>> BALANCES_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    public static final Codec<PlayerBalanceData> CODEC = BALANCES_CODEC.xmap(
            map -> {
                Map<UUID, Integer> balances = new HashMap<>();
                map.forEach((uuid, balance) -> balances.put(UUID.fromString(uuid), balance));
                return new PlayerBalanceData(balances);
            },
            data -> {
                Map<String, Integer> balances = new HashMap<>();
                data.balances.forEach((uuid, balance) -> balances.put(uuid.toString(), balance));
                return balances;
            }
    );

    public static final SavedDataType<PlayerBalanceData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "player_balances"),
            PlayerBalanceData::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Integer> balances;

    public PlayerBalanceData() {
        this.balances = new HashMap<>();
    }

    public PlayerBalanceData(Map<UUID, Integer> balances) {
        this.balances = new HashMap<>(balances);
    }

    public static PlayerBalanceData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public int getBalance(UUID playerUUID) {
        return balances.getOrDefault(playerUUID, 0);
    }

    public int addBalance(UUID playerUUID, int amount) {
        if (amount <= 0) {
            return getBalance(playerUUID);
        }
        int newBalance = Math.addExact(getBalance(playerUUID), amount);
        balances.put(playerUUID, newBalance);
        setDirty();
        return newBalance;
    }
}
