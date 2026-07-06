package com.chinaex123.shipping_box.command;

import com.chinaex123.shipping_box.command.CommandLogic.CountRulesCommand;
import com.chinaex123.shipping_box.command.CommandLogic.ForceExchangeCommand;
import com.chinaex123.shipping_box.command.CommandLogic.ListRulesCommand;
import com.chinaex123.shipping_box.command.CommandLogic.OpenWebEditorCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * 模组命令注册类
 * <p>
 * 注册售货箱模组的所有管理命令，所有命令需要 OP 权限（LEVEL_GAMEMASTERS）才能执行。
 * 根命令为 {@code /shipping_box}，包含以下子命令：
 * <ul>
 *   <li>{@code force_exchange} - 强制玩家指向的售货箱立即执行兑换</li>
 *   <li>{@code rules count} - 统计当前加载的兑换规则数量</li>
 *   <li>{@code rules list [page]} - 分页列出所有兑换规则</li>
 *   <li>{@code web} - 启动本地 Web 规则编辑器</li>
 * </ul>
 */
public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shipping_box")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

                // 子命令：force_exchange - 强制兑换
                .then(Commands.literal("force_exchange")
                        .executes(ForceExchangeCommand::execute))

                // 子命令：rules - 规则管理
                .then(Commands.literal("rules")
                        // count - 统计规则数量
                        .then(Commands.literal("count")
                                .executes(CountRulesCommand::execute))
                        // list - 列出规则
                        .then(Commands.literal("list")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ListRulesCommand::execute))
                                .executes(ListRulesCommand::execute)))

                // 子命令：web - 打开网页编辑器
                .then(Commands.literal("web")
                        .executes(OpenWebEditorCommand::execute)));
    }
}
