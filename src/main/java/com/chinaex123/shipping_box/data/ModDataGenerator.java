package com.chinaex123.shipping_box.data;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = ShippingBox.MOD_ID)
public class ModDataGenerator {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        event.createProvider(((output, lookupProvider) ->
                new LootTableProvider(output, Set.of(), List.of(new LootTableProvider.SubProviderEntry(
                        ModBlockLootTablesProvider::new, LootContextParamSets.BLOCK
                )), lookupProvider)));

        event.createProvider(ModRecipesProvider.Runner::new);
        event.createProvider(ModBlockTagsProvider::new);
        event.createProvider(ModItemTagsProvider::new);
        event.createProvider(ModModelsProvider::new);
    }
}
