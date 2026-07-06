package com.chinaex123.shipping_box.block;

import com.chinaex123.shipping_box.block.entity.ShippingBoxBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 普通售货箱方块类；
 * 负处理方块的右键交互、破坏掉落和刻更新器注册
 *
 * 核心特性：
 * 1. 所有玩家都可以使用普通售货箱（无绑定限制）
 * 2. 支持每日定时兑换
 * 3. 使用玩家独立存储（每个玩家拥有自己的54格存储空间）
 * 4. 与自动售货箱不同，普通售货箱不限制访问权限
 */
@ParametersAreNonnullByDefault
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
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * 获取方块的渲染形状
     *
     * @param state 方块状态对象
     * @return 渲染形状，返回MODEL表示使用模型文件进行渲染
     */
    @Override
    public @NotNull RenderShape getRenderShape(BlockState state) {
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
     * @return 交互结果，成功时返回sidedSuccess
     */
    @Override
    public @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // 客户端只返回成功，不执行实际逻辑
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ShippingBoxBlockEntity shippingBox) {
                // ========== 打开GUI菜单 ==========
                // 通过缓冲区传递方块位置和玩家UUID
                // 玩家UUID用于在菜单中加载该玩家的个人存储
                serverPlayer.openMenu(shippingBox, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeUUID(serverPlayer.getUUID());
                });
            }

            // ========== 播放打开音效 ==========
            // 使用桶打开的音效，音调随机微调增加真实感
            level.playSound(
                    null, pos,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.barrel.open")),
                    SoundSource.BLOCKS, 0.5F,
                    level.random.nextFloat() * 0.1F + 0.9F
            );
        }

        return InteractionResult.CONSUME;
    }

    /**
     * 处理方块被移除时的逻辑
     * 当方块被替换或破坏时，掉落破坏玩家的个人存储物品
     *
     * @param state 当前方块状态
     * @param level 游戏世界实例
     * @param pos 方块位置坐标
     * @param newState 新的方块状态
     * @param isMoving 是否正在被移动（如活塞推动）
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 只有当方块类型发生变化时才执行移除逻辑（即被真正破坏）
        if (!state.is(newState.getBlock())) {
            // 调用父类方法处理基础掉落
            // 由于物品存储在GlobalPlayerStorage中，这里不需要额外处理
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
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
