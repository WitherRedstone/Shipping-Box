package com.chinaex123.shipping_box.block.entity;

import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.menu.AutoShippingBoxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public class AutoShippingBoxBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    /** 存储物品列表 */
    private NonNullList<ItemStack> items;

    /** 绑定的玩家UUID */
    private UUID boundPlayerUUID;

    /** 上次兑换日期 */
    private long lastExchangeDay = -1L;

    /** 记录每个槽位是否包含兑换后的物品 */
    private final Map<Integer, Boolean> slotIsExchanged = new HashMap<>();

    // 在构造函数中初始化
    public AutoShippingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTOMATED_SHIPPING_BOX.get(), pos, state);
        this.items = NonNullList.withSize(54, ItemStack.EMPTY);
    }

    // 修改漏斗输出控制
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // 只允许输出兑换完成的物品
        return slotIsExchanged.getOrDefault(slot, false);
    }

    // 允许所有方向输入
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return true;
    }

    /**
     * 执行物品兑换的核心逻辑方法
     */
    private void performExchange(long currentDay) {
        ExchangeManager.performExchange(items, level, worldPosition, boundPlayerUUID);

        // 标记所有槽位为已兑换状态
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                slotIsExchanged.put(i, true);
            }
        }

        lastExchangeDay = currentDay;
        setChanged();
    }

    // NBT数据保存和加载
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("LastExchangeDay", lastExchangeDay);

        // 保存兑换状态标记
        CompoundTag exchangedTag = new CompoundTag();
        for (Map.Entry<Integer, Boolean> entry : slotIsExchanged.entrySet()) {
            exchangedTag.putBoolean(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("SlotExchanged", exchangedTag);

        if (boundPlayerUUID != null) {
            tag.putString("BoundPlayer", boundPlayerUUID.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(54, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);

        if (tag.contains("LastExchangeDay")) {
            lastExchangeDay = tag.getLong("LastExchangeDay");
        }

        // 加载兑换状态标记
        if (tag.contains("SlotExchanged")) {
            CompoundTag exchangedTag = tag.getCompound("SlotExchanged");
            for (String key : exchangedTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    boolean isExchanged = exchangedTag.getBoolean(key);
                    slotIsExchanged.put(slot, isExchanged);
                } catch (NumberFormatException e) {
                    // 静默处理错误
                }
            }
        }

        if (tag.contains("BoundPlayer")) {
            try {
                boundPlayerUUID = UUID.fromString(tag.getString("BoundPlayer"));
            } catch (IllegalArgumentException e) {
                boundPlayerUUID = null;
            }
        }
    }

    @Override
    public void clearContent() {
        items.clear();
        slotIsExchanged.clear();
        setChanged();
    }


    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        this.items = items;
        setChanged();
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.shipping_box.auto_shipping_box");
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

    // 添加漏斗接口必需的方法
    @Override
    public int[] getSlotsForFace(Direction side) {
        return IntStream.range(0, getContainerSize()).toArray();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    // 添加玩家交互相关方法
    public void bindPlayer(UUID playerUUID) {
        this.boundPlayerUUID = playerUUID;
        setChanged();
    }

    public boolean canPlayerAccess(Player player) {
        return boundPlayerUUID == null || player.getUUID().equals(boundPlayerUUID);
    }

    // 添加tick方法
    public void tick() {
        if (level == null || level.isClientSide) return;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;

        // 检查是否存在物品
        boolean hasItems = false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }

        if (!hasItems) return;

        // 检测兑换时间窗口
        if (timeOfDay >= 0 && timeOfDay <= 180) {
            long timeSinceLastExchange = dayTime - (lastExchangeDay * 24000);

            if (timeSinceLastExchange >= 24000 || lastExchangeDay == -1L) {
                try {
                    performExchange(dayTime / 24000);
                } catch (Exception e) {
                    // 异常处理
                }
            }
        }
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        return new AutoShippingBoxMenu(id, inventory, this);
    }
}
