package com.chinaex123.shipping_box.block;

import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ShippingBoxBlock extends BaseEntityBlock {
    public static final MapCodec<ShippingBoxBlock> CODEC = simpleCodec(ShippingBoxBlock::new);

    public ShippingBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShippingBoxBlockEntity(pos, state);
    }

    /**
     * 处理方块被玩家右键点击的交互
     * 播放声音并打开GUI
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        // 播放木桶开启声音
        level.playSound(
                player,
                pos,
                SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.barrel.open")),
                SoundSource.BLOCKS,
                0.5F,
                level.random.nextFloat() * 0.1F + 0.9F
        );

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShippingBoxBlockEntity shippingBox) {
                // 设置当前操作玩家
                ShippingBoxBlockEntity.setCurrentPlayer((ServerPlayer) player);
                // 打开菜单
                player.openMenu(shippingBox);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShippingBoxBlockEntity shippingBox) {
                if (level instanceof ServerLevel) {
                    Containers.dropContents(level, pos, shippingBox);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof ShippingBoxBlockEntity shippingBox) {
                    shippingBox.tick();
                }
            };
        }
        return null;
    }
}
