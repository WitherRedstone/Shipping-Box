package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.compat.ViScriptShop.ViScriptCoinItemServer;
import com.chinaex123.shipping_box.item.DimensionalPouchItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public interface ModItems {
    DeferredRegister.Items ITEMS_REGISTER = DeferredRegister.createItems(ShippingBox.MOD_ID);

    /** 次元钱袋 */
    DeferredItem<Item> DIMENSIONAL_POUCH = ITEMS_REGISTER.registerItem("dimensional_pouch",
            props -> new DimensionalPouchItem(props.stacksTo(1)),
            () -> new Item.Properties().rarity(Rarity.COMMON));

    /** 铜爬爬币 */
    DeferredItem<Item> COPPER_CREEPER_COIN = ITEMS_REGISTER.registerItem("copper_creeper_coin",
            props -> new ViScriptCoinItemServer(props, 1,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.copper_creeper_coin"))),
            () -> new Item.Properties().rarity(Rarity.COMMON));
    /** 铁爬爬币 */
    DeferredItem<Item> IRON_CREEPER_COIN = ITEMS_REGISTER.registerItem("iron_creeper_coin",
            props -> new ViScriptCoinItemServer(props, 8,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.iron_creeper_coin"))),
            () -> new Item.Properties().rarity(Rarity.COMMON));
    /** 金爬爬币 */
    DeferredItem<Item> GOLD_CREEPER_COIN = ITEMS_REGISTER.registerItem("gold_creeper_coin",
            props -> new ViScriptCoinItemServer(props, 16,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.gold_creeper_coin"))),
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    /** 钻石爬爬币 */
    DeferredItem<Item> DIAMOND_CREEPER_COIN = ITEMS_REGISTER.registerItem("diamond_creeper_coin",
            props -> new ViScriptCoinItemServer(props, 64,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.diamond_creeper_coin"))),
            () -> new Item.Properties().rarity(Rarity.RARE));
    /** 下界合金爬爬币 */
    DeferredItem<Item> NETHERITE_CREEPER_COIN = ITEMS_REGISTER.registerItem("netherite_creeper_coin",
            props -> new ViScriptCoinItemServer(props, 512,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.netherite_creeper_coin"))),
            () -> new Item.Properties().rarity(Rarity.RARE));
    /** 混沌立方爬爬币 */
    DeferredItem<Item> SYMBOLS_CHAOS_CREEPER_COIN = ITEMS_REGISTER.registerItem("symbols_chaos_creeper_coin",
            props -> new ViScriptCoinItemServer(props, 4096,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.symbols_chaos_creeper_coin"))),
            () -> new Item.Properties().rarity(Rarity.EPIC));
    /** 绿宝石爬爬币 */
    DeferredItem<Item> EMERALD_CREEPER_COIN = ITEMS_REGISTER.registerItem("emerald_creeper_coin",
            props -> new ViScriptCoinItemServer(props, 256,
                    () -> List.of(Component.translatable("tooltip.item.shipping_box.emerald_creeper_coin"))),
            () -> new Item.Properties().rarity(Rarity.EPIC));

    static void register(IEventBus eventBus) {
        ITEMS_REGISTER.register(eventBus);
    }
}