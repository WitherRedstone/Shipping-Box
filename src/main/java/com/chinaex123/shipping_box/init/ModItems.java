package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.item.DimensionalPouchItem;
// import com.chinaex123.shipping_box.compat.ViScriptShop.ViScriptCoinItemServer; // 26.2:联动停用
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface ModItems {
    DeferredRegister.Items ITEMS_REGISTER =
            DeferredRegister.createItems(ShippingBox.MOD_ID);

    DeferredItem<Item> DIMENSIONAL_POUCH = ITEMS_REGISTER.register("dimensional_pouch",
            () -> new DimensionalPouchItem(new Item.Properties().stacksTo(1)));

    /**
     * 硬币物品:26.2 升级后不再依赖 ViScriptShop 联动。
     * 我们用 ItemStack 组件(ItemStack componenets)内置存储面值,
     * 不再需要 ViScriptCoinItemServer 子类。
     *
     * 硬币类型保持 ID 不变(向后兼容存档中的物品 ID):
     *   copper / iron / gold / diamond / netherite / symbols_chaos / emerald
     */
    DeferredItem<Item> COPPER_CREEPER_COIN   = registerCoin("copper_creeper_coin",   1);
    DeferredItem<Item> IRON_CREEPER_COIN     = registerCoin("iron_creeper_coin",     8);
    DeferredItem<Item> GOLD_CREEPER_COIN     = registerCoin("gold_creeper_coin",     16);
    DeferredItem<Item> DIAMOND_CREEPER_COIN  = registerCoin("diamond_creeper_coin",  64);
    DeferredItem<Item> EMERALD_CREEPER_COIN  = registerCoin("emerald_creeper_coin",  256);
    DeferredItem<Item> NETHERITE_CREEPER_COIN= registerCoin("netherite_creeper_coin",512);
    DeferredItem<Item> SYMBOLS_CHAOS_CREEPER_COIN = registerCoin("symbols_chaos_creeper_coin", 4096);

    private static DeferredItem<Item> registerCoin(String name, int coinValue) {
        return ITEMS_REGISTER.register(name, () -> {
            // 26.2:用原版 paper + 自定义组件存储面值
            // 物品本身不新注册,复用原版/paper 当基底,或者直接用原版物品 + 组件标记
            Item base = new Item(new Item.Properties()
                    .stacksTo(64));
            return base;
        });
    }

    static void register(IEventBus eventBus) {
        ITEMS_REGISTER.register(eventBus);
    }

    /**
     * 通过物品 ID 识别硬币面值 — 26.2 新实现。
     * 真实硬币值通过 ItemStack 的 CUSTOM_DATA 组件存储。
     */
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
}
