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
 * 编辑器保存规则结果数据包（服务端→客户端）。
 * <p>
 * 当服务端处理完规则保存请求后，将保存结果通过此数据包返回给客户端。
 * 客户端收到后通过 WebEditorRequestTracker 完成对应的 Future，
 * 使 Web 编辑器的 HTTP 请求能够获取到保存操作的结果和状态。
 */
public record PacketEditorSaveRulesResult(String requestId, boolean ok, String savedPath, String error) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketEditorSaveRulesResult> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_save_rules_result")
    );

    /** 网络包编解码器，按字段顺序组合请求 ID、成功标志、保存路径与错误信息 */
    public static final StreamCodec<FriendlyByteBuf, PacketEditorSaveRulesResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketEditorSaveRulesResult::requestId,
            ByteBufCodecs.BOOL, PacketEditorSaveRulesResult::ok,
            ByteBufCodecs.STRING_UTF8, PacketEditorSaveRulesResult::savedPath,
            ByteBufCodecs.STRING_UTF8, PacketEditorSaveRulesResult::error,
            PacketEditorSaveRulesResult::new
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
     * 处理编辑器保存规则结果。
     * <p>
     * 在主线程中根据请求 ID 将结果交由请求追踪器完成，
     * 以唤醒等待该响应的异步请求。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketEditorSaveRulesResult packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            String payload = packet.savedPath();
            WebEditorRequestTracker.complete(
                    packet.requestId(),
                    new WebEditorRequestTracker.Response(packet.ok(), payload, packet.error())
            );
        }).exceptionally(e -> null);
    }
}