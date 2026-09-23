package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 销售计数同步数据包记录类。
 * <p>
 * 服务端在销售计数发生变化时向客户端发送此数据包，
 * 客户端收到后更新本地销售计数缓存，供 Tooltip 显示使用。
 */
public record PacketSoldCountSync(String itemIdentifier, int soldCount) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketSoldCountSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "sold_count_sync")
    );

    /** 网络包编解码器，按字段顺序组合物品标识符与销售计数 */
    public static final StreamCodec<FriendlyByteBuf, PacketSoldCountSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketSoldCountSync::itemIdentifier,
            ByteBufCodecs.INT, PacketSoldCountSync::soldCount,
            PacketSoldCountSync::new
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
     * 处理销售计数同步请求。
     * <p>
     * 在主线程中将物品的最新销售计数更新到客户端缓存中。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketSoldCountSync packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端更新销售计数缓存
            ClientSoldCountCache.updateCache(packet.itemIdentifier, packet.soldCount);
        });
    }
}