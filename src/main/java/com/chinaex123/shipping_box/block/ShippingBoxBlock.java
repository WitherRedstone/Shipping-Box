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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShippingBoxBlock extends BaseEntityBlock {
    public static final MapCodec<ShippingBoxBlock> CODEC = simpleCodec(ShippingBoxBlock::new);

    public ShippingBoxBlock(Properties properties) {
        super(properties);
    }

    /**
     * 获取方块的编解码器
     *
     * @return 方块属性的MapCodec编解码器实例
     */
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * 获取方块的渲染形状
     *
     * @param state 方块状态对象
     * @return 渲染形状，返回MODEL表示使用模型文件进行渲染
     */
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 创建方块实体实例
     *
     * @param pos 方块位置坐标
     * @param state 方块状态
     * @return 新创建的售货箱方块实体，如果无法创建则返回null
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShippingBoxBlockEntity(pos, state);
    }

    /**
     * 处理方块被玩家右键点击的交互
     * 播放声音并打开GUI界面
     *
     * @param state 方块状态
     * @param level 游戏世界实例
     * @param pos 方块位置坐标
     * @param player 交互的玩家
     * @param hitResult 点击结果信息
     * @return 交互结果，成功时返回sidedSuccess
     */
    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
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

    /**
     * 处理方块被移除时的逻辑
     * 当方块被替换或破坏时，掉落其中存储的物品
     *
     * @param state 当前方块状态
     * @param level 游戏世界实例
     * @param pos 方块位置坐标
     * @param newState 新的方块状态
     * @param isMoving 是否正在被移动（如活塞推动）
     */
    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean isMoving) {
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

    /**
     * 获取方块实体的刻更新器
     * 只在服务端为售货箱方块实体提供tick方法调用
     *
     * @param <T> 方块实体类型参数
     * @param level 游戏世界实例
     * @param state 方块状态
     * @param type 方块实体类型
     * @return 服务端返回刻更新器，客户端返回null
     */
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
