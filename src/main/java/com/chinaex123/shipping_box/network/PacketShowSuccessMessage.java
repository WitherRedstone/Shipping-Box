package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 显示成功消息的数据包记录类 **/
public record PacketShowSuccessMessage() implements CustomPacketPayload {
    public static final Type<PacketShowSuccessMessage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "show_success_message")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketShowSuccessMessage> STREAM_CODEC =
            StreamCodec.unit(new PacketShowSuccessMessage());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketShowSuccessMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端显示成功消息
            if (context.player() != null) {
                context.player().displayClientMessage(
                        Component.translatable("message.shipping_box.exchange_success"),
                        true // 在行动栏显示
                );
            }
        }).exceptionally(e -> null);
    }
}