package com.chinaex123.shipping_box.command;

import com.chinaex123.shipping_box.command.CommandLogic.CountRulesCommand;
import com.chinaex123.shipping_box.command.CommandLogic.ForceExchangeCommand;
import com.chinaex123.shipping_box.command.CommandLogic.ListRulesCommand;
import com.chinaex123.shipping_box.command.CommandLogic.OpenWebEditorCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shipping_box")
                .requires(source -> source.hasPermission(2))

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
