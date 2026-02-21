package com.chinaex123.shipping_box.block;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    // 创建方块注册器实例
    public static final DeferredRegister.Blocks BLOCK_REGISTER =
            DeferredRegister.createBlocks(ShippingBox.MOD_ID);


    public static final DeferredBlock<Block> SHIPPING_BOX =
            registerBlocks("shipping_box", () -> new ShippingBoxBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.5f, 6.0f).noOcclusion()));

    /**
     * 为指定方块注册对应的物品形式
     *
     * @param <T> 方块类型参数
     * @param name 方块名称，用于物品注册
     * @param block 延迟方块对象
     */
    private static <T extends Block> void registerBlockItems(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    /**
     * 注册方块及其对应的物品形式
     *
     * @param <T> 方块类型参数
     * @param name 方块的注册名称
     * @param block 方块供应器
     * @return 注册的延迟方块对象
     */
    private static <T extends Block> DeferredBlock<T> registerBlocks(String name, Supplier<T> block) {
        DeferredBlock<T> blocks = BLOCK_REGISTER.register(name, block);
        registerBlockItems(name, blocks);
        return blocks;
    }

    // 向指定事件总线注册所有物品
    public static void register(IEventBus eventBus) {
        BLOCK_REGISTER.register(eventBus);
    }
}
