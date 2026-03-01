package com.chinaex123.shipping_box.network;

import java.util.HashMap;
import java.util.Map;

public class ClientSoldCountCache {
    private static final Map<String, Integer> cache = new HashMap<>();

    /**
     * 更新客户端销售计数缓存
     * <p>
     * 将指定物品的销售数量更新到客户端缓存中，用于在tooltip显示时
     * 提供最新的销售数据，避免频繁的网络请求。
     *
     * @param itemIdentifier 物品标识符，用于定位特定物品的缓存数据
     * @param soldCount 要更新的销售数量
     */
    public static void updateCache(String itemIdentifier, int soldCount) {
        cache.put(itemIdentifier, soldCount);
    }

    /**
     * 获取指定物品的缓存销售数量
     * <p>
     * 从客户端缓存中获取指定物品的销售数量，如果缓存中不存在该物品，
     * 则返回默认值0。此方法用于快速获取销售数据而不必进行网络请求。
     *
     * @param itemIdentifier 物品标识符，用于查找对应的缓存数据
     * @return 指定物品的销售数量，如果未缓存则返回0
     */
    public static int getCachedSoldCount(String itemIdentifier) {
        return cache.getOrDefault(itemIdentifier, 0);
    }

    /**
     * 检查指定物品是否在缓存中存在数据
     * <p>
     * 查询客户端缓存中是否包含指定物品的销售数据记录，
     * 用于判断是否需要从服务器获取最新数据。
     *
     * @param itemIdentifier 物品标识符，用于检查缓存中是否存在该物品的数据
     * @return 如果缓存中存在该物品的数据则返回true，否则返回false
     */
    public static boolean hasCachedData(String itemIdentifier) {
        return cache.containsKey(itemIdentifier);
    }

    /**
     * 清空客户端销售计数缓存
     * <p>
     * 清除客户端缓存中所有的销售数据记录，通常在玩家登出或需要刷新
     * 缓存数据时调用，确保数据的一致性和准确性。
     */
    public static void clearCache() {
        cache.clear();
    }
}
