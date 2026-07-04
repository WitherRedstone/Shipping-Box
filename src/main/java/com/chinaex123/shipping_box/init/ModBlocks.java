package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.AutoShippingBoxBlock;
import com.chinaex123.shipping_box.block.ShippingBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册类
 * <p>
 * 负责注册售货箱模组的所有方块到游戏中。
 * 使用 NeoForge 的 DeferredRegister 系统进行延迟注册，
 * 确保方块在正确的初始化阶段被注册。
 * <p>
 * 当前注册的方块：
 * <ul>
 *   <li>普通售货箱（shipping_box）- 所有玩家可用的基础售货箱</li>
 *   <li>自动售货箱（auto_shipping_box）- 绑定玩家的高级自动售货箱</li>
 * </ul>
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCK_REGISTER = DeferredRegister.createBlocks(ShippingBox.MOD_ID);

    // 26.2:使用 registerBlock(Function<Properties, B>) API,properties 会被自动注入 id
    public static final DeferredBlock<ShippingBoxBlock> SHIPPING_BOX =
            BLOCK_REGISTER.registerBlock("shipping_box",
                    props -> new ShippingBoxBlock(props
                            .mapColor(MapColor.WOOD)
                            .sound(SoundType.WOOD)
                            .strength(2.5f, 6.0f)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
                    ));
    public static final DeferredBlock<AutoShippingBoxBlock> AUTO_SHIPPING_BOX =
            BLOCK_REGISTER.registerBlock("auto_shipping_box",
                    props -> new AutoShippingBoxBlock(props
                            .mapColor(MapColor.WOOD)
                            .sound(SoundType.WOOD)
                            .strength(2.5f, 6.0f)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
                    ));

    public static void register(IEventBus eventBus) {
        BLOCK_REGISTER.register(eventBus);
    }
}
