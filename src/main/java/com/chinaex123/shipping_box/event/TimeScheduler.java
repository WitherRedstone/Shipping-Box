package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.config.CommonConfig;
import net.minecraft.world.level.Level;

/**
 * 时间调度器。
 * <p>
 * 检测游戏时间窗口并触发定时兑换逻辑。
 * 通过配置的兑换时刻与固定长度的时间窗口判断当前是否处于可兑换时段，
 * 并结合距上次兑换的间隔、首次兑换与时间重置等边界情况决定是否触发。
 */
public class TimeScheduler {

    /**
     * 判断当前是否应当触发兑换。
     * <p>
     * 仅在服务端有效。先判断当前时刻是否落在配置的兑换时间窗口内
     * （支持跨天窗口），再判断距上次兑换是否已满一天、
     * 是否为首次兑换、或游戏时间是否发生重置。
     *
     * @param level           世界实例
     * @param lastExchangeDay 上次兑换的游戏日，-1 表示尚未兑换过
     * @return 应当触发兑换返回 true，否则返回 false
     */
    public static boolean shouldExchange(Level level, long lastExchangeDay) {
        if (level == null || level.isClientSide) return false;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;
        int configExchangeTime = CommonConfig.EXCHANGE_TIME.get();
        // 设置一个合理的时间窗口
        int windowEnd = configExchangeTime + 180;

        // 处理跨天的时间窗口
        boolean inWindow;
        if (windowEnd >= 24000) {
            inWindow = (timeOfDay >= configExchangeTime) || (timeOfDay <= (windowEnd % 24000));
        } else {
            inWindow = (timeOfDay >= configExchangeTime && timeOfDay <= windowEnd);
        }

        if (!inWindow) return false;

        long timeSinceLastExchange = dayTime - (lastExchangeDay * 24000);

        // 如果距离上次兑换超过一天，或者这是第一次兑换，或者时间被重置
        return timeSinceLastExchange >= 24000 || lastExchangeDay == -1L || timeSinceLastExchange < 0;
    }
}