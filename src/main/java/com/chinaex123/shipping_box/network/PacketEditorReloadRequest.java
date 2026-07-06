package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/**
 * 编辑器重载请求数据包
 * <p>
 * 客户端向服务端发送此数据包，请求重新加载服务器配置/脚本。
 * 主要用于编辑器功能，方便开发者在不重启服务器的情况下应用更改。
 */
public record PacketEditorReloadRequest() implements CustomPacketPayload {

    /**
     * 数据包类型标识符
     * 用于在网络中唯一标识此数据包
     */
    public static final Type<PacketEditorReloadRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_reload_request")
    );

    /**
     * 数据包流编解码器
     * <p>
     * 使用 StreamCodec.unit() 创建，因为此数据包不包含任何数据字段。
     * 发送时不需要写入任何数据，接收时创建一个新实例。
     */
    public static final StreamCodec<FriendlyByteBuf, PacketEditorReloadRequest> STREAM_CODEC =
            StreamCodec.unit(new PacketEditorReloadRequest());

    /**
     * 获取数据包类型
     *
     * @return 数据包类型标识符
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理编辑器重载请求
     * <p>
     * 工作流程：
     * 1. 验证发送者是否为服务端玩家
     * 2. 检查玩家权限（需要 OP 权限等级 ≥ 2）
     * 3. 异步执行重载命令（/reload）
     * 4. 返回执行结果消息
     *
     * @param packet  接收到的数据包
     * @param context 网络上下文（包含玩家、网络线程等信息）
     */
    public static void handle(PacketEditorReloadRequest packet, IPayloadContext context) {
        // ========== 切换到主线程执行 ==========
        // context.enqueueWork 确保代码在服务端主线程执行，避免并发问题
        context.enqueueWork(() -> {
            // ========== 第一步：权限验证 ==========
            // 检查是否为服务端玩家且拥有 OP 权限（等级 ≥ 2）
            if (!(context.player() instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)) {
                // 权限不足，发送拒绝消息
                context.player().displayClientMessage(Component.literal("Permission denied"), false);
                return;
            }

            // ========== 第二步：执行重载命令 ==========
            try {
                // 在服务端主线程中执行 /reload 命令
                Objects.requireNonNull(serverPlayer.getServer()).execute(() -> {
                    try {
                        // 通过命令系统执行重载
                        // 使用 createCommandSourceStack() 创建命令源
                        serverPlayer.getServer().getCommands().performPrefixedCommand(
                                serverPlayer.getServer().createCommandSourceStack(),
                                "reload"
                        );
                    } catch (Exception ignored) {
                        // 命令执行异常，静默忽略
                    }
                });

                // 发送成功队列消息
                serverPlayer.displayClientMessage(Component.literal("Reload queued"), false);
            } catch (Exception e) {
                // 发送失败消息
                serverPlayer.displayClientMessage(Component.literal("Failed to queue reload"), false);
            }
        }).exceptionally(e -> null); // 异常处理：返回 null 避免进一步传播
    }
}