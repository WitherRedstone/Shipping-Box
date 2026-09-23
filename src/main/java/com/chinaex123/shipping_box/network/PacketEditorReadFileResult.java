package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.web.WebEditorRequestTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 编辑器读取文件结果数据包（服务端→客户端）。
 * <p>
 * 当服务端处理完文件读取请求后，将结果通过此数据包返回给客户端。
 * 客户端收到后通过 WebEditorRequestTracker 完成对应的 Future，
 * 使 Web 编辑器的 HTTP 请求能够获取到响应。
 */
public record PacketEditorReadFileResult(String requestId, boolean ok, String content, String error) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketEditorReadFileResult> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_read_file_result")
    );

    /** 网络包编解码器，按字段顺序组合请求 ID、成功标志、内容与错误信息 */
    public static final StreamCodec<FriendlyByteBuf, PacketEditorReadFileResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFileResult::requestId,
            ByteBufCodecs.BOOL, PacketEditorReadFileResult::ok,
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFileResult::content,
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFileResult::error,
            PacketEditorReadFileResult::new
    );

    /**
     * 获取网络包类型。
     *
     * @return 网络包类型标识
     */
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理编辑器读取文件结果。
     * <p>
     * 在主线程中根据请求 ID 将结果交由请求追踪器完成，
     * 以唤醒等待该响应的异步请求。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketEditorReadFileResult packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            WebEditorRequestTracker.complete(
                    packet.requestId(),
                    new WebEditorRequestTracker.Response(packet.ok(), packet.content(), packet.error())
            );
        }).exceptionally(e -> null);
    }
}