package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 销售计数同步数据包（服务端→客户端）
 * <p>
 * 当物品的销售计数发生变化时，服务端向所有客户端广播此数据包，
 * 以更新客户端的本地缓存。销售计数用于动态定价模式下
 * 在 Tooltip 中显示当前销量和价格信息。
 */
public record PacketSoldCountSync(String itemIdentifier, int soldCount) implements CustomPacketPayload {
    public static final Type<PacketSoldCountSync> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "sold_count_sync")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketSoldCountSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketSoldCountSync::itemIdentifier,
            ByteBufCodecs.INT, PacketSoldCountSync::soldCount,
            PacketSoldCountSync::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketSoldCountSync packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端更新销售计数缓存
            ClientSoldCountCache.updateCache(packet.itemIdentifier, packet.soldCount);
        });
    }
}