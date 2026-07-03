package com.chinaex123.shipping_box.client;

import com.chinaex123.client.screen.AutoShippingBoxScreen;
import com.chinaex123.client.screen.ShippingBoxScreen;
import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.init.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端 Screen 注册 — 通过 @EventBusSubscriber 自动注册，
 * 不污染 common/server 代码路径。
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID, value = Dist.CLIENT)
public final class ClientSetupEvents {
    private ClientSetupEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SHIPPING_BOX.get(), ShippingBoxScreen::new);
        event.register(ModMenuTypes.AUTO_SHIPPING_BOX.get(), AutoShippingBoxScreen::new);
    }
}
