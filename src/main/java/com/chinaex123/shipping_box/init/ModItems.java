package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.item.DimensionalPouchItem;
import com.chinaex123.shipping_box.compat.ViScriptShop.ViScriptCoinItemServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public interface ModItems {
    DeferredRegister.Items ITEMS_REGISTER =
            DeferredRegister.createItems(ShippingBox.MOD_ID);

    DeferredItem<Item> DIMENSIONAL_POUCH = ITEMS_REGISTER.register("dimensional_pouch",
            () -> new DimensionalPouchItem(new Item.Properties().rarity(Rarity.COMMON)));
    DeferredItem<Item> COPPER_CREEPER_COIN = ITEMS_REGISTER.register("copper_creeper_coin",
            () -> new ViScriptCoinItemServer(new Item.Properties().rarity(Rarity.COMMON), 1,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.copper_creeper_coin"))));
    DeferredItem<Item> IRON_CREEPER_COIN = ITEMS_REGISTER.register("iron_creeper_coin",
            () -> new ViScriptCoinItemServer(new Item.Properties().rarity(Rarity.COMMON), 8,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.iron_creeper_coin"))));
    DeferredItem<Item> GOLD_CREEPER_COIN = ITEMS_REGISTER.register("gold_creeper_coin",
            () -> new ViScriptCoinItemServer(new Item.Properties().rarity(Rarity.UNCOMMON), 16,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.gold_creeper_coin"))));
    DeferredItem<Item> DIAMOND_CREEPER_COIN = ITEMS_REGISTER.register("diamond_creeper_coin",
            () -> new ViScriptCoinItemServer(new Item.Properties().rarity(Rarity.RARE), 64,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.diamond_creeper_coin"))));
    DeferredItem<Item> NETHERITE_CREEPER_COIN = ITEMS_REGISTER.register("netherite_creeper_coin",
            () -> new ViScriptCoinItemServer(new Item.Properties().rarity(Rarity.RARE), 512,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.netherite_creeper_coin"))));
    DeferredItem<Item> SYMBOLS_CHAOS_CREEPER_COIN = ITEMS_REGISTER.register("symbols_chaos_creeper_coin",
            () -> new ViScriptCoinItemServer(new Item.Properties().rarity(Rarity.EPIC), 4096,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.symbols_chaos_creeper_coin"))));
    DeferredItem<Item> EMERALD_CREEPER_COIN = ITEMS_REGISTER.register("emerald_creeper_coin",
            () -> new ViScriptCoinItemServer(new Item.Properties().rarity(Rarity.EPIC), 256,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.emerald_creeper_coin"))));

    static void register(IEventBus eventBus) {
        ITEMS_REGISTER.register(eventBus);
    }
}
