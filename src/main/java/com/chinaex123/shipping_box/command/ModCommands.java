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
 * 模组命令注册类。
 * <p>
 * 注册 {@code /shipping_box} 根命令及其子命令，包括强制兑换、
 * 规则管理与打开网页编辑器。根命令需要权限等级 2 才可执行。
 */
public class ModCommands {

    /**
     * 注册模组命令。
     * <p>
     * 注册根命令 {@code /shipping_box}，并挂载以下子命令：
     * - {@code force_exchange}：强制兑换；
     * - {@code rules}：规则管理（count 统计数量、list 分页列出）；
     * - {@code web}：打开网页编辑器。
     *
     * @param dispatcher 命令调度器
     */
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