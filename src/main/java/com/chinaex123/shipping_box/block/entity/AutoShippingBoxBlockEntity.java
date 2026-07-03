package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.init.ModBlockEntities;
import com.chinaex123.shipping_box.menu.AutoShippingBoxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 自动售货箱的方块实体类 — 26.2 适配。
 *
 * 26.2 里物品存储继续用内部 NonNullList<ItemStack>(保持旧模式),
 * 能力(Capability)层用 VanillaContainerWrapper 包装后暴露为新的 ResourceHandler API。
 */
public class AutoShippingBoxBlockEntity extends BaseContainerBlockEntity implements MenuProvider {

    /** 54 槽内部物品存储 */
    private final NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);

    private UUID boundPlayerUUID;
    private long lastExchangeDay = -1L;
    private final Map<Integer, Boolean> slotIsExchanged = new HashMap<>();
    private final Map<Integer, ItemStack> exchangedItemPrototype = new HashMap<>();
    private boolean skipResetDuringExchange = false;
    private ResourceHandler<ItemResource> transferHandler;

    public AutoShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTOMATED_SHIPPING_BOX.get(), pos, state);
    }

    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        for (int i = 0; i < Math.min(54, items.size()); i++) this.items.set(i, items.get(i));
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.shipping_box.auto_shipping_box");
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory playerInventory) {
        return new AutoShippingBoxMenu(id, playerInventory, this);
    }

    @Override
    public int getContainerSize() {
        return 54;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int count) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.split(count);
        if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
        setChanged();
        return result;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        setItem(slot, stack, false);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack, boolean insideTransaction) {
        items.set(slot, stack);
        if (!insideTransaction && !skipResetDuringExchange) {
            slotIsExchanged.put(slot, false);
            exchangedItemPrototype.remove(slot);
        }
        if (!insideTransaction) {
            setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int index, @NotNull ItemStack stack) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
        slotIsExchanged.clear();
        exchangedItemPrototype.clear();
    }

    public UUID getBoundPlayerUUID() { return boundPlayerUUID; }
    public void bindPlayer(UUID playerUUID) { this.boundPlayerUUID = playerUUID; setChanged(); }

    public boolean canPlayerAccess(Player player) {
        return boundPlayerUUID == null || boundPlayerUUID.equals(player.getUUID());
    }

    public boolean isSlotExchanged(int slot) { return slotIsExchanged.getOrDefault(slot, false); }
    public Set<Integer> getExchangedSlots() {
        Set<Integer> exchanged = new HashSet<>();
        slotIsExchanged.forEach((slot, value) -> {
            if (value) exchanged.add(slot);
        });
        return exchanged;
    }

    public boolean canExternalExtract(int slot, ItemStack currentStack) {
        if (!slotIsExchanged.getOrDefault(slot, false) || currentStack.isEmpty()) {
            return false;
        }
        ItemStack prototype = exchangedItemPrototype.get(slot);
        return prototype != null && ItemStack.isSameItemSameComponents(currentStack, prototype);
    }

    public ResourceHandler<ItemResource> getTransferHandler() {
        if (transferHandler == null) {
            transferHandler = new AutoShippingBoxResourceHandler(this);
        }
        return transferHandler;
    }

    public void forceExchange() {
        if (level != null && !level.isClientSide()) {
            performExchange(level.getLevelData().getGameTime() / 24000);
            lastExchangeDay = level.getLevelData().getGameTime() / 24000;
            setChanged();
        }
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;
        long currentDay = level.getLevelData().getGameTime() / 24000;
        if (currentDay > lastExchangeDay && lastExchangeDay != -1L) {
            performExchange(currentDay);
        } else if (lastExchangeDay == -1L) {
            lastExchangeDay = currentDay;
        }
    }

    private void performExchange(long currentDay) {
        List<Integer> slotsWithItems = new ArrayList<>();
        List<ItemStack> itemsToProcess = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                slotsWithItems.add(i);
                itemsToProcess.add(stack.copy());
            }
        }
        if (itemsToProcess.isEmpty()) return;

        NonNullList<ItemStack> processedItems = NonNullList.withSize(itemsToProcess.size(), ItemStack.EMPTY);
        for (int i = 0; i < itemsToProcess.size(); i++) processedItems.set(i, itemsToProcess.get(i));
        try {
            ExchangeManager.performExchange(processedItems, level, worldPosition, boundPlayerUUID);
        } catch (Exception ignored) {}

        for (int slot : slotsWithItems) slotIsExchanged.put(slot, false);
        skipResetDuringExchange = true;

        for (int i = 0; i < slotsWithItems.size() && i < processedItems.size(); i++) {
            int si = slotsWithItems.get(i);
            ItemStack newStack = processedItems.get(i);
            items.set(si, newStack);
            ItemStack oldStack = itemsToProcess.get(i);
            if (!ItemStack.matches(oldStack, newStack) && !newStack.isEmpty()) {
                slotIsExchanged.put(si, true);
                exchangedItemPrototype.put(si, newStack.copy());
            } else {
                slotIsExchanged.put(si, false);
                exchangedItemPrototype.remove(si);
            }
        }
        for (int i = processedItems.size(); i < slotsWithItems.size(); i++) {
            int si = slotsWithItems.get(i);
            items.set(si, ItemStack.EMPTY);
            slotIsExchanged.put(si, false);
            exchangedItemPrototype.remove(si);
        }

        skipResetDuringExchange = false;
        lastExchangeDay = currentDay;
        setChanged();
    }

    @Override
    protected void saveAdditional(@NotNull ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putLong("LastExchangeDay", lastExchangeDay);
        if (boundPlayerUUID != null) {
            out.putString("BoundPlayerUUID", boundPlayerUUID.toString());
        }

        ValueOutput exchangedSlots = out.child("SlotIsExchanged");
        slotIsExchanged.forEach((slot, exchanged) -> exchangedSlots.putBoolean(String.valueOf(slot), exchanged));

        ValueOutput prototypes = out.child("ExchangedItemPrototype");
        exchangedItemPrototype.forEach((slot, stack) ->
                prototypes.store(String.valueOf(slot), ItemStack.OPTIONAL_CODEC, stack));
    }

    @Override
    protected void loadAdditional(@NotNull ValueInput in) {
        super.loadAdditional(in);
        items.clear();
        ContainerHelper.loadAllItems(in, items);
        lastExchangeDay = in.getLongOr("LastExchangeDay", -1L);
        String boundPlayer = in.getStringOr("BoundPlayerUUID", "");
        boundPlayerUUID = boundPlayer.isEmpty() ? null : UUID.fromString(boundPlayer);

        slotIsExchanged.clear();
        in.child("SlotIsExchanged").ifPresent(slotsIn -> {
            for (String key : slotsIn.keySet()) {
                try {
                    slotIsExchanged.put(Integer.parseInt(key), slotsIn.getBooleanOr(key, false));
                } catch (NumberFormatException ignored) {
                }
            }
        });

        exchangedItemPrototype.clear();
        in.child("ExchangedItemPrototype").ifPresent(prototypesIn -> {
            for (String key : prototypesIn.keySet()) {
                try {
                    prototypesIn.read(key, ItemStack.OPTIONAL_CODEC)
                            .filter(stack -> !stack.isEmpty())
                            .ifPresent(stack -> exchangedItemPrototype.put(Integer.parseInt(key), stack));
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }
}
