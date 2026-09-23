package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 配方同步数据包记录类。
 * <p>
 * 服务端在规则加载或变更后向客户端发送此数据包，
 * 将序列化后的兑换规则同步到客户端，供客户端 Tooltip 等逻辑使用。
 * 规则 JSON 使用 GZIP 压缩传输。
 */
public record PacketSyncRecipes(String rulesJson) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketSyncRecipes> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "sync_recipes")
    );

    /** 网络包编解码器，写入时 GZIP 压缩规则 JSON，读取时解压还原 */
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
     * 处理配方同步请求。
     * <p>
     * 在主线程中将收到的规则 JSON 设置到客户端配方管理器中，
     * 同步失败时静默处理，避免影响游戏运行。
     *
     * @param packet  数据包
     * @param context 上下文
     */
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