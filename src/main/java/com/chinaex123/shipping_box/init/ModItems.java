package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.item.CreeperCoinItem;
import com.chinaex123.shipping_box.item.DimensionalPouchItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * 物品注册接口
 * <p>
 * 负责注册售货箱模组的所有物品到游戏中。
 * 使用 NeoForge 的 DeferredRegister 系统进行延迟注册。
 * 同时包含硬币工具方法，用于扫描容器中的硬币总价值。
 * <p>
 * 注册的物品包括：
 * <ul>
 *   <li>次元钱袋（dimensional_pouch）</li>
 *   <li>7种面值的苦力怕硬币（铜/铁/金/钻石/绿宝石/下界合金/混沌符印）</li>
 *   <li>方块的物品形式（通过 registerSimpleBlockItem 自动生成）</li>
 * </ul>
 */
public interface ModItems {
    DeferredRegister.Items ITEMS_REGISTER =
            DeferredRegister.createItems(ShippingBox.MOD_ID);

    // 26.2:使用 registerItem API,properties 会被自动注入 id
    DeferredItem<Item> DIMENSIONAL_POUCH = ITEMS_REGISTER.registerItem("dimensional_pouch",
            props -> new DimensionalPouchItem(props.stacksTo(1)),
            Item.Properties::new);

    DeferredItem<Item> COPPER_CREEPER_COIN = ITEMS_REGISTER.registerItem("copper_creeper_coin",
            props -> coin(props, 1, "tooltip.item.shipping_box.copper_creeper_coin"),
            Item.Properties::new);
    DeferredItem<Item> IRON_CREEPER_COIN = ITEMS_REGISTER.registerItem("iron_creeper_coin",
            props -> coin(props, 8, "tooltip.item.shipping_box.iron_creeper_coin"),
            Item.Properties::new);
    DeferredItem<Item> GOLD_CREEPER_COIN = ITEMS_REGISTER.registerItem("gold_creeper_coin",
            props -> coin(props, 16, "tooltip.item.shipping_box.gold_creeper_coin"),
            Item.Properties::new);
    DeferredItem<Item> DIAMOND_CREEPER_COIN = ITEMS_REGISTER.registerItem("diamond_creeper_coin",
            props -> coin(props, 64, "tooltip.item.shipping_box.diamond_creeper_coin"),
            Item.Properties::new);
    DeferredItem<Item> EMERALD_CREEPER_COIN = ITEMS_REGISTER.registerItem("emerald_creeper_coin",
            props -> coin(props, 256, "tooltip.item.shipping_box.emerald_creeper_coin"),
            Item.Properties::new);
    DeferredItem<Item> NETHERITE_CREEPER_COIN = ITEMS_REGISTER.registerItem("netherite_creeper_coin",
            props -> coin(props, 512, "tooltip.item.shipping_box.netherite_creeper_coin"),
            Item.Properties::new);
    DeferredItem<Item> SYMBOLS_CHAOS_CREEPER_COIN = ITEMS_REGISTER.registerItem("symbols_chaos_creeper_coin",
            props -> coin(props, 4096, "tooltip.item.shipping_box.symbols_chaos_creeper_coin"),
            Item.Properties::new);

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

    private static CreeperCoinItem coin(Item.Properties properties, int value, String tooltipKey) {
        return new CreeperCoinItem(properties, value, () -> List.of(
                Component.translatable(tooltipKey),
                Component.translatable("tooltip.item.shipping_box.virtual_currency.right_click"),
                Component.translatable("tooltip.item.shipping_box.virtual_currency.sneak_click")
        ));
    }
}
