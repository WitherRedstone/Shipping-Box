package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.web.WebEditorRequestTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 编辑器保存规则结果包 **/
public record PacketEditorSaveRulesResult(String requestId, boolean ok, String savedPath, String error) implements CustomPacketPayload {
    public static final Type<PacketEditorSaveRulesResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_save_rules_result")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketEditorSaveRulesResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketEditorSaveRulesResult::requestId,
            ByteBufCodecs.BOOL, PacketEditorSaveRulesResult::ok,
            ByteBufCodecs.STRING_UTF8, PacketEditorSaveRulesResult::savedPath,
            ByteBufCodecs.STRING_UTF8, PacketEditorSaveRulesResult::error,
            PacketEditorSaveRulesResult::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

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