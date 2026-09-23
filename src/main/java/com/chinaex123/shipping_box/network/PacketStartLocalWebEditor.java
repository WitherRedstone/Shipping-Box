package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.web.WebEditorLocalServer;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 启动本地 Web 编辑器包。
 * <p>
 * 服务端在玩家执行打开编辑器命令后发送此数据包，
 * 客户端收到后以访问令牌启动本地 Web 编辑器服务，
 * 在聊天栏显示可点击的访问链接并尝试用系统浏览器打开。
 */
public record PacketStartLocalWebEditor(String token) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketStartLocalWebEditor> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "start_local_web_editor")
    );

    /** 网络包编解码器，仅携带访问令牌字段 */
    public static final StreamCodec<FriendlyByteBuf, PacketStartLocalWebEditor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketStartLocalWebEditor::token,
            PacketStartLocalWebEditor::new
    );

    /**
     * 获取网络包类型。
     *
     * @return 网络包类型标识
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理启动本地 Web 编辑器请求。
     * <p>
     * 在主线程中使用访问令牌启动本地 Web 编辑器服务；
     * 启动失败时向玩家显示错误消息，成功后显示可点击的访问链接，
     * 并尝试用系统默认浏览器打开该链接。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketStartLocalWebEditor packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            String url;
            try {
                url = WebEditorLocalServer.start(packet.token());
            } catch (Throwable e) {
                context.player().displayClientMessage(
                        Component.translatable(
                                "message.shipping_box.web_editor.start_failed",
                                e.getMessage() == null ? "" : e.getMessage()
                        ),
                        false
                );
                return;
            }

            // 构造可点击链接并在聊天栏显示
            Component link = Component.literal(url)
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
            context.player().displayClientMessage(
                    Component.translatable("message.shipping_box.web_editor.url_prefix").append(link),
                    false
            );

            // 尝试用系统默认浏览器打开链接
            try {
                Util.getPlatform().openUri(url);
            } catch (Exception e) {
                return;
            }
        }).exceptionally(e -> null);
    }
}