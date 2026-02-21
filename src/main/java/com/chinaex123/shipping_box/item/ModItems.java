package com.chinaex123.shipping_box.item;

import com.chinaex123.shipping_box.ShippingBox;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ShippingBox.MOD_ID);

    // 向指定事件总线注册所有物品
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
