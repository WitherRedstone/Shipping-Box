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
 * 编辑器重载请求数据包（客户端→服务端）。
 * <p>
 * Web 编辑器通过此数据包请求重载服务端的数据包和配置。
 * 需要发送端处于创造模式才能执行。
 * 服务端收到后会执行 {@code /reload} 命令使最新配置生效。
 */
public record PacketEditorReloadRequest() implements CustomPacketPayload {

    /**
     * 网络包类型标识
     */
    public static final Type<PacketEditorReloadRequest> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_reload_request")
    );

    /**
     * 无字段网络包的编解码器
     */
    public static final StreamCodec<FriendlyByteBuf, PacketEditorReloadRequest> STREAM_CODEC =
            StreamCodec.unit(new PacketEditorReloadRequest());

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
     * 处理编辑器重载请求。
     * <p>
     * 在主线程中校验玩家是否处于创造模式；通过后由服务端
     * 在自身线程上执行 {@code /reload} 命令，并向玩家反馈排队提示。
     * 权限不足时发送拒绝消息。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketEditorReloadRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            ServerPlayerGameMode gm = serverPlayer.gameMode;
            // 检查是否创造模式(OP 等级 ≥ 2 对应 isCreative)
            if (!gm.isCreative()) {
                context.player().sendSystemMessage(
                        Component.translatable("message.shipping_box.editor_reload.denied"));
                return;
            }

            try {
                // 执行 reload
                var server = serverPlayer.level().getServer();
                server.execute(() -> {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(),
                            "reload"
                    );
                });

                // 发送成功队列消息
                serverPlayer.sendSystemMessage(
                        Component.translatable("message.shipping_box.editor_reload.queued"));
            } catch (Exception e) {
                // 发送失败消息
                serverPlayer.sendSystemMessage(
                        Component.translatable("message.shipping_box.editor_reload.failed"));
            }
        }).exceptionally(e -> null);
    }
}