package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.web.WebEditorLocalServer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

/**
 * 启动本地 Web 编辑器数据包（服务端→客户端）。
 * <p>
 * 当玩家执行 {@code /shipping_box web} 命令时，服务端发送此数据包
 * 通知客户端在本地启动 Web 编辑器 HTTP 服务器。
 * 客户端收到后会在本地绑定端口并打开默认浏览器访问编辑器界面。
 * 使用安全令牌验证请求，防止未授权访问。
 */
public record PacketStartLocalWebEditor(String token) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketStartLocalWebEditor> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "start_local_web_editor")
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
     * 启动失败时向玩家显示错误消息，成功后向玩家发送访问链接，
     * 并根据操作系统调用系统命令以默认浏览器打开该链接。
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
                context.player();
                context.player().sendSystemMessage(
                        Component.translatable(
                                "message.shipping_box.web_editor.start_failed",
                                e.getMessage() == null ? "" : e.getMessage()
                        )
                );
                return;
            }

            context.player();
            MutableComponent msg = Component.translatable("message.shipping_box.web_editor.url_prefix")
                    .append(Component.literal(url));
            context.player().sendSystemMessage(msg);

            try {
                GLFW.glfwInit();
                GLFW.glfwDefaultWindowHints();
                // 直接用系统浏览器
                String os = System.getProperty("os.name", "").toLowerCase();
                Runtime rt = Runtime.getRuntime();
                if (os.contains("mac") || os.contains("darwin")) {
                    rt.exec("open " + url);
                } else if (os.contains("win")) {
                    rt.exec("rundll32 url.dll,FileProtocolHandler " + url);
                } else {
                    rt.exec("xdg-open " + url);
                }
            } catch (Exception e) {
                // ignore
            }
        }).exceptionally(e -> null);
    }
}