package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.storage.PlayerBalanceManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 余额动画事件处理器
 * <p>
 * 在服务端游戏刻（Server Tick）上驱动玩家虚拟货币余额的动画更新。
 * 通过监听 {@link ServerTickEvent.Post} 事件，在每 tick 结束时
 * 调用 {@link PlayerBalanceManager#tickAnimations} 推进动画进度。
 * 使用 NeoForge 的事件总线订阅机制自动注册。
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID)
public final class BalanceAnimationEvents {
    /** 工具类，私有构造防止实例化 */
    private BalanceAnimationEvents() {}

    /**
     * 服务端刻结束事件回调
     * <p>
     * 在每个服务端游戏刻结束时触发，驱动所有在线玩家的
     * 虚拟货币余额变化动画过渡效果。
     *
     * @param event 服务端刻结束后事件，包含服务器实例引用
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PlayerBalanceManager.tickAnimations(event.getServer());
    }
}
