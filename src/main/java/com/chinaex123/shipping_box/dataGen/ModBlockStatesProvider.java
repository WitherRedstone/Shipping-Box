package com.chinaex123.shipping_box.dataGen;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStatesProvider extends BlockStateProvider {
    public ModBlockStatesProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, ShippingBox.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlockWithItem(ModBlocks.SHIPPING_BOX.get(), cubeAll(ModBlocks.SHIPPING_BOX.get()));

    }
}
