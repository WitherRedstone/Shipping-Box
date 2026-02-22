package com.chinaex123.shipping_box.tags;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ModItemTags {

    public static final TagKey<Item> COINS = bind("coins");

    private static TagKey<Item> bind(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, name));
    }
}
