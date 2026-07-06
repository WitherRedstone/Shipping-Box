package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.config.CommonConfig;
import net.minecraft.world.level.Level;

/**
 * 时间调度器；
 * 检测游戏时间窗口并触发定时兑换逻辑
 */
public class TimeScheduler {
    public static boolean shouldExchange(Level level, long lastExchangeDay) {
        if (level == null || level.isClientSide) return false;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;
        int configExchangeTime = CommonConfig.EXCHANGE_TIME.get();
        // 设置一个合理的时间窗口（例如180 ticks = 9秒）
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
