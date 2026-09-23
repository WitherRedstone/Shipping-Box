package com.chinaex123.shipping_box.client;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.network.ClientSoldCountCache;
import com.chinaex123.shipping_box.web.EditorIconCacheManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 客户端事件处理器。
 * <p>
 * 集中处理客户端侧的玩家登出、客户端刻与客户端命令注册等事件，
 * 通过事件订阅自动注册到游戏事件总线，仅在客户端生效。
 */
@EventBusSubscriber(modid = ShippingBox.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {

    /**
     * 客户端玩家登出事件处理器。
     * <p>
     * 玩家登出时清空已售数量缓存，避免残留数据影响下次登录。
     *
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientSoldCountCache.clearCache();
    }

    /**
     * 客户端刻事件处理器。
     * <p>
     * 每客户端刻驱动编辑器图标缓存任务。
     *
     * @param event 客户端刻事件
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EditorIconCacheManager.getInstance().tick();
    }

    /**
     * 注册客户端命令事件处理器。
     * <p>
     * 在客户端注册自定义命令，用于：
     * - 缓存管理（图标缓存）；
     * - 状态查询；
     * - 调试和运维。
     *
     * @param event 注册客户端命令事件
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal(ShippingBox.MOD_ID)
                .then(Commands.literal("editor")
                        // 子命令：cache_icons（缓存图标
                        .then(Commands.literal("cache_icons")
                                // 普通执行：启动图标缓存（非强制模式）
                                .executes(ctx -> {
                                    EditorIconCacheManager.getInstance().startCache(false);
                                    ctx.getSource().sendSuccess(() ->
                                                    Component.translatable("command." + ShippingBox.MOD_ID + ".editor.cache_icons.started"),
                                            false);
                                    return 1;
                                })
                                // 强制执行：启动图标缓存（强制模式，忽略已有缓存）
                                .then(Commands.literal("force")
                                        .executes(ctx -> {
                                            EditorIconCacheManager.getInstance().startCache(true);
                                            ctx.getSource().sendSuccess(() ->
                                                            Component.translatable("command." + ShippingBox.MOD_ID + ".editor.cache_icons.force_started"),
                                                    false);
                                            return 1;
                                        })
                                )
                        )
                        // 子命令：cache_status（查询缓存状态）
                        .then(Commands.literal("cache_status")
                                .executes(ctx -> {
                                    var mgr = EditorIconCacheManager.getInstance();
                                    String status = mgr.getStatus().name();
                                    int processed = mgr.getProcessed();
                                    int total = mgr.getTotal();
                                    String errorMsg = mgr.getErrorMessage();

                                    // 根据是否有错误信息构造不同的消息
                                    Component message;
                                    if (errorMsg != null) {
                                        // 有错误：显示状态、进度和错误信息
                                        message = Component.translatable(
                                                "command." + ShippingBox.MOD_ID + ".editor.cache_status.with_error",
                                                status, processed, total, errorMsg
                                        );
                                    } else {
                                        // 正常：显示状态和进度
                                        message = Component.translatable(
                                                "command." + ShippingBox.MOD_ID + ".editor.cache_status",
                                                status, processed, total
                                        );
                                    }

                                    ctx.getSource().sendSuccess(() -> message, false);
                                    return 1;
                                })
                        )
                        // 子命令：cache_clear（清除缓存）
                        .then(Commands.literal("cache_clear")
                                .executes(ctx -> {
                                    EditorIconCacheManager.getInstance().clearCache();
                                    ctx.getSource().sendSuccess(() ->
                                                    Component.translatable("command." + ShippingBox.MOD_ID + ".editor.cache_clear.success"),
                                            false);
                                    return 1;
                                })
                        )
                )
        );
    }
}