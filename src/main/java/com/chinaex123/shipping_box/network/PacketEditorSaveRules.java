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

/** 编辑器保存规则包 **/
public record PacketEditorSaveRules(String requestId, String relativePath, String rulesJson) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketEditorSaveRules.class);

    public static final Type<PacketEditorSaveRules> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "editor_save_rules")
    );

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketEditorSaveRules packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)) {
                sendResult(context, packet.requestId(), false, "", "Permission denied");
                return;
            }

            JsonObject obj;
            try {
                obj = JsonParser.parseString(packet.rulesJson()).getAsJsonObject();
            } catch (JsonParseException e) {
                sendResult(context, packet.requestId(), false, "", "Invalid JSON");
                return;
            } catch (Exception e) {
                sendResult(context, packet.requestId(), false, "", "Invalid payload");
                return;
            }

            if (!obj.has("rules") || !obj.get("rules").isJsonArray()) {
                sendResult(context, packet.requestId(), false, "", "Expected {\"rules\": [...]}");
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
                    LOGGER.warn("Web editor save blocked by path validation. base={}, rel={}", base, rel);
                    sendResult(context, packet.requestId(), false, "", "Invalid path");
                    return;
                }
                Files.createDirectories(file.getParent());
                Files.writeString(file, packet.rulesJson(), StandardCharsets.UTF_8);

                var server = serverPlayer.getServer();
                server.execute(() -> {
                    var commandSource = server.createCommandSourceStack().withPermission(4);
                    try {
                        if (ModList.get().isLoaded("kubejs")) {
                            server.getCommands().performPrefixedCommand(commandSource, "kubejs reload server_scripts");
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to run KubeJS reload command after save: {}", e.getMessage());
                    }
                    try {
                        server.getCommands().performPrefixedCommand(commandSource, "reload");
                    } catch (Exception e) {
                        LOGGER.warn("Failed to run /reload after save: {}", e.getMessage());
                    }
                });

                sendResult(context, packet.requestId(), true, file.toString(), "");
            } catch (Exception e) {
                sendResult(context, packet.requestId(), false, "", e.getMessage());
            }
        }).exceptionally(e -> null);
    }

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

    private static Path getBaseDir() {
        if (ModList.get().isLoaded("kubejs")) {
            return FMLPaths.GAMEDIR.get().resolve("kubejs/data/shipping_box/exchange_rules").normalize();
        }
        return FMLPaths.CONFIGDIR.get().resolve("shipping_box/exchange_rules").normalize();
    }

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