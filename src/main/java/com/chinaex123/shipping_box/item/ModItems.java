package com.chinaex123.shipping_box.item;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS_REGISTER =
            DeferredRegister.createItems(ShippingBox.MOD_ID);

    public static final DeferredItem<Item> COPPER_CREEPER_COIN =
            ITEMS_REGISTER.register("copper_creeper_coin", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> IRON_CREEPER_COIN =
            ITEMS_REGISTER.register("iron_creeper_coin", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> GOLD_CREEPER_COIN =
            ITEMS_REGISTER.register("gold_creeper_coin", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> DIAMOND_CREEPER_COIN =
            ITEMS_REGISTER.register("diamond_creeper_coin", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> NETHERITE_CREEPER_COIN =
            ITEMS_REGISTER.register("netherite_creeper_coin", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> EMERALD_CREEPER_COIN =
            ITEMS_REGISTER.register("emerald_creeper_coin", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYMBOLS_CHAOS_CREEPER_COIN =
            ITEMS_REGISTER.register("symbols_chaos_creeper_coin", () -> new Item(new Item.Properties()));

    // 向指定事件总线注册所有物品
    public static void register(IEventBus eventBus) {
        ITEMS_REGISTER.register(eventBus);
    }
}
