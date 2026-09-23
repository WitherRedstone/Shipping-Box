package com.chinaex123.shipping_box.data;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.init.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, ShippingBox.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.SHIPPING_BOX.get())
                .add(ModBlocks.AUTO_SHIPPING_BOX.get());

        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.SHIPPING_BOX.get())
                .add(ModBlocks.AUTO_SHIPPING_BOX.get());
    }
}
