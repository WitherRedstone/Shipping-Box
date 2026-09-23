package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.client.menu.AutoShippingBoxMenu;
import com.chinaex123.shipping_box.client.menu.ShippingBoxMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ShippingBox.MOD_ID);

    /** 售货箱屏幕GUI */
    public static final Supplier<MenuType<ShippingBoxMenu>> SHIPPING_BOX = MENU_TYPES.register("shipping_box",
            () -> IMenuTypeExtension.create(ShippingBoxMenu::new));

    /** 自动售货箱屏幕GUI */
    public static final Supplier<MenuType<AutoShippingBoxMenu>> AUTO_SHIPPING_BOX = MENU_TYPES.register("auto_shipping_box",
            () -> IMenuTypeExtension.create(AutoShippingBoxMenu::new));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
