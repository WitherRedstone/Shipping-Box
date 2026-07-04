package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 显示成功消息的数据包（服务端→客户端）
 * <p>
 * 当兑换成功后，服务端发送此数据包通知客户端显示成功提示消息。
 * 该数据包不含任何附加数据，仅作为触发信号使用。
 * 客户端收到后会在聊天栏显示一条本地化的兑换成功消息。
 */
public record PacketShowSuccessMessage() implements CustomPacketPayload {
    public static final Type<PacketShowSuccessMessage> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "show_success_message")
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
                // 26.2:sendSystemMessage 只接受一个 Component 参数
                context.player().sendSystemMessage(
                        Component.translatable("message.shipping_box.exchange_success")
                );
            }
        }).exceptionally(e -> null);
    }
}