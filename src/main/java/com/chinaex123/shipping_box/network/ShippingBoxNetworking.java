package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.UUID;

/**
 * 售货箱网络通信管理类
 * 处理多人协作时的数据同步
 */
public class ShippingBoxNetworking {
    /**
     * 打开售货箱的数据包
     */
    public record OpenShippingBox(BlockPos pos, UUID playerUUID) implements CustomPacketPayload {
        public static final Type<OpenShippingBox> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "open_shipping_box")
        );

        public static final StreamCodec<FriendlyByteBuf, OpenShippingBox> STREAM_CODEC =
                StreamCodec.of(
                        (buf, packet) -> {
                            BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                            buf.writeUtf(packet.playerUUID().toString());
                        },
                        buf -> new OpenShippingBox(
                                BlockPos.STREAM_CODEC.decode(buf),
                                UUID.fromString(buf.readUtf())
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 显示成功消息的数据包记录类
     * 用于在网络上传输兑换成功的通知消息
     */
    public record ShowSuccessMessage() implements CustomPacketPayload {
        public static final Type<ShowSuccessMessage> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "show_success_message")
        );

        public static final StreamCodec<FriendlyByteBuf, ShowSuccessMessage> STREAM_CODEC =
                StreamCodec.unit(new ShowSuccessMessage());

        /**
         * 获取数据包类型
         *
         * @return 数据包类型标识
         */
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 玩家放置物品的数据包记录类
     * 用于在网络上传输玩家在售货箱中放置物品的信息
     *
     * @param pos 方块位置
     * @param slot 槽位索引
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

        /**
         * 获取数据包类型
         *
         * @return 数据包类型标识
         */
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 配方同步数据包记录类
     * 用于将服务端的兑换配方同步到客户端
     *
     * @param rulesJson 配方规则列表的JSON字符串表示
     */
    public record SyncRecipes(String rulesJson) implements CustomPacketPayload {
        public static final Type<SyncRecipes> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "sync_recipes")
        );

        public static final StreamCodec<FriendlyByteBuf, SyncRecipes> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SyncRecipes::rulesJson,
                        SyncRecipes::new
                );

        /**
         * 获取数据包类型
         *
         * @return 数据包类型标识
         */
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 注册网络数据包处理器
     *
     * @param event 负载处理器注册事件
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

        // 注册配方同步数据包
        registrar.playToClient(
                SyncRecipes.TYPE,
                SyncRecipes.STREAM_CODEC,
                ShippingBoxNetworking::handleSyncRecipes
        );
    }

    /**
     * 处理打开运输箱GUI的网络数据包
     * 在服务端执行GUI打开逻辑，确保线程安全性
     *
     * @param packet 包含位置信息和玩家UUID的打开请求数据包
     * @param context 网络上下文，提供玩家和执行环境信息
     */
    private static void handleOpenShippingBox(OpenShippingBox packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Level level = player.level();

            if (level.getBlockEntity(packet.pos()) instanceof ShippingBoxBlockEntity box) {
                // 直接打开GUI，菜单会处理玩家特定的存储
                player.openMenu(new SimpleMenuProvider(
                        (windowId, playerInventory, playerEntity) ->
                                new ShippingBoxMenu(windowId, playerInventory, box, packet.playerUUID()),
                        Component.translatable("block.shipping_box.shipping_box")
                ), buf -> {
                    buf.writeBlockPos(packet.pos());
                    buf.writeUUID(packet.playerUUID());
                });
            }
        }).exceptionally(e -> null);
    }

    /**
     * 处理配方同步数据包
     * 在客户端接收并应用服务端发送的配方规则
     *
     * @param packet 配方同步数据包
     * @param context 网络上下文
     */
    private static void handleSyncRecipes(SyncRecipes packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                // 在客户端设置配方规则
                ExchangeRecipeManager.setClientRules(packet.rulesJson());
            } catch (Exception e) {
                // 静默处理同步错误
            }
        }).exceptionally(e -> {
            return null;
        });
    }

    /**
     * 处理显示成功消息的数据包
     * 在客户端玩家界面显示兑换成功的提示信息
     *
     * @param packet 成功消息数据包
     * @param context 网络上下文
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
     * 处理玩家放置物品的数据包
     * 记录玩家在指定槽位放置物品的所有权信息
     *
     * @param packet 玩家放置物品数据包
     * @param context 网络上下文
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
     * 向指定玩家发送兑换成功消息
     *
     * @param player 目标玩家
     */
    public static void sendSuccessMessage(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ShowSuccessMessage());
    }

    /**
     * 发送玩家放置物品的信息到服务器
     *
     * @param player 发送消息的玩家
     * @param pos 方块位置
     * @param slot 槽位索引
     */
    public static void sendPlayerPlaceItem(ServerPlayer player, BlockPos pos, int slot) {
        PacketDistributor.sendToServer(new PlayerPlaceItem(pos, slot));
    }

    /**
     * 向所有客户端玩家同步配方规则
     *
     * @param players 玩家列表
     */
    public static void syncRecipesToClients(List<ServerPlayer> players) {
        try {
            String rulesJson = ExchangeRecipeManager.serializeRulesToJson();
            SyncRecipes packet = new SyncRecipes(rulesJson);

            for (ServerPlayer player : players) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        } catch (Exception e) {
            // 静默处理序列化错误
        }
    }

    /**
     * 向单个客户端玩家同步配方规则
     *
     * @param player 目标玩家
     */
    public static void syncRecipesToClient(ServerPlayer player) {
        try {
            String rulesJson = ExchangeRecipeManager.serializeRulesToJson();
            SyncRecipes packet = new SyncRecipes(rulesJson);
            PacketDistributor.sendToPlayer(player, packet);
        } catch (Exception e) {
            // 静默处理序列化错误
        }
    }
}
