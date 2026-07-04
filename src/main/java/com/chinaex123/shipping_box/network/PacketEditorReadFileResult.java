package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.web.WebEditorRequestTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 编辑器读取文件结果数据包（服务端→客户端）
 * <p>
 * 当服务端处理完文件读取请求后，将结果通过此数据包返回给客户端。
 * 客户端收到后通过 {@link WebEditorRequestTracker} 完成对应的 Future，
 * 使 Web 编辑器的 HTTP 请求能够获取到响应。
 */
public record PacketEditorReadFileResult(String requestId, boolean ok, String content, String error) implements CustomPacketPayload {
    public static final Type<PacketEditorReadFileResult> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_read_file_result")
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