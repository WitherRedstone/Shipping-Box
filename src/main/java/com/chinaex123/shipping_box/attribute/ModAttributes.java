package com.chinaex123.shipping_box.attribute;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义属性注册类
 * <p>
 * 负责注册售货箱模组使用的自定义属性系统。
 * 使用 NeoForge 的 DeferredRegister 系统进行延迟注册，
 * 并通过 {@link EntityAttributeModificationEvent} 将属性添加到玩家实体上。
 * <p>
 * 当前注册的属性：
 * <ul>
 *   <li>出售价格加成（selling_price_boost）- 影响物品兑换时的产出加成百分比</li>
 * </ul>
 */
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
        // 26.2: EntityType.PLAYER 字段已移除，改为从注册表获取
        EntityType<?> playerType = BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "player"));
        event.add((EntityType<net.minecraft.world.entity.LivingEntity>) playerType, SELLING_PRICE_BOOST);
    }
}
