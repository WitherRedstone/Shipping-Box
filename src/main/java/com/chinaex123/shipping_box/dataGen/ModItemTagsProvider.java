package com.chinaex123.shipping_box.dataGen;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.item.ModItems;
import com.chinaex123.shipping_box.tags.ModItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, ShippingBox.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        tag(ModItemTags.COINS)
                .add(ModItems.COPPER_CREEPER_COIN.get())
                .add(ModItems.IRON_CREEPER_COIN.get())
                .add(ModItems.GOLD_CREEPER_COIN.get())
                .add(ModItems.DIAMOND_CREEPER_COIN.get())
                .add(ModItems.NETHERITE_CREEPER_COIN.get())
                .add(ModItems.EMERALD_CREEPER_COIN.get())
                .add(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get());
    }
}
