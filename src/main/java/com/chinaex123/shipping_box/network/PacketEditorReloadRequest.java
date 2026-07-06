package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 编辑器重载请求数据包（客户端→服务端）
 * <p>
 * Web 编辑器通过此数据包请求重载服务端的数据包和配置。
 * 需要发送端处于创造模式才能执行。
 * 服务端收到后会执行 {@code /reload} 命令使最新配置生效。
 */
public record PacketEditorReloadRequest() implements CustomPacketPayload {

    public static final Type<PacketEditorReloadRequest> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_reload_request")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketEditorReloadRequest> STREAM_CODEC =
            StreamCodec.unit(new PacketEditorReloadRequest());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketEditorReloadRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 26.2:server-side handle,player access via level()
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            ServerPlayerGameMode gm = serverPlayer.gameMode;
            // 检查是否创造模式(OP 等级 ≥ 2 对应 isCreative)
            if (gm == null || !gm.isCreative()) {
                context.player().sendSystemMessage(
                        Component.literal("§cPermission denied: need creative mode / OP"));
                return;
            }

            // 执行 reload
            var server = serverPlayer.level().getServer();
            if (server != null) {
                server.execute(() -> {
                    try {
                        server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack(),
                                "reload"
                        );
                    } catch (Exception ignored) {}
                });
                serverPlayer.sendSystemMessage(Component.literal("Reload queued"));
            }
        }).exceptionally(e -> null);
    }
}
