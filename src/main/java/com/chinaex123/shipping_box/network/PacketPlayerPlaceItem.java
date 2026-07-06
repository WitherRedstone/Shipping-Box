package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 玩家放置物品数据包（客户端→服务端）
 * <p>
 * 当玩家在普通售货箱中放置物品时，客户端发送此数据包通知服务端，
 * 记录该槽位的物品归属玩家。此信息用于跨玩家兑换时的物品所有权追踪。
 * 服务端收到后会在方块实体中记录该槽位的放置者 UUID。
 */
public record PacketPlayerPlaceItem(BlockPos pos, int slot) implements CustomPacketPayload {
    public static final Type<PacketPlayerPlaceItem> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ShippingBox.MOD_ID, "player_place_item")
    );

    public static final StreamCodec<FriendlyByteBuf, PacketPlayerPlaceItem> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PacketPlayerPlaceItem::pos,
                    ByteBufCodecs.INT, PacketPlayerPlaceItem::slot,
                    PacketPlayerPlaceItem::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketPlayerPlaceItem packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.getBlockEntity(packet.pos()) instanceof ShippingBoxBlockEntity box) {
                box.setSlotOwner(packet.slot(), context.player().getUUID());
            }
        });
    }
}