package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.AutoShippingBoxBlock;
import com.chinaex123.shipping_box.block.ShippingBoxBlock;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCK_REGISTER = DeferredRegister.createBlocks(ShippingBox.MOD_ID);

    /** 售货箱 */
    public static final DeferredBlock<ShippingBoxBlock> SHIPPING_BOX =
            registerBlocks("shipping_box",
                    props -> new ShippingBoxBlock(props
                            .mapColor(MapColor.WOOD)
                            .sound(SoundType.WOOD)
                            .strength(2.5f, 6.0f)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
                    ),
                    Rarity.COMMON);

    /** 自动售货箱 */
    public static final DeferredBlock<AutoShippingBoxBlock> AUTO_SHIPPING_BOX =
            registerBlocks("auto_shipping_box",
                    props -> new AutoShippingBoxBlock(props
                            .mapColor(MapColor.WOOD)
                            .sound(SoundType.WOOD)
                            .strength(2.5f, 6.0f)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
                    ),
                    Rarity.COMMON);

    /**
     * 注册方块及其对应的物品形式（带稀有度）
     *
     * @param <T> 方块类型参数
     * @param name 方块的注册名称
     * @param block 方块工厂函数，接收 Properties 并返回方块实例
     * @param rarity 物品稀有度
     * @return 注册的延迟方块对象
     */
    private static <T extends Block> DeferredBlock<T> registerBlocks(
            String name,
            Function<BlockBehaviour.Properties, T> block,
            Rarity rarity) {
        DeferredBlock<T> blocks = BLOCK_REGISTER.registerBlock(name, block);
        registerBlockItems(blocks, rarity);
        return blocks;
    }

    /**
     * 为指定方块注册对应的物品形式
     *
     * @param <T> 方块类型参数
     * @param block 延迟方块对象
     * @param rarity 物品稀有度
     */
    private static <T extends Block> void registerBlockItems(DeferredBlock<T> block, Rarity rarity) {
        ModItems.ITEMS_REGISTER.registerSimpleBlockItem(
                block,
                props -> props.rarity(rarity)
        );
    }

    public static void register(IEventBus eventBus) {
        BLOCK_REGISTER.register(eventBus);
    }
}