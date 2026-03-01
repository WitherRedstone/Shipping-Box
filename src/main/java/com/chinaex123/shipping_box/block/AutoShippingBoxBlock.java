package com.chinaex123.shipping_box.block;

import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class AutoShippingBoxBlock extends BaseEntityBlock {

    public static final MapCodec<AutoShippingBoxBlock> CODEC = simpleCodec(AutoShippingBoxBlock::new);

    public MapCodec<AutoShippingBoxBlock> codec() {
        return CODEC;
    }

    public AutoShippingBoxBlock(Properties properties) {
        super(properties);
    }

    /**
     * 获取方块的渲染形状。
     * <p>
     * 此方法指定自动售货箱方块使用模型渲染方式。
     *
     * @param state 方块状态对象
     * @return 渲染形状枚举值，此处返回MODEL表示使用模型渲染
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 创建新的方块实体实例。
     * <p>
     * 当自动售货箱方块被放置时，此方法会被调用以创建对应的方块实体。
     *
     * @param pos   方块位置坐标
     * @param state 方块状态对象
     * @return 新创建的AutoShippingBoxBlockEntity实例
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoShippingBoxBlockEntity(pos, state);
    }

    /**
     * 获取方块实体的更新器。
     * <p>
     * 此方法为服务器端的自动售货箱方块实体提供周期性更新功能，
     * 主要用于处理每日兑换逻辑。
     *
     * @param level 当前世界对象
     * @param state 方块状态对象
     * @param type  方块实体类型
     * @param <T>   方块实体泛型参数
     * @return 方块实体更新器，如果条件不匹配则返回null
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 简单直接的实现
        if (type == ModBlockEntities.AUTOMATED_SHIPPING_BOX.get() && !level.isClientSide) {
            return (level1, pos, state1, blockEntity) -> ((AutoShippingBoxBlockEntity) blockEntity).tick();
        }
        return null;
    }

    /**
     * 处理自动售货箱方块被放置时的逻辑。
     * <p>
     * 此方法在方块被放置到世界中时调用，负责处理玩家绑定逻辑。
     * 如果放置的方块物品包含绑定信息，则恢复原有的绑定关系；
     * 否则将当前放置玩家绑定到该自动售货箱。
     *
     * @param level  当前世界对象
     * @param pos    方块被放置的位置
     * @param state  方块状态对象
     * @param placer 放置方块的实体（通常是玩家）
     * @param stack  被放置的方块物品堆
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AutoShippingBoxBlockEntity autoBox) {
                // 检查物品是否有绑定信息
                CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                UUID boundPlayerUUID = null;
                String boundPlayerName = "Unknown";

                if (customData != null) {
                    CompoundTag tag = customData.copyTag();
                    if (tag.contains("BoundPlayerUUID")) {
                        try {
                            boundPlayerUUID = UUID.fromString(tag.getString("BoundPlayerUUID"));
                        } catch (IllegalArgumentException e) {
                            // UUID格式错误，忽略
                        }
                    }
                    // 读取玩家名字
                    if (tag.contains("BoundPlayerName")) {
                        boundPlayerName = tag.getString("BoundPlayerName");
                    }
                }

                if (boundPlayerUUID != null) {
                    // 使用物品中保存的绑定信息
                    autoBox.bindPlayer(boundPlayerUUID);

                    // 显示绑定信息，使用读取到的玩家名字
                    player.displayClientMessage(
                            Component.translatable("message.shipping_box.auto_box_already_bound",
                                    Component.literal(boundPlayerName).withStyle(style -> style.withColor(0xFFAA00))),
                            true
                    );
                } else {
                    // 没有绑定信息，正常绑定当前玩家
                    autoBox.bindPlayer(player.getUUID());
                    player.displayClientMessage(
                            Component.translatable("message.shipping_box.auto_box_bound",
                                    player.getName().copy().withStyle(style -> style.withColor(0xFFAA00))),
                            true
                    );
                }
            }
        }
    }

    /**
     * 处理玩家右键点击自动售货箱方块的交互逻辑。
     * <p>
     * 此方法在玩家右键点击自动售货箱时调用，负责验证玩家访问权限
     * 并打开相应的GUI界面。只有绑定的玩家或未绑定的自动售货箱
     * 才能被访问。
     *
     * @param state  方块状态对象
     * @param level  当前世界对象
     * @param pos    方块位置坐标
     * @param player 执行交互的玩家
     * @param hit    点击结果信息
     * @return 交互结果枚举值，CONSUME表示消耗此次交互
     */
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

    /**
     * 处理自动售货箱方块被破坏时的逻辑。
     * <p>
     * 此方法在方块被破坏时调用，负责保存绑定玩家信息到掉落物品中，
     * 并处理物品掉落逻辑。通过CUSTOM_DATA组件存储UUID和玩家名字，
     * 通过LORE组件显示友好的绑定信息提示。
     *
     * @param state    被破坏前的方块状态
     * @param level    当前世界对象
     * @param pos      方块位置坐标
     * @param newState 破坏后的新方块状态
     * @param isMoving 是否由活塞等机械装置移动
     */
    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AutoShippingBoxBlockEntity autoBox) {
                if (level instanceof ServerLevel) {
                    // 自动售货箱必定有绑定信息
                    UUID boundPlayer = autoBox.getBoundPlayerUUID();

                    // 创建带绑定信息的掉落物品
                    ItemStack dropStack = new ItemStack(this);

                    if (boundPlayer != null) {
                        // 获取玩家名字（如果在线的话）
                        String playerName = "Unknown Player";
                        Player player = level.getPlayerByUUID(boundPlayer);
                        if (player != null) {
                            playerName = player.getName().getString();
                        }

                        // 使用CUSTOM_DATA组件存储UUID和玩家名字
                        CompoundTag customData = new CompoundTag();
                        customData.putString("BoundPlayerUUID", boundPlayer.toString());
                        customData.putString("BoundPlayerName", playerName);
                        dropStack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));

                        // 使用LORE组件显示友好的绑定信息
                        dropStack.set(DataComponents.LORE, new ItemLore(List.of(
                                Component.translatable("tooltip.shipping_box.bound_to_player_formatted",
                                                Component.literal(playerName).withStyle(ChatFormatting.YELLOW))
                                        .withStyle(ChatFormatting.GRAY)
                        )));
                    }

                    // 掉落方块
                    //Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), dropStack);
                    Block.popResource(level, pos, dropStack);

                    // 掉落存储的物品
                    Containers.dropContents(level, pos, autoBox);

                    // 直接返回，避免重复掉落
                    return;
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
