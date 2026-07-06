package com.chinaex123.shipping_box.compat.EclipticSeasons;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/** 节气兼容工具类 **/
public class EclipticSeasonsUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(EclipticSeasonsUtil.class);

    // 静态常量类引用
    static final Class<?> eclipticUtilClass;
    static final Class<?> solarTermEnumClass;
    static final Class<?> seasonClass;

    // 静态常量方法句柄
    private static final MethodHandle getNowSolarTermHandle;
    private static final MethodHandle getSeasonHandle;
    private static final MethodHandle getSerializedNameHandle;
    private static final MethodHandle solarTermOrdinalHandle;
    private static final MethodHandle getNowSolarDayHandle;

    // 配置相关字段
    private static final Class<?> commonConfigSeasonClass;
    private static final MethodHandle lastingDaysOfEachTermHandle;

    // 缓存对象
    private static Object cachedSolarTerm = null;
    private static Level lastCheckedLevel = null;
    private static String cachedSeasonName = null;
    private static Integer cachedSeasonIndex = null;
    private static Integer cachedTermDuration = null;

    static {
        // 临时变量用于初始化
        Class<?> tempEclipticUtilClass = null;
        Class<?> tempSolarTermEnumClass = null;
        Class<?> tempSeasonClass = null;
        MethodHandle tempGetNowSolarTermHandle = null;
        MethodHandle tempGetSeasonHandle = null;
        MethodHandle tempGetSerializedNameHandle = null;
        MethodHandle tempSolarTermOrdinalHandle = null;
        MethodHandle tempGetNowSolarDayHandle = null;
        Class<?> tempCommonConfigSeasonClass = null;
        MethodHandle tempLastingDaysOfEachTermHandle = null;

        // 初始化所有反射对象，如果失败则设为 null
        try {
            tempEclipticUtilClass = Class.forName("com.teamtea.eclipticseasons.api.util.EclipticUtil");

            // 获取 getNowSolarTerm 方法句柄（静态方法）
            Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
            
            // 先加载 SolarTerm 类，用于方法签名
            Class<?> solarTermClass = Class.forName("com.teamtea.eclipticseasons.api.constant.solar.SolarTerm");
            
            tempGetNowSolarTermHandle = MethodHandles.publicLookup()
                    .findStatic(tempEclipticUtilClass, "getNowSolarTerm",
                            MethodType.methodType(solarTermClass, levelClass));

            // 获取 SolarTerm 枚举类
            tempSolarTermEnumClass = solarTermClass;

            // 获取 ordinal 方法句柄
            tempSolarTermOrdinalHandle = MethodHandles.publicLookup()
                    .findVirtual(tempSolarTermEnumClass, "ordinal",
                            MethodType.methodType(int.class));

            // 获取 getSeason() 方法句柄
            // 需要加载 Season 类用于方法签名
            Class<?> seasonCls = Class.forName("com.teamtea.eclipticseasons.api.constant.solar.Season");
            
            tempGetSeasonHandle = MethodHandles.publicLookup()
                    .findVirtual(tempSolarTermEnumClass, "getSeason",
                            MethodType.methodType(seasonCls));

            // 获取 Season 类
            tempSeasonClass = seasonCls;

            // 获取 getSerializedName() 方法句柄
            tempGetSerializedNameHandle = MethodHandles.publicLookup()
                    .findVirtual(tempSeasonClass, "getSerializedName",
                            MethodType.methodType(String.class));

            // 尝试加载配置类
            Class<?> configSeasonClass = null;
            MethodHandle termDurationHandle = null;

            try {
                Class<?> commonConfigClass = Class.forName("com.teamtea.eclipticseasons.config.CommonConfig");
                configSeasonClass = Class.forName("com.teamtea.eclipticseasons.config.CommonConfig$Season");

                // 获取 lastingDaysOfEachTerm 字段的 getter
                var field = configSeasonClass.getField("lastingDaysOfEachTerm");
                var fieldValueClass = field.getType();

                // 获取 ModConfigSpec.IntValue 的 get() 方法
                termDurationHandle = MethodHandles.publicLookup()
                        .findVirtual(fieldValueClass, "get",
                                MethodType.methodType(Object.class));

            } catch (Exception configError) {
                LOGGER.debug("[Shipping Box] 节气模组配置类加载失败，但核心功能仍可用");
            }

            tempCommonConfigSeasonClass = configSeasonClass;
            tempLastingDaysOfEachTermHandle = termDurationHandle;

            // 尝试加载 getNowSolarDay 方法（非必需）
            MethodHandle solarDayHandle = null;
            try {
                solarDayHandle = MethodHandles.publicLookup()
                        .findVirtual(tempEclipticUtilClass, "getNowSolarDay",
                                MethodType.methodType(int.class, levelClass));
            } catch (Exception e) {
                // getNowSolarDay 可能是实例方法，需要 INSTANCE
                try {
                    var instanceField = tempEclipticUtilClass.getDeclaredField("INSTANCE");
                    var instance = instanceField.get(null);
                    solarDayHandle = MethodHandles.publicLookup()
                            .bind(instance, "getNowSolarDay",
                                    MethodType.methodType(int.class, levelClass));
                } catch (Exception e2) {
                    // 完全不可用
                }
            }
            tempGetNowSolarDayHandle = solarDayHandle;

        } catch (Exception e) {
            // 所有反射初始化失败，设置为 null
        }

        // 将临时变量赋值给 final 字段
        eclipticUtilClass = tempEclipticUtilClass;
        solarTermEnumClass = tempSolarTermEnumClass;
        seasonClass = tempSeasonClass;
        getNowSolarTermHandle = tempGetNowSolarTermHandle;
        getSeasonHandle = tempGetSeasonHandle;
        getSerializedNameHandle = tempGetSerializedNameHandle;
        solarTermOrdinalHandle = tempSolarTermOrdinalHandle;
        getNowSolarDayHandle = tempGetNowSolarDayHandle;
        commonConfigSeasonClass = tempCommonConfigSeasonClass;
        lastingDaysOfEachTermHandle = tempLastingDaysOfEachTermHandle;
    }

    /**
     * 检查节气模组是否已加载
     */
    public static boolean isAvailable() {
        return eclipticUtilClass != null && getNowSolarTermHandle != null;
    }

    /**
     * 获取当前世界的节气对象
     * @param level 世界实例
     * @return 节气对象，如果无法获取则返回 null
     */
    @Nullable
    private static Object getSolarTerm(Level level) {
        if (!isAvailable() || level == null || getNowSolarTermHandle == null) {
            return null;
        }

        // 如果缓存有效，直接返回
        if (cachedSolarTerm != null && level == lastCheckedLevel) {
            return cachedSolarTerm;
        }

        try {
            cachedSolarTerm = getNowSolarTermHandle.invokeExact(level);
            lastCheckedLevel = level;
            return cachedSolarTerm;
        } catch (Throwable e) {
            cachedSolarTerm = null;
            return null;
        }
    }

    /**
     * 获取当前世界的季节（带缓存）
     * @param level 世界实例
     * @return 季节名称（spring/summer/autumn/winter），如果无法获取则返回 null
     */
    @Nullable
    public static String getSeason(Level level) {
        // 如果缓存有效，直接返回
        if (cachedSeasonName != null && level == lastCheckedLevel) {
            return cachedSeasonName;
        }

        Object solarTermObj = getSolarTerm(level);

        if (solarTermObj != null && getSeasonHandle != null && getSerializedNameHandle != null) {
            try {
                // 调用 solarTerm.getSeason()
                Object seasonObj = getSeasonHandle.invokeExact(solarTermObj);

                if (seasonObj != null) {
                    // 调用 season.getSerializedName()
                    String seasonName = (String) getSerializedNameHandle.invokeExact(seasonObj);

                    if (seasonName != null && !seasonName.equals("none")) {
                        cachedSeasonName = seasonName.toLowerCase();
                        return cachedSeasonName;
                    }
                }
            } catch (Throwable e) {
                // 反射调用失败，返回 null
            }
        }

        cachedSeasonName = null;
        return null;
    }

    /**
     * 获取当前世界的子季节索引 (0-5)（带缓存）
     * 基于节气在 6 个节气中的位置计算
     * @param level 世界实例
     * @return 子季节索引 (0-5)，如果无法获取则返回 -1
     */
    public static int getSubSeasonIndex(Level level) {
        // 如果缓存有效，直接返回
        if (cachedSeasonIndex != null && level == lastCheckedLevel) {
            return cachedSeasonIndex;
        }

        Object solarTermObj = getSolarTerm(level);

        if (solarTermObj != null && solarTermOrdinalHandle != null) {
            try {
                // 获取节气的 ordinal 值
                int ordinal = (int) solarTermOrdinalHandle.invokeExact(solarTermObj);

                // 每 6 个节气为一个季节，每 2 个节气为一个子季节
                // 所以子季节索引 = (ordinal % 6) / 2
                cachedSeasonIndex = (ordinal % 6) / 2;
                return cachedSeasonIndex;
            } catch (Throwable e) {
                // 反射调用失败，返回 -1
            }
        }

        cachedSeasonIndex = null;
        return -1;
    }

    /**
     * 获取当前世界的子季节名称
     * @param level 世界实例
     * @return 子季节名称（Early/Mid/Late 或类似），如果无法获取则返回 null
     */
    @Nullable
    public static String getSubSeason(Level level) {
        int index = getSubSeasonIndex(level);
        if (index >= 0 && index <= 5) {
            String[] subSeasonNames = {"Early", "Mid", "Late", "Early", "Mid", "Late"};
            return subSeasonNames[index];
        }
        return null;
    }

    /**
     * 获取当前世界的子季节名称
     * @param level 世界实例
     * @return 子季节名称（Early/Mid/Late 或类似），如果无法获取则返回 null
     */
    @Nullable
    public static int getSolarTermDuration(Level level) {
        // 如果已缓存，直接返回
        if (cachedTermDuration != null) {
            return cachedTermDuration;
        }

        if (!isAvailable() || commonConfigSeasonClass == null || lastingDaysOfEachTermHandle == null) {
            return 7; // 默认值
        }

        try {
            // 获取 CommonConfig.Season.lastingDaysOfEachTerm 字段的访问句柄
            var field = commonConfigSeasonClass.getField("lastingDaysOfEachTerm");
            Object intValueObj = field.get(null);
            
            if (intValueObj != null) {
                // 调用 get() 方法获取实际的整数值
                Object valueObj = lastingDaysOfEachTermHandle.invokeExact(intValueObj);

                if (valueObj instanceof Integer i) {
                    cachedTermDuration = i;
                    return cachedTermDuration;
                }
            }
        } catch (Throwable e) {
            // 获取失败返回默认值
        }

        return 7;
    }

    /**
     * 获取当前季节的总天数
     * @param level 世界实例
     * @return 季节总天数（默认 42 天 = 6 个节气 × 7 天），失败返回 -1
     */
    public static int getSeasonDuration(Level level) {
        int termDuration = getSolarTermDuration(level);
        return termDuration * 6; // 一个季节有 6 个节气
    }

    /**
     * 获取当前季节的第几天
     * @param level 世界实例
     * @return 当前季节的日期（1 到季节总天数），失败返回 -1
     */
    public static int getCurrentSeasonDate(Level level) {
        if (!isAvailable() || level == null || getNowSolarDayHandle == null) {
            return -1;
        }

        try {
            int seasonDay = (int) getNowSolarDayHandle.invokeExact(level);

            int termDuration = getSolarTermDuration(level);
            int seasonDuration = termDuration * 6; // 42 天

            // 计算在当前季节的日期
            int seasonDate = (seasonDay % seasonDuration) + 1;

            return Math.max(1, Math.min(seasonDate, seasonDuration));
        } catch (Throwable e) {
            // 获取失败返回 -1
        }

        return -1;
    }

    /**
     * 检查指定季节列表是否包含当前季节
     * @param level 世界实例
     * @param targetSeasons 目标季节列表
     * @return 当前季节在目标列表中返回 true，否则返回 false
     */
    public static boolean isInSeasons(Level level, List<String> targetSeasons) {
        String currentSeason = getSeason(level);

        if (currentSeason == null || targetSeasons == null || targetSeasons.isEmpty()) {
            return false;
        }

        // 检查是否包含"all"，如果是则直接返回 true
        if (targetSeasons.contains("all")) {
            return true;
        }

        return targetSeasons.contains(currentSeason);
    }
}
