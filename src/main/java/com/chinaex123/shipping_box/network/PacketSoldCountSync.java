package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 销售计数同步数据包记录类 **/
public record PacketSoldCountSync(String itemIdentifier, int soldCount) implements CustomPacketPayload {
    public static final Type<PacketSoldCountSync> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "sold_count_sync")
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