package com.chinaex123.shipping_box.block.entity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

/**
 * 自动售货箱的资源处理器。
 * <p>
 * 实现 NeoForge 的 ResourceHandler 接口，作为自动售货箱对外暴露的 transfer 能力。
 * 内部通过 VanillaContainerWrapper 包装方块实体，将大部分操作委托给原版容器行为；
 * 仅在提取操作上施加额外限制：只有已完成兑换且与原型匹配的物品才允许被外部提取。
 */
public final class AutoShippingBoxResourceHandler implements ResourceHandler<ItemResource> {

    /** 关联的自动售货箱方块实体 */
    private final AutoShippingBoxBlockEntity blockEntity;
    /** 委托的原版容器资源处理器 */
    private final ResourceHandler<ItemResource> delegate;

    /**
     * 构造自动售货箱资源处理器。
     *
     * @param blockEntity 关联的自动售货箱方块实体
     */
    public AutoShippingBoxResourceHandler(AutoShippingBoxBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.delegate = VanillaContainerWrapper.of(blockEntity);
    }

    /**
     * 获取槽位数量。
     *
     * @return 槽位总数
     */
    @Override
    public int size() {
        return delegate.size();
    }

    /**
     * 获取指定槽位的资源。
     *
     * @param index 槽位索引
     * @return 该槽位的资源
     */
    @Override
    public @NonNull ItemResource getResource(int index) {
        return delegate.getResource(index);
    }

    /**
     * 获取指定槽位的资源数量。
     *
     * @param index 槽位索引
     * @return 资源数量
     */
    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    /**
     * 获取指定槽位对指定资源的容量。
     *
     * @param index    槽位索引
     * @param resource 资源
     * @return 容量
     */
    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return delegate.getCapacityAsLong(index, resource);
    }

    /**
     * 检查指定槽位是否允许存放指定资源。
     *
     * @param index    槽位索引
     * @param resource 资源
     * @return 允许存放返回 true
     */
    @Override
    public boolean isValid(int index, ItemResource resource) {
        return delegate.isValid(index, resource);
    }

    /**
     * 向指定槽位插入资源。
     * <p>
     * 直接委托给原版容器处理器，不做额外限制。
     *
     * @param index       槽位索引
     * @param resource    要插入的资源
     * @param amount      插入数量
     * @param transaction 事务上下文
     * @return 实际插入的数量
     */
    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return delegate.insert(index, resource, amount, transaction);
    }

    /**
     * 从指定槽位提取资源。
     * <p>
     * 在委托提取前进行额外校验：仅当槽位允许外部提取
     * （已兑换且与原型匹配）且资源与当前物品匹配时才允许提取。
     *
     * @param index       槽位索引
     * @param resource    要提取的资源
     * @param amount      提取数量
     * @param transaction 事务上下文
     * @return 实际提取的数量，未通过校验时返回 0
     */
    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        ItemStack current = blockEntity.getItem(index);
        // 校验槽位是否允许外部提取，且请求的资源与当前物品匹配
        if (!blockEntity.canExternalExtract(index, current) || !resource.matches(current)) {
            return 0;
        }
        return delegate.extract(index, resource, amount, transaction);
    }
}