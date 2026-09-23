package com.chinaex123.shipping_box.data;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.init.ModBlocks;
import com.chinaex123.shipping_box.init.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;

public class ModModelsProvider extends ModelProvider {
    public ModModelsProvider(PackOutput output) {
        super(output, ShippingBox.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(ModItems.DIMENSIONAL_POUCH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.COPPER_CREEPER_COIN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.IRON_CREEPER_COIN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GOLD_CREEPER_COIN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DIAMOND_CREEPER_COIN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NETHERITE_CREEPER_COIN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EMERALD_CREEPER_COIN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get(), ModelTemplates.FLAT_ITEM);


        blockModels.createTrivialCube(ModBlocks.SHIPPING_BOX.get());
        blockModels.createTrivialCube(ModBlocks.AUTO_SHIPPING_BOX.get());
    }
}
