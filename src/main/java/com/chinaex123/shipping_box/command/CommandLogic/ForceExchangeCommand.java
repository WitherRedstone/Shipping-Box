package com.chinaex123.shipping_box.command.CommandLogic;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 强制兑换命令。
 * <p>
 * 由玩家执行，对视线指向的售货箱（普通或自动）立即触发一次兑换，
 * 忽略每日兑换的时间窗口限制。
 */
public class ForceExchangeCommand {

    /**
     * 执行强制兑换命令。
     * <p>
     * 先校验命令来源为玩家，再通过视线射线检测获取目标方块，
     * 根据方块实体类型分别调用对应的强制兑换方法。
     *
     * @param context 命令上下文
     * @return 命令执行结果（成功返回 1，失败返回 0）
     */
    public static int execute(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();

            // 先获取玩家实体
            var entity = source.getEntity();
            if (!(entity instanceof ServerPlayer player)) {
                source.sendFailure(Component.translatable("command.shipping_box.not_player")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            // 获取玩家视线指向的方块
            HitResult hitResult = player.pick(20.0D, 0.0F, false);

            if (hitResult.getType() != HitResult.Type.BLOCK) {
                player.sendSystemMessage(Component.translatable("command.shipping_box.no_block_target")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
            BlockEntity blockEntity = player.level().getBlockEntity(pos);

            if (blockEntity instanceof ShippingBoxBlockEntity shippingBox) {
                shippingBox.forceExchange();
                player.sendSystemMessage(Component.translatable("command.shipping_box.force_exchange_success", pos.toShortString())
                        .withStyle(ChatFormatting.GOLD), true);
                return 1;
            } else if (blockEntity instanceof AutoShippingBoxBlockEntity autoShippingBox) {
                autoShippingBox.forceExchange();
                player.sendSystemMessage(Component.translatable("command.shipping_box.force_exchange_success", pos.toShortString())
                        .withStyle(ChatFormatting.GOLD), true);
                return 1;
            } else {
                player.sendSystemMessage(Component.translatable("command.shipping_box.invalid_block_entity")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

        } catch (Exception e) {
            context.getSource().sendFailure(Component.translatable("command.shipping_box.error.execution", e.getMessage()));
            return 0;
        }
    }
}