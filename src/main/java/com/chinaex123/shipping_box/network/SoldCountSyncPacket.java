package com.chinaex123.shipping_box.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SoldCountSyncPacket(String itemIdentifier, int soldCount) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("shipping_box", "sold_count_sync");
    public static final Type<SoldCountSyncPacket> TYPE = new Type<>(ID);

    // 添加StreamCodec用于序列化
    public static final StreamCodec<FriendlyByteBuf, SoldCountSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.itemIdentifier);
                buf.writeInt(packet.soldCount);
            },
            buf -> new SoldCountSyncPacket(buf.readUtf(), buf.readInt())
    );

    public SoldCountSyncPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理销售计数同步数据包
     * <p>
     * 在客户端接收并处理来自服务器的销售计数更新数据包，
     * 将新的销售计数存储到客户端缓存中以供tooltip显示使用。
     *
     * @param context 网络上下文，提供执行环境和玩家信息
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端更新销售计数缓存
            ClientSoldCountCache.updateCache(itemIdentifier, soldCount);
        });
    }
}
