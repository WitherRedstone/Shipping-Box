package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 显示成功消息的数据包记录类。
 * <p>
 * 服务端在兑换成功后向客户端发送此数据包，
 * 由客户端在动作栏显示兑换成功提示消息。
 */
public record PacketShowSuccessMessage() implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketShowSuccessMessage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "show_success_message")
    );

    /** 无字段网络包的编解码器 */
    public static final StreamCodec<FriendlyByteBuf, PacketShowSuccessMessage> STREAM_CODEC =
            StreamCodec.unit(new PacketShowSuccessMessage());

    /**
     * 获取网络包类型。
     *
     * @return 网络包类型标识
     */
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理显示成功消息请求。
     * <p>
     * 在主线程中向玩家动作栏显示兑换成功提示消息。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketShowSuccessMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端显示成功消息
            context.player();
            context.player().displayClientMessage(
                    Component.translatable("message.shipping_box.exchange_success"),
                    true
            );
        }).exceptionally(e -> null);
    }
}