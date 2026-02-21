package com.chinaex123.shipping_box.menu;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    // 创建菜单类型注册器实例
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ShippingBox.MOD_ID);

    // 注册售货箱菜单类型
    public static final Supplier<MenuType<ShippingBoxMenu>> SHIPPING_BOX_MENU =
            MENU_TYPES.register("shipping_box_menu",
                    () -> new MenuType<>(new ShippingBoxMenu.Factory(), FeatureFlags.VANILLA_SET));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
