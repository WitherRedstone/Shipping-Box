package com.chinaex123.shipping_box;

import com.chinaex123.shipping_box.block.ModBlocks;
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

                    })
                    .build());

    // 注册到NeoForge事件总线里
    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
