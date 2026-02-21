package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 售货箱网络通信管理类
 * 处理多人协作时的数据同步
 */
public class ShippingBoxNetworking {

    /**
     * 向玩家发送成功提示消息的数据包
     */
    public record ShowSuccessMessage() implements CustomPacketPayload {
        public static final Type<ShowSuccessMessage> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "show_success_message")
        );

        public static final StreamCodec<FriendlyByteBuf, ShowSuccessMessage> STREAM_CODEC =
                StreamCodec.unit(new ShowSuccessMessage());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 玩家放置物品的数据包
     */
    public record PlayerPlaceItem(BlockPos pos, int slot) implements CustomPacketPayload {
        public static final Type<PlayerPlaceItem> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "player_place_item")
        );

        public static final StreamCodec<FriendlyByteBuf, PlayerPlaceItem> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, PlayerPlaceItem::pos,
                        ByteBufCodecs.INT, PlayerPlaceItem::slot,
                        PlayerPlaceItem::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 注册网络数据包处理器
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ShippingBox.MOD_ID);

        // 注册成功提示消息数据包
        registrar.playToClient(
                ShowSuccessMessage.TYPE,
                ShowSuccessMessage.STREAM_CODEC,
                ShippingBoxNetworking::handleShowSuccessMessage
        );

        // 注册玩家放置物品数据包
        registrar.playToServer(
                PlayerPlaceItem.TYPE,
                PlayerPlaceItem.STREAM_CODEC,
                ShippingBoxNetworking::handlePlayerPlaceItem
        );
    }

    /**
     * 处理显示成功消息
     */
    private static void handleShowSuccessMessage(ShowSuccessMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 在客户端显示成功消息
            context.player().displayClientMessage(
                    Component.translatable("message.shipping_box.exchange_success"),
                    true // 在行动栏显示
            );
        }).exceptionally(e -> {
            return null;
        });
    }

    /**
     * 处理玩家放置物品
     */
    private static void handlePlayerPlaceItem(PlayerPlaceItem packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.getBlockEntity(packet.pos()) instanceof ShippingBoxBlockEntity box) {
                box.setSlotOwner(packet.slot(), context.player().getUUID());
            }
        });
    }

    /**
     * 向指定玩家发送成功提示
     */
    public static void sendSuccessMessage(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ShowSuccessMessage());
    }

    /**
     * 发送玩家放置物品包
     */
    public static void sendPlayerPlaceItem(ServerPlayer player, BlockPos pos, int slot) {
        PacketDistributor.sendToServer(new PlayerPlaceItem(pos, slot));
    }
}
