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

/**
 * 方块实体类型注册 — 26.2 适配。
 * <p>
 * 26.2 的 {@link BlockEntityType#isValid} 在 {@code BlockEntity.<init>} 中被调用，
 * 因此必须传入实际的方块引用而非空集合。DeferredRegister 保证方块在方块实体之前注册，
 * 所以在 lambda 中调用 {@code ModBlocks.XXX.get()} 是安全的。
 */
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShippingBox.MOD_ID);

    /** 普通售货箱方块实体类型 */
    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<ShippingBoxBlockEntity>> SHIPPING_BOX =
            BLOCK_ENTITY_TYPES.register("shipping_box",
                    () -> new BlockEntityType<>(ShippingBoxBlockEntity::new,
                            Set.of(ModBlocks.SHIPPING_BOX.get())));

    /** 自动售货箱方块实体类型 */
    @SuppressWarnings("unchecked")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoShippingBoxBlockEntity>> AUTOMATED_SHIPPING_BOX =
            BLOCK_ENTITY_TYPES.register("automated_shipping_box",
                    () -> new BlockEntityType<>(AutoShippingBoxBlockEntity::new,
                            Set.of(ModBlocks.AUTO_SHIPPING_BOX.get())));

    /**
     * 注册方块实体类型到事件总线。
     * <p>
     * 必须在方块注册之后调用，否则 {@code ModBlocks.XXX.get()} 会返回 null。
     *
     * @param bus NeoForge 模组事件总线
     */
    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
