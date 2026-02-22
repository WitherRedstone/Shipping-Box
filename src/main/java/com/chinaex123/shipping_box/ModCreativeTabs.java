package com.chinaex123.shipping_box;

import com.chinaex123.shipping_box.block.ModBlocks;
import com.chinaex123.shipping_box.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ShippingBox.MOD_ID);

    public static final Supplier<CreativeModeTab> SHIPPING_BOX_TAB =
            CREATIVE_MODE_TAB.register("shipping_box_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModBlocks.SHIPPING_BOX.get()))
                    .title(Component.translatable("itemGroup.shipping_box_tab"))
                    .displayItems((parameters, output) -> {

                        output.accept(ModBlocks.SHIPPING_BOX.get());
                        output.accept(ModItems.COPPER_CREEPER_COIN.get());
                        output.accept(ModItems.IRON_CREEPER_COIN.get());
                        output.accept(ModItems.GOLD_CREEPER_COIN.get());
                        output.accept(ModItems.DIAMOND_CREEPER_COIN.get());
                        output.accept(ModItems.NETHERITE_CREEPER_COIN.get());
                        output.accept(ModItems.EMERALD_CREEPER_COIN.get());
                        output.accept(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get());

                    })
                    .build());

    // 注册到NeoForge事件总线里
    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
