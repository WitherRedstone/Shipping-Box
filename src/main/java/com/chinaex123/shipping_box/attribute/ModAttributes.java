package com.chinaex123.shipping_box.attribute;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = ShippingBox.MOD_ID)
public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, ShippingBox.MOD_ID);

    // 出售价格加成
    public static final Holder<Attribute> SELLING_PRICE_BOOST = ATTRIBUTES.register(
            "selling_price_boost",
            () -> new PercentageAttribute(
                    "attribute.shipping_box.selling_price_boost",
                    0.0,    // 默认值
                    -10.0,    // 最小值
                    10.0    // 最大值
            ).setSyncable(true)
    );

    @SubscribeEvent
    public static void addAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, SELLING_PRICE_BOOST);
    }
}
