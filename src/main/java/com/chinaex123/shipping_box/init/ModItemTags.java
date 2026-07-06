package com.chinaex123.shipping_box.init;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 物品标签注册接口
 * <p>
 * 定义售货箱模组使用的物品标签（Tag），用于对物品进行分类和分组。
 * 标签可用于配方输入、兑换规则匹配等场景，提供了比单个物品 ID
 * 更灵活的物品匹配方式。
 */
public interface ModItemTags {

    /** 硬币类物品标签，标记所有苦力怕硬币变种 */
    TagKey<Item> COINS = bind("coins");

    private static TagKey<Item> bind(String name) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, name));
    }
}
