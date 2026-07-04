package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.menu.AutoShippingBoxMenu;
import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 菜单类型注册类
 * <p>
 * 负责注册售货箱模组的所有容器菜单类型。
 * 使用 NeoForge 的 DeferredRegister 系统进行延迟注册，
 * 支持通过网络缓冲区（RegistryFriendlyByteBuf）构造菜单实例。
 * <p>
 * 当前注册的菜单类型：
 * <ul>
 *   <li>普通售货箱菜单（shipping_box）</li>
 *   <li>自动售货箱菜单（auto_shipping_box）</li>
 * </ul>
 */
public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ShippingBox.MOD_ID);

    public static final Supplier<MenuType<ShippingBoxMenu>> SHIPPING_BOX =
            MENU_TYPES.register("shipping_box",
                    () -> IMenuTypeExtension.create(
                            (id, inv, buf) -> new ShippingBoxMenu(id, inv, buf)));

    public static final Supplier<MenuType<AutoShippingBoxMenu>> AUTO_SHIPPING_BOX =
            MENU_TYPES.register("auto_shipping_box",
                    () -> IMenuTypeExtension.create(
                            (id, inv, buf) -> new AutoShippingBoxMenu(id, inv, buf)));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
