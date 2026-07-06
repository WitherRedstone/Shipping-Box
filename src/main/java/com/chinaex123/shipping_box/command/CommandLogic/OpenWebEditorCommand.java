package com.chinaex123.shipping_box.command.CommandLogic;

import com.chinaex123.shipping_box.network.PacketStartLocalWebEditor;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.security.SecureRandom;
import java.util.Base64;

/** 打开Web编辑器的命令执行逻辑 **/
public class OpenWebEditorCommand {

    /** 加密安全的随机数生成器 **/
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 执行打开Web编辑器的命令
     *
     * <p>这是 Brigadier 命令框架的命令执行方法</p>
     *
     * @param context 命令上下文，包含命令源和其他信息
     * @return 命令执行结果码（1表示成功，0表示失败）
     */
    public static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // 验证命令执行者
        ServerPlayer player;
        try {
            // 获取执行命令的玩家
            player = source.getPlayerOrException();
        } catch (Exception e) {
            // 非玩家执行命令时，发送错误消息
            source.sendFailure(Component.translatable("command.shipping_box.web.not_player"));
            return 0; // 返回0表示执行失败
        }

        // 生成访问令牌
        String token = generateToken();

        // 发送网络包到客户端
        PacketDistributor.sendToPlayer(player, new PacketStartLocalWebEditor(token));

        // 向玩家发送反馈消息
        player.displayClientMessage(Component.translatable("command.shipping_box.web.starting"), false);

        return 1;
    }

    /**
     * 生成安全的访问令牌
     *
     * @return Base64编码的令牌字符串，可用于URL参数传输
     */
    private static String generateToken() {
        // 生成18字节的随机数据
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);

        // 使用URL安全的Base64编码，去除填充字符 '='
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}