package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShippingBox.MOD_ID);

    public static final Supplier<BlockEntityType<ShippingBoxBlockEntity>> SHIPPING_BOX_BE =
            BLOCK_ENTITY_TYPES.register("shipping_box",
                    () -> BlockEntityType.Builder.of(ShippingBoxBlockEntity::new,
                            ModBlocks.SHIPPING_BOX.get()).build(null));

    public static void register(IEventBus bus){
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
