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

/** 启动本地 Web 编辑器包 — 26.2 重构。 */
public record PacketStartLocalWebEditor(String token) implements CustomPacketPayload {
    public static final Type<PacketStartLocalWebEditor> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "start_local_web_editor")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketStartLocalWebEditor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketStartLocalWebEditor::token,
            PacketStartLocalWebEditor::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketStartLocalWebEditor packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            String url;
            try {
                url = WebEditorLocalServer.start(packet.token());
            } catch (Throwable e) {
                if (context.player() != null) {
                    context.player().sendSystemMessage(
                            Component.literal("Failed to start local web editor: " + e.getMessage())
                    );
                }
                return;
            }

            if (context.player() != null) {
                // 26.2:ClickEvent 改为 codec 驱动无法直接构造。
                // Minecraft client 会自动识别 http(s):// 文本为可点击链接。
                MutableComponent msg = Component.literal("Web editor: ")
                        .append(Component.literal(url));
                context.player().sendSystemMessage(msg);
            }

            // 26.2:Util 不再存在。直接用 GLFW 打开 URL。
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
