package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.event.ExchangeRule;
import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ShippingBoxBlockEntity extends BaseContainerBlockEntity {
    /** 存储物品列表 */
    private NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);

    /** 上次兑换日期 */
    private long lastExchangeDay = -1L;

    /** 记录每个槽位最后放置物品的玩家UUID */
    private Map<Integer, UUID> slotOwners = new HashMap<>();

    /** 记录玩家放置的物品数量 */
    private Map<UUID, Integer> playerItemCounts = new HashMap<>();

    // 添加静态变量记录当前操作玩家
    private static ThreadLocal<ServerPlayer> currentPlayer = new ThreadLocal<>();

    /**
     * 设置当前操作玩家（由外部调用）
     */
    public static void setCurrentPlayer(ServerPlayer player) {
        currentPlayer.set(player);
    }

    /**
     * 清除当前操作玩家
     */
    public static void clearCurrentPlayer() {
        currentPlayer.remove();
    }

    public ShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIPPING_BOX_BE.get(), pos, state);
    }

    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
        setChanged();
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("container.shipping_box.shipping_box");
    }

    /**
     * 创建容器菜单 - 这是必须实现的抽象方法
     */
    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x6, id, inventory, this, 6);
    }

    @Override
    public int getContainerSize() {
        return 54;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            slotOwners.remove(slot);
            setChanged();
        }
        return result;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        slotOwners.remove(slot);
        return result;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        ItemStack oldStack = items.get(slot);
        items.set(slot, stack);

        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }

        // 记录玩家信息
        if (level != null && !level.isClientSide && !ItemStack.matches(oldStack, stack)) {
            ServerPlayer player = currentPlayer.get();
            if (player != null) {
                setSlotOwner(slot, player.getUUID());
            }
        }

        setChanged();
    }

    /**
     * 记录玩家在指定槽位放置物品
     */
    public void setSlotOwner(int slot, UUID playerUUID) {
        if (playerUUID != null) {
            slotOwners.put(slot, playerUUID);
            playerItemCounts.put(playerUUID, playerItemCounts.getOrDefault(playerUUID, 0) + 1);
            setChanged();
        }
    }

    @Override
    public void clearContent() {
        items.clear();
        slotOwners.clear();
        playerItemCounts.clear();
        setChanged();
    }

    public void tick() {
        if (level == null || level.isClientSide) return;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;
        long currentDay = dayTime / 24000;

        boolean hasItems = false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }

        if (hasItems && timeOfDay >= 0 && timeOfDay <= 180 && lastExchangeDay < currentDay) {
            try {
                performExchange(currentDay);
                lastExchangeDay = currentDay;
                setChanged();
            } catch (Exception e) {
                // 异常处理保持简洁
            }
        }
    }

    private void performExchange(long currentDay) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        List<ItemStack> itemsToProcess = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                itemsToProcess.add(stack.copy());
            }
        }

        if (itemsToProcess.isEmpty()) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }

        List<ItemStack> results = new ArrayList<>();
        boolean exchanged = false;
        boolean hadSuccessfulExchange = false;

        do {
            exchanged = false;
            ExchangeRule rule = ExchangeRecipeManager.findMatchingRule(itemsToProcess);

            if (rule != null) {
                int maxExchanges = getMaxExchanges(rule, itemsToProcess);

                if (maxExchanges > 0) {
                    for (int i = 0; i < maxExchanges; i++) {
                        itemsToProcess = ExchangeRecipeManager.consumeInputs(rule, itemsToProcess);
                    }

                    // 修正：使用 OutputItem 的 getResultStack() 方法
                    ItemStack output = rule.getOutputItem().getResultStack().copy();
                    output.setCount(rule.getOutputItem().getCount() * maxExchanges);
                    results.add(output);

                    exchanged = true;
                    hadSuccessfulExchange = true;
                }
            }
        } while (exchanged);

        results.addAll(itemsToProcess);

        for (int i = 0; i < Math.min(results.size(), 54); i++) {
            items.set(i, results.get(i));
        }

        lastExchangeDay = currentDay;
        setChanged();

        if (hadSuccessfulExchange) {
            // 全局声音播放（如果需要的话）
            serverLevel.playSound(null, worldPosition,
                    SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                    SoundSource.BLOCKS,
                    0.5F, 1.0F);

            notifyPlayersOfSuccess(serverLevel);
        }
    }

    /**
     * 向放置物品的玩家发送成功通知
     */
    private void notifyPlayersOfSuccess(ServerLevel serverLevel) {
        Set<UUID> playerUUIDs = new HashSet<>(slotOwners.values());

        if (playerUUIDs.isEmpty()) {
            return;
        }

        for (UUID playerUUID : playerUUIDs) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                // 向特定玩家播放声音（无视距离）
                player.playNotifySound(
                        SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.note_block.bell")),
                        SoundSource.BLOCKS,
                        0.5F,
                        1.0F
                );

                // 发送成功消息（无视距离）
                ShippingBoxNetworking.sendSuccessMessage(player);
            }
        }

        // 清除记录
        slotOwners.clear();
        playerItemCounts.clear();
    }

    private int getMaxExchanges(ExchangeRule rule, List<ItemStack> availableStacks) {
        int maxExchanges = Integer.MAX_VALUE;

        for (ExchangeRule.InputItem required : rule.getInputs()) {
            int totalCount = 0;
            for (ItemStack stack : availableStacks) {
                if (required.matches(stack)) {
                    totalCount += stack.getCount();
                }
            }

            int possibleExchanges = totalCount / required.getCount();
            if (possibleExchanges < maxExchanges) {
                maxExchanges = possibleExchanges;
            }
        }

        return maxExchanges;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("LastExchangeDay", lastExchangeDay);

        // 保存槽位所有者信息
        CompoundTag ownersTag = new CompoundTag();
        for (Map.Entry<Integer, UUID> entry : slotOwners.entrySet()) {
            ownersTag.putString(String.valueOf(entry.getKey()), entry.getValue().toString());
        }
        tag.put("SlotOwners", ownersTag);

        // 保存玩家物品计数
        CompoundTag countsTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : playerItemCounts.entrySet()) {
            countsTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("PlayerCounts", countsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(54, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);

        if (tag.contains("LastExchangeDay")) {
            lastExchangeDay = tag.getLong("LastExchangeDay");
        }

        // 加载槽位所有者信息
        if (tag.contains("SlotOwners")) {
            CompoundTag ownersTag = tag.getCompound("SlotOwners");
            for (String key : ownersTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    UUID uuid = UUID.fromString(ownersTag.getString(key));
                    slotOwners.put(slot, uuid);
                } catch (NumberFormatException e) {
                    // 静默处理解析错误
                } catch (IllegalArgumentException e) {
                    // 静默处理UUID错误
                }
            }
        }

        // 加载玩家物品计数
        if (tag.contains("PlayerCounts")) {
            CompoundTag countsTag = tag.getCompound("PlayerCounts");
            for (String key : countsTag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int count = countsTag.getInt(key);
                    playerItemCounts.put(uuid, count);
                } catch (IllegalArgumentException e) {
                    // 静默处理错误
                }
            }
        }
    }
}
