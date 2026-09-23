package com.chinaex123.shipping_box.client;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.client.screen.AutoShippingBoxScreen;
import com.chinaex123.shipping_box.client.screen.ShippingBoxScreen;
import com.chinaex123.shipping_box.init.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端 Screen 注册。
 * <p>
 * 负责将模组的自定义 MenuType 与对应的 Screen 关联起来，
 * 使得玩家打开箱子时能显示正确的 GUI 界面。
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID, value = Dist.CLIENT)
public final class ClientSetupEvents {

    /**
     * 私有构造函数，防止实例化。
     * 此类仅包含静态注册逻辑，不应被实例化。
     */
    private ClientSetupEvents() {}

    /**
     * 注册菜单对应的 Screen。
     * <p>
     * 将普通售货箱与自动售货箱的菜单类型分别绑定到对应的 Screen 实现。
     *
     * @param event 菜单 Screen 注册事件
     */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SHIPPING_BOX.get(), ShippingBoxScreen::new);
        event.register(ModMenuTypes.AUTO_SHIPPING_BOX.get(), AutoShippingBoxScreen::new);
    }
}