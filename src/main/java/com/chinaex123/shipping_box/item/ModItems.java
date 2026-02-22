package com.chinaex123.shipping_box.item;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.tooltip.TooltipItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS_REGISTER =
            DeferredRegister.createItems(ShippingBox.MOD_ID);

    public static final DeferredItem<Item> COPPER_CREEPER_COIN =
            ITEMS_REGISTER.register("copper_creeper_coin",
                    () -> new TooltipItems(new Item.Properties().rarity(Rarity.COMMON),
                            () -> List.of(
                                    Component.translatable("tooltip.item.shipping_box.copper_creeper_coin"),
                                    Component.translatable("tooltip.item.shipping_box.coin.1"),
                                    Component.translatable("tooltip.item.shipping_box.coin.2"),
                                    Component.translatable("tooltip.item.shipping_box.coin.3")
                            )));

    // 铁爬爬币
    public static final DeferredItem<Item> IRON_CREEPER_COIN =
            ITEMS_REGISTER.register("iron_creeper_coin",
                    () -> new TooltipItems(new Item.Properties().rarity(Rarity.COMMON),
                            () -> List.of(
                                    Component.translatable("tooltip.item.shipping_box.iron_creeper_coin"),
                                    Component.translatable("tooltip.item.shipping_box.coin.1"),
                                    Component.translatable("tooltip.item.shipping_box.coin.2"),
                                    Component.translatable("tooltip.item.shipping_box.coin.3")
                            )));

    // 金爬爬币
    public static final DeferredItem<Item> GOLD_CREEPER_COIN =
            ITEMS_REGISTER.register("gold_creeper_coin",
                    () -> new TooltipItems(new Item.Properties().rarity(Rarity.UNCOMMON),
                            () -> List.of(
                                    Component.translatable("tooltip.item.shipping_box.gold_creeper_coin"),
                                    Component.translatable("tooltip.item.shipping_box.coin.1"),
                                    Component.translatable("tooltip.item.shipping_box.coin.2"),
                                    Component.translatable("tooltip.item.shipping_box.coin.3")
                            )));

    // 钻石爬爬币
    public static final DeferredItem<Item> DIAMOND_CREEPER_COIN =
            ITEMS_REGISTER.register("diamond_creeper_coin",
                    () -> new TooltipItems(new Item.Properties().rarity(Rarity.RARE),
                            () -> List.of(
                                    Component.translatable("tooltip.item.shipping_box.diamond_creeper_coin"),
                                    Component.translatable("tooltip.item.shipping_box.coin.1"),
                                    Component.translatable("tooltip.item.shipping_box.coin.2"),
                                    Component.translatable("tooltip.item.shipping_box.coin.3")
                            )));

    // 下界合金爬爬币
    public static final DeferredItem<Item> NETHERITE_CREEPER_COIN =
            ITEMS_REGISTER.register("netherite_creeper_coin",
                    () -> new TooltipItems(new Item.Properties().rarity(Rarity.RARE),
                            () -> List.of(
                                    Component.translatable("tooltip.item.shipping_box.netherite_creeper_coin"),
                                    Component.translatable("tooltip.item.shipping_box.coin.1"),
                                    Component.translatable("tooltip.item.shipping_box.coin.2"),
                                    Component.translatable("tooltip.item.shipping_box.coin.3")
                            )));

    // 混沌立方爬爬币
    public static final DeferredItem<Item> SYMBOLS_CHAOS_CREEPER_COIN =
            ITEMS_REGISTER.register("symbols_chaos_creeper_coin",
                    () -> new TooltipItems(new Item.Properties().rarity(Rarity.EPIC),
                            () -> List.of(
                                    Component.translatable("tooltip.item.shipping_box.symbols_chaos_creeper_coin"),
                                    Component.translatable("tooltip.item.shipping_box.coin.1"),
                                    Component.translatable("tooltip.item.shipping_box.coin.2"),
                                    Component.translatable("tooltip.item.shipping_box.coin.3")
                            )));

    // 绿宝石爬爬币
    public static final DeferredItem<Item> EMERALD_CREEPER_COIN =
            ITEMS_REGISTER.register("emerald_creeper_coin",
                    () -> new TooltipItems(new Item.Properties().rarity(Rarity.EPIC),
                            () -> List.of(
                                    Component.translatable("tooltip.item.shipping_box.emerald_creeper_coin"),
                                    Component.translatable("tooltip.item.shipping_box.coin.1"),
                                    Component.translatable("tooltip.item.shipping_box.coin.2"),
                                    Component.translatable("tooltip.item.shipping_box.coin.3")
                            )));


    // 向指定事件总线注册所有物品
    public static void register(IEventBus eventBus) {
        ITEMS_REGISTER.register(eventBus);
    }
}
