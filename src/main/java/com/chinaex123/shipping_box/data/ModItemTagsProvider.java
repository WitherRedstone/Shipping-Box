package com.chinaex123.shipping_box.data;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.init.ModItemTags;
import com.chinaex123.shipping_box.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, ShippingBox.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        tag(ModItemTags.COINS)
                .add(ModItems.COPPER_CREEPER_COIN.getKey())
                .add(ModItems.IRON_CREEPER_COIN.getKey())
                .add(ModItems.GOLD_CREEPER_COIN.getKey())
                .add(ModItems.DIAMOND_CREEPER_COIN.getKey())
                .add(ModItems.NETHERITE_CREEPER_COIN.getKey())
                .add(ModItems.EMERALD_CREEPER_COIN.getKey())
                .add(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.getKey());
    }
}
