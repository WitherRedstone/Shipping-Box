package com.chinaex123.shipping_box.network;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 售货箱网络通信管理类 **/
public class ShippingBoxNetworking {

    /**
     * 注册网络数据包处理器
     *
     * @param event 负载处理器注册事件
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ShippingBox.MOD_ID);

        // 注册成功提示消息数据包
        registrar.playToClient(
                PacketShowSuccessMessage.TYPE,
                PacketShowSuccessMessage.STREAM_CODEC,
                PacketShowSuccessMessage::handle
        );

        // 注册玩家放置物品数据包
        registrar.playToServer(
                PacketPlayerPlaceItem.TYPE,
                PacketPlayerPlaceItem.STREAM_CODEC,
                PacketPlayerPlaceItem::handle
        );

        // 注册配方同步数据包
        registrar.playToClient(
                PacketSyncRecipes.TYPE,
                PacketSyncRecipes.STREAM_CODEC,
                PacketSyncRecipes::handle
        );

        // 注册销售计数同步数据包
        registrar.playToClient(
                PacketSoldCountSync.TYPE,
                PacketSoldCountSync.STREAM_CODEC,
                PacketSoldCountSync::handle
        );

        // 注册特效数据包
        registrar.playToClient(
                PacketExchangeEffects.TYPE,
                PacketExchangeEffects.STREAM_CODEC,
                PacketExchangeEffects::handle
        );

        // 网页编辑器相关数据包
        registrar.playToClient(
                PacketStartLocalWebEditor.TYPE,
                PacketStartLocalWebEditor.STREAM_CODEC,
                PacketStartLocalWebEditor::handle
        );

        registrar.playToServer(
                PacketEditorSaveRules.TYPE,
                PacketEditorSaveRules.STREAM_CODEC,
                PacketEditorSaveRules::handle
        );

        registrar.playToServer(
                PacketEditorReadFile.TYPE,
                PacketEditorReadFile.STREAM_CODEC,
                PacketEditorReadFile::handle
        );

        registrar.playToClient(
                PacketEditorReadFileResult.TYPE,
                PacketEditorReadFileResult.STREAM_CODEC,
                PacketEditorReadFileResult::handle
        );

        registrar.playToClient(
                PacketEditorSaveRulesResult.TYPE,
                PacketEditorSaveRulesResult.STREAM_CODEC,
                PacketEditorSaveRulesResult::handle
        );

        registrar.playToServer(
                PacketEditorReloadRequest.TYPE,
                PacketEditorReloadRequest.STREAM_CODEC,
                PacketEditorReloadRequest::handle
        );
    }

    /**
     * 向单个客户端玩家同步配方规则
     *
     * @param player 目标玩家
     */
    public static void syncRecipesToClient(ServerPlayer player) {
        try {
            String rulesJson = ExchangeRecipeManager.serializeRulesToJson();
            PacketSyncRecipes packet = new PacketSyncRecipes(rulesJson);
            PacketDistributor.sendToPlayer(player, packet);
        } catch (Exception e) {
            // 静默处理序列化错误
        }
    }
}