package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 编辑器读取文件包。
 * <p>
 * 由客户端发起，请求服务端读取指定相对路径下的规则文件内容。
 * 服务端校验请求者权限与路径合法性后，通过
 * {@link PacketEditorReadFileResult} 将文件内容或错误信息回传给客户端。
 */
public record PacketEditorReadFile(String requestId, String relativePath) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketEditorReadFile> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_read_file")
    );

    /** 网络包编解码器，按字段顺序组合请求 ID 与相对路径 */
    public static final StreamCodec<FriendlyByteBuf, PacketEditorReadFile> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFile::requestId,
            ByteBufCodecs.STRING_UTF8, PacketEditorReadFile::relativePath,
            PacketEditorReadFile::new
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
     * 处理编辑器读取文件请求。
     * <p>
     * 在服务端主线程中执行：先校验玩家权限（需权限等级 2），
     * 再解析相对路径并读取对应规则文件，最后将结果通过
     * 结果包回传给请求玩家。无权限或路径非法时返回相应错误信息。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketEditorReadFile packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)) {
                if (context.player() instanceof ServerPlayer player) {
                    PacketDistributor.sendToPlayer(
                            player,
                            new PacketEditorReadFileResult(packet.requestId(), false, "", "Permission denied")
                    );
                }
                return;
            }

            String content = "{\"rules\":[]}";
            String error = null;
            boolean ok = true;

            try {
                Path base = getBaseDir();
                Files.createDirectories(base);
                String rel = packet.relativePath() == null || packet.relativePath().isBlank() ? "editor.json" : packet.relativePath();
                if (!rel.endsWith(".json")) {
                    rel = rel + ".json";
                }
                Path relPath = Path.of(rel).normalize();
                Path target = base.resolve(relPath).normalize();
                if (relPath.isAbsolute() || !target.startsWith(base)) {
                    ok = false;
                    error = "Invalid path";
                } else if (Files.exists(target) && Files.isRegularFile(target)) {
                    content = Files.readString(target, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                ok = false;
                error = e.getMessage();
            }
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new PacketEditorReadFileResult(packet.requestId(), ok, content, error == null ? "" : error)
            );
        }).exceptionally(e -> null);
    }

    /**
     * 获取规则文件的基础目录。
     * <p>
     * 若 KubeJS 存在，优先使用 KubeJS 数据目录（与保存逻辑保持一致）；
     * 否则使用配置目录下的规则文件夹。
     *
     * @return 规则文件的基础目录
     */
    private static Path getBaseDir() {
        if (ModList.get().isLoaded("kubejs")) {
            return FMLPaths.GAMEDIR.get().resolve("kubejs/data/shipping_box/exchange_rules").normalize();
        }
        return FMLPaths.CONFIGDIR.get().resolve("shipping_box/exchange_rules").normalize();
    }
}