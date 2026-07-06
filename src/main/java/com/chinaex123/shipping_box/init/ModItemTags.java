package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public interface ModItemTags {

    TagKey<Item> COINS = bind("coins");

    private static TagKey<Item> bind(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, name));
    }
}
