package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家放置物品的数据包记录类。
 * <p>
 * 当玩家在普通售货箱中放置物品时，客户端发送此数据包通知服务端，
 * 记录该槽位的物品归属玩家。此信息用于跨玩家兑换时的物品所有权追踪。
 * 服务端收到后会在方块实体中记录该槽位的放置者 UUID。
 */
public record PacketPlayerPlaceItem(BlockPos pos, int slot) implements CustomPacketPayload {

    /** 网络包类型标识 */
    public static final Type<PacketPlayerPlaceItem> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "player_place_item")
    );

    /** 网络包编解码器，按字段顺序组合方块位置与槽位索引 */
    public static final StreamCodec<FriendlyByteBuf, PacketPlayerPlaceItem> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PacketPlayerPlaceItem::pos,
                    ByteBufCodecs.INT, PacketPlayerPlaceItem::slot,
                    PacketPlayerPlaceItem::new
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
     * 处理玩家放置物品请求。
     * <p>
     * 在主线程中获取玩家所在世界，若目标位置为普通售货箱方块实体，
     * 则将对应槽位的归属者记录为当前玩家。
     *
     * @param packet  数据包
     * @param context 上下文
     */
    public static void handle(PacketPlayerPlaceItem packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.getBlockEntity(packet.pos()) instanceof ShippingBoxBlockEntity box) {
                box.setSlotOwner(packet.slot(), context.player().getUUID());
            }
        });
    }
}