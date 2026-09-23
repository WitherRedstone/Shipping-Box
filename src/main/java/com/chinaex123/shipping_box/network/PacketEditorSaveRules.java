package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 编辑器保存规则包。
 * <p>
 * Web 编辑器通过此数据包将修改后的兑换规则保存到服务端文件系统。
 * 需要发送端拥有 OP 权限（等级 2 及以上）才能执行。
 * 数据使用 GZIP 压缩传输，支持 KubeJS 和独立配置两种保存路径。
 * 保存后自动执行 /reload 命令使规则生效，若安装了 KubeJS 则先执行 KubeJS 重载。
 */
public record PacketEditorSaveRules(String requestId, String relativePath, String rulesJson) implements CustomPacketPayload {

    /** 日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketEditorSaveRules.class);

    /** 网络包类型标识 */
    public static final Type<PacketEditorSaveRules> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_save_rules")
    );

    /** 网络包编解码器，写入时 GZIP 压缩规则 JSON，读取时解压还原 */
    public static final StreamCodec<FriendlyByteBuf, PacketEditorSaveRules> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                try {
                    buf.writeUtf(packet.requestId());
                    buf.writeUtf(packet.relativePath());
                    buf.writeByteArray(compress(packet.rulesJson()));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to compress editor rules", e);
                }
            },
            (buf) -> {
                try {
                    String requestId = buf.readUtf();
                    String relativePath = buf.readUtf();
                    byte[] compressed = buf.readByteArray();
                    String json = decompress(compressed);
                    return new PacketEditorSaveRules(requestId, relativePath, json);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to decompress editor rules", e);
                }
            }
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
     * 处理编辑器保存规则请求。
     * <p>
     * 在主线程中执行：校验玩家 OP 权限，解析并校验规则 JSON 结构，
     * 校验通过后执行路径安全校验并写入文件，随后异步触发 KubeJS 与 /reload 重载，
     * 最后通过结果包回传保存结果。任一环节失败时返回对应的错误信息。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketEditorSaveRules packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)) {
                sendResult(context, packet.requestId(), false, "",
                        Component.translatable("message.shipping_box.editor_save.denied").getString());
                return;
            }

            JsonObject obj;
            try {
                obj = JsonParser.parseString(packet.rulesJson()).getAsJsonObject();
            } catch (JsonParseException e) {
                sendResult(context, packet.requestId(), false, "",
                        Component.translatable("message.shipping_box.editor_save.invalid_json").getString());
                return;
            } catch (Exception e) {
                sendResult(context, packet.requestId(), false, "",
                        Component.translatable("message.shipping_box.editor_save.invalid_payload").getString());
                return;
            }

            if (!obj.has("rules") || !obj.get("rules").isJsonArray()) {
                sendResult(context, packet.requestId(), false, "",
                        Component.translatable("message.shipping_box.editor_save.expected_rules").getString());
                return;
            }

            try {
                Path base = getBaseDir();
                Files.createDirectories(base);
                String rel = packet.relativePath() == null || packet.relativePath().isBlank() ? "editor.json" : packet.relativePath();
                if (!rel.endsWith(".json")) {
                    rel = rel + ".json";
                }
                Path relPath = Path.of(rel).normalize();
                Path file = base.resolve(relPath).normalize();
                if (relPath.isAbsolute() || !file.startsWith(base)) {
                    ShippingBox.LOGGER.warn("[PacketEditorSaveRules.handle] Web 编辑器保存被路径校验拦截，base={}, rel={}", base, rel);
                    sendResult(context, packet.requestId(), false, "",
                            Component.translatable("message.shipping_box.editor_save.invalid_path").getString());
                    return;
                }
                Files.createDirectories(file.getParent());
                Files.writeString(file, packet.rulesJson(), StandardCharsets.UTF_8);

                // 写入成功后异步触发重载：若安装 KubeJS 先重载脚本，再执行 /reload
                var server = serverPlayer.getServer();
                server.execute(() -> {
                    var commandSource = server.createCommandSourceStack().withPermission(4);
                    try {
                        if (ModList.get().isLoaded("kubejs")) {
                            server.getCommands().performPrefixedCommand(commandSource, "kubejs reload server_scripts");
                        }
                    } catch (Exception e) {
                        ShippingBox.LOGGER.warn("[PacketEditorSaveRules.handle] 保存后执行 KubeJS 重载命令失败", e);
                    }
                    try {
                        server.getCommands().performPrefixedCommand(commandSource, "reload");
                    } catch (Exception e) {
                        ShippingBox.LOGGER.warn("[PacketEditorSaveRules.handle] 保存后执行 /reload 命令失败", e);
                    }
                });

                sendResult(context, packet.requestId(), true, file.toString(), "");
            } catch (Exception e) {
                ShippingBox.LOGGER.warn("[PacketEditorSaveRules.handle] 保存规则文件失败，requestId={}, relativePath={}",
                        packet.requestId(), packet.relativePath(), e);
                sendResult(context, packet.requestId(), false, "",
                        e.getMessage() == null ? "unknown error" : e.getMessage());
            }
        }).exceptionally(e -> null);
    }

    /**
     * 向请求玩家发送保存结果。
     *
     * @param context   上下文
     * @param requestId 请求 ID
     * @param ok        是否成功
     * @param savedPath 保存路径（成功时）
     * @param error     错误信息（失败时）
     */
    private static void sendResult(IPayloadContext context, String requestId, boolean ok, String savedPath, String error) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new PacketEditorSaveRulesResult(
                            requestId,
                            ok,
                            savedPath == null ? "" : savedPath,
                            error == null ? "" : error
                    )
            );
        }
    }

    /**
     * 获取规则文件的基础目录。
     * <p>
     * 若 KubeJS 存在，优先使用 KubeJS 数据目录（与读取逻辑保持一致）；
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

    /**
     * 使用 GZIP 压缩字符串。
     *
     * @param str 待压缩字符串
     * @return 压缩后的字节数组，输入为空时返回空数组
     * @throws IOException 压缩过程发生 I/O 错误时抛出
     */
    private static byte[] compress(String str) throws IOException {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    /**
     * 解压 GZIP 字节数组。
     *
     * @param compressed 压缩后的字节数组
     * @return 解压后的字符串，输入为空时返回空字符串
     * @throws IOException 解压过程发生 I/O 错误时抛出
     */
    private static String decompress(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) {
            return "";
        }
        ByteArrayInputStream in = new ByteArrayInputStream(compressed);
        try (GZIPInputStream gzip = new GZIPInputStream(in)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}