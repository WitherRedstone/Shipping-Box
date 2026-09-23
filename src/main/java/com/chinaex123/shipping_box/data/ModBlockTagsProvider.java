package com.chinaex123.shipping_box.data;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.init.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, ShippingBox.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.SHIPPING_BOX.getKey())
                .add(ModBlocks.AUTO_SHIPPING_BOX.getKey());

        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.SHIPPING_BOX.getKey())
                .add(ModBlocks.AUTO_SHIPPING_BOX.getKey());
    }
}
