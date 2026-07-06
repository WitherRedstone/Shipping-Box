package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 配方同步数据包（服务端→客户端）
 * <p>
 * 在玩家登录时，服务端将当前加载的兑换规则序列化为 JSON，
 * 经过 GZIP 压缩后发送给客户端。客户端解压后设置到本地缓存，
 * 用于在物品 Tooltip 中显示兑换信息。
 * 使用 GZIP 压缩减少网络传输量，适用于大量兑换规则的场景。
 */
public record PacketSyncRecipes(String rulesJson) implements CustomPacketPayload {
    public static final Type<PacketSyncRecipes> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "sync_recipes")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketSyncRecipes> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                try {
                    byte[] compressed = compress(packet.rulesJson);
                    buf.writeByteArray(compressed);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to compress recipe sync packet", e);
                }
            },
            (buf) -> {
                try {
                    byte[] compressed = buf.readByteArray();
                    String json = decompress(compressed);
                    return new PacketSyncRecipes(json);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to decompress recipe sync packet", e);
                }
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketSyncRecipes packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                // 在客户端设置配方规则
                ExchangeRecipeManager.setClientRules(packet.rulesJson());
            } catch (Exception e) {
                // 静默处理同步错误
            }
        }).exceptionally(e -> null);
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