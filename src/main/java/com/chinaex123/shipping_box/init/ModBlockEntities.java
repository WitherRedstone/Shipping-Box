package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.function.Supplier;

public class ModBlockEntities {
    public static DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShippingBox.MOD_ID);

    // 26.2:NPE 问题 — 构造 block entity type 时不能引用 ModBlocks.SHIPPING_BOX.get(),
    //                                 因为此时 block 还未注册(ID 未分配)。
    //                                        先使用空集合,方块实体的可用性通过在 Block 里 override newBlockEntity() 暴露。
    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<ShippingBoxBlockEntity>> SHIPPING_BOX =
            BLOCK_ENTITY_TYPES.register("shipping_box",
                    () -> new BlockEntityType<>(ShippingBoxBlockEntity::new,
                            Collections.emptySet()));
    @SuppressWarnings("unchecked")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoShippingBoxBlockEntity>> AUTOMATED_SHIPPING_BOX =
            BLOCK_ENTITY_TYPES.register("automated_shipping_box", () ->
                    new BlockEntityType<>(AutoShippingBoxBlockEntity::new,
                            Collections.emptySet()));

    public static void register(IEventBus bus){
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
