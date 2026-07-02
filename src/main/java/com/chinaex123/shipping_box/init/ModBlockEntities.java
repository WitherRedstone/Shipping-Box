package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

public class ModBlockEntities {
    public static DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShippingBox.MOD_ID);

    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<ShippingBoxBlockEntity>> SHIPPING_BOX =
            BLOCK_ENTITY_TYPES.register("shipping_box",
                    () -> new BlockEntityType<>(ShippingBoxBlockEntity::new,
                            Set.of(ModBlocks.SHIPPING_BOX.get())));
    @SuppressWarnings("unchecked")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoShippingBoxBlockEntity>> AUTOMATED_SHIPPING_BOX =
            BLOCK_ENTITY_TYPES.register("automated_shipping_box", () ->
                    new BlockEntityType<>(AutoShippingBoxBlockEntity::new,
                            Set.of(ModBlocks.AUTO_SHIPPING_BOX.get())));

    public static void register(IEventBus bus){
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
