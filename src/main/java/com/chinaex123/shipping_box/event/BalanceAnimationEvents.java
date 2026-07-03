package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.storage.PlayerBalanceManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives internal currency balance animations on the server tick. */
@EventBusSubscriber(modid = ShippingBox.MOD_ID)
public final class BalanceAnimationEvents {
    private BalanceAnimationEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PlayerBalanceManager.tickAnimations(event.getServer());
    }
}
