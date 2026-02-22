package com.chinaex123.shipping_box.dataGen;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.item.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelsProvider extends ItemModelProvider {
    public ModItemModelsProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ShippingBox.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.COPPER_CREEPER_COIN.get());
        basicItem(ModItems.IRON_CREEPER_COIN.get());
        basicItem(ModItems.GOLD_CREEPER_COIN.get());
        basicItem(ModItems.DIAMOND_CREEPER_COIN.get());
        basicItem(ModItems.NETHERITE_CREEPER_COIN.get());
        basicItem(ModItems.EMERALD_CREEPER_COIN.get());
        basicItem(ModItems.SYMBOLS_CHAOS_CREEPER_COIN.get());
    }
}
