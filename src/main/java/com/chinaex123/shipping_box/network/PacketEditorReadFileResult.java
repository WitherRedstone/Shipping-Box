package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.web.WebEditorRequestTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 编辑器读取文件结果包。
 * <p>
 * 作为服务端对 {@link PacketEditorReadFile} 请求的响应，
 * 携带请求 ID、是否成功、文件内容与错误信息，
 * 客户端收到后交由请求追踪器完成对应的异步请求。
 */
public record PacketEditorReadFileResult(String requestId, boolean ok, String content, String error) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketEditorReadFileResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_read_file_result")
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
    public @NotNull Type<? extends CustomPacketPayload> type() {
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