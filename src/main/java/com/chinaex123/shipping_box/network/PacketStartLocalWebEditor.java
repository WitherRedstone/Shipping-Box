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

/** 启动本地 Web 编辑器包 **/
public record PacketStartLocalWebEditor(String token) implements CustomPacketPayload {
    public static final Type<PacketStartLocalWebEditor> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "start_local_web_editor")
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
                    context.player().displayClientMessage(
                            Component.literal("Failed to start local web editor: " + e.getMessage()),
                            false
                    );
                }
                return;
            }

            if (context.player() != null) {
                Component link = Component.literal(url).withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
                context.player().displayClientMessage(Component.literal("Web editor: ").append(link), false);
            }

            try {
                Util.getPlatform().openUri(url);
            } catch (Exception e) {
                return;
            }
        }).exceptionally(e -> null);
    }
}