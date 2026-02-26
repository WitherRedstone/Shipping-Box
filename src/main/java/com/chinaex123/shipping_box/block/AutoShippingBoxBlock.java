package com.chinaex123.shipping_box.block;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AutoShippingBoxBlock extends BaseEntityBlock {

    public static final MapCodec<AutoShippingBoxBlock> CODEC = simpleCodec(AutoShippingBoxBlock::new);

    public MapCodec<AutoShippingBoxBlock> codec() {
        return CODEC;
    }

    public AutoShippingBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoShippingBoxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 简单直接的实现
        if (type == ModBlockEntities.AUTOMATED_SHIPPING_BOX.get() && !level.isClientSide) {
            return (level1, pos, state1, blockEntity) -> {
                ((AutoShippingBoxBlockEntity) blockEntity).tick();
            };
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AutoShippingBoxBlockEntity autoBox) {
                autoBox.bindPlayer(player.getUUID());
                // 添加绑定成功的消息提示
                player.displayClientMessage(
                        Component.translatable("message.shipping_box.auto_box_bound",
                                player.getName().copy().withStyle(style -> style.withColor(0xFFAA00))),
                        true
                );
            }
        }
    }

    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (blockEntity instanceof AutoShippingBoxBlockEntity autoBox) {
                // 检查玩家权限
                if (autoBox.canPlayerAccess(player)) {
                    // 播放开启声音
                    level.playSound(null, pos,
                            SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.barrel.open")),
                            SoundSource.BLOCKS,
                            0.5F, level.random.nextFloat() * 0.1F + 0.9F);

                    player.openMenu(autoBox);
                } else {
                    player.displayClientMessage(Component.translatable("message.shipping_box.access_denied"), true);
                }
            }
        }
        return InteractionResult.CONSUME;
    }
}
