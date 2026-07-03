package com.chinaex123.shipping_box.block.entity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Transfer API wrapper that preserves the old "only extract exchanged outputs" rule. */
public final class AutoShippingBoxResourceHandler implements ResourceHandler<ItemResource> {
    private final AutoShippingBoxBlockEntity blockEntity;
    private final ResourceHandler<ItemResource> delegate;

    public AutoShippingBoxResourceHandler(AutoShippingBoxBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.delegate = VanillaContainerWrapper.of(blockEntity);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return delegate.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return delegate.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return delegate.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return delegate.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        ItemStack current = blockEntity.getItem(index);
        if (!blockEntity.canExternalExtract(index, current) || !resource.matches(current)) {
            return 0;
        }
        return delegate.extract(index, resource, amount, transaction);
    }
}
