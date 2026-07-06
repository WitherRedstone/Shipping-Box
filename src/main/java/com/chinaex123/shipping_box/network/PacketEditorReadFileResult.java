package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.web.WebEditorRequestTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 编辑器读取文件结果包 **/
public record PacketEditorReadFileResult(String requestId, boolean ok, String content, String error) implements CustomPacketPayload {
    public static final Type<PacketEditorReadFileResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_read_file_result")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketEditorReadFileResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFileResult::requestId,
            ByteBufCodecs.BOOL, PacketEditorReadFileResult::ok,
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFileResult::content,
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFileResult::error,
            PacketEditorReadFileResult::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketEditorReadFileResult packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            WebEditorRequestTracker.complete(
                    packet.requestId(),
                    new WebEditorRequestTracker.Response(packet.ok(), packet.content(), packet.error())
            );
        }).exceptionally(e -> null);
    }
}