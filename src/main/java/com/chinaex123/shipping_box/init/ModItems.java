package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.item.DimensionalPouchItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

 public interface ModItems {
    DeferredRegister.Items ITEMS_REGISTER =
            DeferredRegister.createItems(ShippingBox.MOD_ID);

    // 26.2:使用 registerItem API,properties 会被自动注入 id
    DeferredItem<Item> DIMENSIONAL_POUCH = ITEMS_REGISTER.registerItem("dimensional_pouch",
            props -> new DimensionalPouchItem(props.stacksTo(1)),
            Item.Properties::new);

    // 26.2:基础 Item 用 registerSimpleItem
    DeferredItem<Item> COPPER_CREEPER_COIN = ITEMS_REGISTER.registerSimpleItem("copper_creeper_coin", Item.Properties::new);
    DeferredItem<Item> IRON_CREEPER_COIN = ITEMS_REGISTER.registerSimpleItem("iron_creeper_coin", Item.Properties::new);
    DeferredItem<Item> GOLD_CREEPER_COIN = ITEMS_REGISTER.registerSimpleItem("gold_creeper_coin", Item.Properties::new);
    DeferredItem<Item> DIAMOND_CREEPER_COIN = ITEMS_REGISTER.registerSimpleItem("diamond_creeper_coin", Item.Properties::new);
    DeferredItem<Item> EMERALD_CREEPER_COIN = ITEMS_REGISTER.registerSimpleItem("emerald_creeper_coin", Item.Properties::new);
    DeferredItem<Item> NETHERITE_CREEPER_COIN = ITEMS_REGISTER.registerSimpleItem("netherite_creeper_coin", Item.Properties::new);
    DeferredItem<Item> SYMBOLS_CHAOS_CREEPER_COIN = ITEMS_REGISTER.registerSimpleItem("symbols_chaos_creeper_coin", Item.Properties::new);

    static void register(IEventBus eventBus) {
        // 26.2:block item 通过 registerSimpleBlockItem 自动生成,自动继承 block id
        ITEMS_REGISTER.registerSimpleBlockItem("shipping_box", () -> ModBlocks.SHIPPING_BOX.get());
        ITEMS_REGISTER.registerSimpleBlockItem("auto_shipping_box", () -> ModBlocks.AUTO_SHIPPING_BOX.get());
        ITEMS_REGISTER.register(eventBus);
    }

    static int getCoinValue(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (!"shipping_box".equals(id.getNamespace())) return 0;
        return switch (id.getPath()) {
            case "copper_creeper_coin" -> 1;
            case "iron_creeper_coin" -> 8;
            case "gold_creeper_coin" -> 16;
            case "diamond_creeper_coin" -> 64;
            case "emerald_creeper_coin" -> 256;
            case "netherite_creeper_coin" -> 512;
            case "symbols_chaos_creeper_coin" -> 4096;
            default -> 0;
        };
    }

    /** 扫描容器中硬币总价值(工具方法,26.2 仍适用) */
    static int scanContainerCoins(net.minecraft.world.Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            total += getCoinValue(s.getItem()) * s.getCount();
        }
        return total;
    }
}
