package com.chinaex123.shipping_box.command.CommandLogic;

import com.chinaex123.shipping_box.network.PacketStartLocalWebEditor;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 打开 Web 编辑器的命令执行逻辑。
 * <p>
 * 校验命令执行者为玩家后生成一次性访问令牌，
 * 通过自定义网络包通知客户端启动本地 Web 编辑器，
 * 并向玩家反馈启动提示。
 */
public class OpenWebEditorCommand {

    /** 加密安全的随机数生成器 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 执行打开 Web 编辑器的命令。
     * <p>
     * 这是 Brigadier 命令框架的命令执行方法。
     * 先校验执行者为玩家，再生成访问令牌并发送网络包，
     * 最后向玩家发送反馈消息。
     *
     * @param context 命令上下文，包含命令源和其他信息
     * @return 命令执行结果码（1 表示成功，0 表示失败）
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
            return 0; // 返回 0 表示执行失败
        }

        // 生成访问令牌
        String token = generateToken();

        // 发送网络包到客户端
        PacketDistributor.sendToPlayer(player, new PacketStartLocalWebEditor(token));

        // 向玩家发送反馈消息
        player.sendSystemMessage(Component.translatable("command.shipping_box.web.starting"));

        return 1;
    }

    /**
     * 生成安全的访问令牌。
     * <p>
     * 生成 18 字节随机数据，并使用 URL 安全的 Base64 编码（去除填充字符），
     * 以便作为 URL 参数传输。
     *
     * @return Base64 编码的令牌字符串，可用于 URL 参数传输
     */
    private static String generateToken() {
        // 生成 18 字节的随机数据
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);

        // 使用 URL 安全的 Base64 编码，去除填充字符 '='
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}