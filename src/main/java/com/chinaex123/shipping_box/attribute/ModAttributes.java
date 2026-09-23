package com.chinaex123.shipping_box.attribute;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组属性注册类。
 * <p>
 * 负责注册本模组自定义的属性，并将其附加到玩家实体上。
 * 通过事件订阅自动注册到游戏事件总线。
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID)
public class ModAttributes {

    /** 属性延迟注册器 */
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, ShippingBox.MOD_ID);

    /** 出售价格加成属性，以百分比形式表示，取值范围 -10.0 至 10.0 */
    public static final Holder<Attribute> SELLING_PRICE_BOOST = ATTRIBUTES.register(
            "selling_price_boost",
            () -> new PercentageAttribute(
                    "attribute.shipping_box.selling_price_boost",
                    0.0,
                    -10.0,
                    10.0
            ).setSyncable(true)
    );

    /**
     * 将自定义属性附加到玩家实体。
     * <p>
     * 通过实体属性修改事件，将出售价格加成属性添加到玩家类型上。
     *
     * @param event 实体属性修改事件
     */
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void addAttributes(EntityAttributeModificationEvent event) {
        EntityType<? extends LivingEntity> playerType =
                (EntityType<? extends LivingEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "player"));
        event.add(playerType, SELLING_PRICE_BOOST);
    }
}