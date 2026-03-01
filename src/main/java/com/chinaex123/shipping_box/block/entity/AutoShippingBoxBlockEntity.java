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

    public UUID getBoundPlayerUUID() {
        return boundPlayerUUID;
    }

    /**
     * 检查是否可以通过指定面取出指定槽位的物品
     * <p>
     * 实现WorldlyContainer接口的方法，控制漏斗等自动化设备从该方块实体
     * 中提取物品的权限。只有当槽位中的物品已完成兑换流程时才允许提取。
     *
     * @param slot 物品槽位索引
     * @param stack 要检查的物品堆栈（在此实现中未使用）
     * @param side 提取物品的方向面
     * @return 如果该槽位的物品已完成兑换则返回true，否则返回false
     */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // 只允许输出兑换完成的物品
        return slotIsExchanged.getOrDefault(slot, false);
    }

    /**
     * 检查是否可以通过指定面放入物品到指定槽位
     * <p>
     * 实现WorldlyContainer接口的方法，控制外部设备向该方块实体
     * 输入物品的权限。当前实现允许从任何方向向任何槽位放入物品。
     *
     * @param slot 目标槽位索引
     * @param stack 要放入的物品堆栈
     * @param side 输入物品的方向面，可能为null
     * @return 总是返回true，表示允许从任何方向放入任何物品
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return true;
    }

    /**
     * 执行物品兑换的核心逻辑方法
     * <p>
     * 调用全局兑换管理器处理当前存储中的所有物品，完成兑换后标记
     * 所有非空槽位为已兑换状态，并更新最后兑换日期。
     *
     * @param currentDay 当前游戏天数，用于记录兑换时间戳
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

    /**
     * 将自动售货箱方块实体的数据保存到NBT标签中。
     * <p>
     * 此方法负责序列化方块实体的所有重要数据，包括物品库存、上次兑换日期、
     * 各槽位的兑换状态以及绑定玩家的UUID。这些数据将在世界保存时持久化，
     * 并在世界加载时通过loadAdditional方法恢复。
     *
     * @param tag       用于存储序列化数据的CompoundTag对象
     * @param registries 注册表提供者，用于处理复杂数据类型的序列化
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存物品库存数据
        ContainerHelper.saveAllItems(tag, items, registries);

        // 保存上次兑换日期
        tag.putLong("LastExchangeDay", lastExchangeDay);

        // 保存各槽位的兑换状态标记
        CompoundTag exchangedTag = new CompoundTag();
        for (Map.Entry<Integer, Boolean> entry : slotIsExchanged.entrySet()) {
            exchangedTag.putBoolean(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put("SlotExchanged", exchangedTag);

        // 保存绑定玩家的UUID（如果存在）
        if (boundPlayerUUID != null) {
            tag.putString("BoundPlayer", boundPlayerUUID.toString());
        }
    }

    /**
     * 从NBT标签中加载自动售货箱方块实体的数据。
     * <p>
     * 此方法负责反序列化之前保存的方块实体数据，包括物品库存、上次兑换日期、
     * 各槽位的兑换状态以及绑定玩家的UUID。这些数据通常在世界加载时从磁盘读取。
     *
     * @param tag       包含序列化数据的CompoundTag对象
     * @param registries 注册表提供者，用于处理复杂数据类型的反序列化
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        // 初始化并加载物品库存
        items = NonNullList.withSize(54, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);

        // 加载上次兑换日期
        if (tag.contains("LastExchangeDay")) {
            lastExchangeDay = tag.getLong("LastExchangeDay");
        }

        // 加载各槽位的兑换状态标记
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

        // 加载绑定玩家的UUID
        if (tag.contains("BoundPlayer")) {
            try {
                boundPlayerUUID = UUID.fromString(tag.getString("BoundPlayer"));
            } catch (IllegalArgumentException e) {
                boundPlayerUUID = null;
            }
        }
    }

    /**
     * 清空自动售货箱的内容。
     * <p>
     * 此方法会清空方块实体中的所有物品库存和兑换状态标记，
     * 并标记方块实体为已更改状态，以便在下次保存时写入磁盘。
     */
    @Override
    public void clearContent() {
        items.clear();
        slotIsExchanged.clear();
        setChanged();
    }

    /**
     * 获取自动售货箱的物品列表。
     * <p>
     * 此方法返回方块实体内部存储的物品库存列表。
     *
     * @return 包含所有物品的NonNullList<ItemStack>
     */
    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    /**
     * 设置自动售货箱的物品列表。
     * <p>
     * 此方法用于更新方块实体的物品库存，并标记方块实体为已更改状态。
     *
     * @param items 新的物品列表
     */
    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        this.items = items;
        setChanged();
    }

    /**
     * 获取自动售货箱的默认显示名称。
     * <p>
     * 此方法返回用于GUI界面显示的本地化名称。
     *
     * @return 本地化的组件名称
     */
    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.shipping_box.auto_shipping_box");
    }

    /**
     * 获取自动售货箱的容器大小。
     * <p>
     * 此方法返回自动售货箱物品栏的槽数量。
     *
     * @return 容器大小（槽数）
     */
    @Override
    public int getContainerSize() {
        return 54;
    }

    /**
     * 检查自动售货箱是否为空。
     * <p>
     * 此方法遍历所有物品槽位，如果任何一个槽位包含物品则返回false，
     * 只有当所有槽位都为空时才返回true。
     *
     * @return 如果所有槽位都为空则返回true，否则返回false
     */
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取指定方向面上可用的物品槽位索引数组。
     * <p>
     * 实现漏斗接口所需的方法，返回所有槽位的索引数组，
     * 允许从任何方向访问所有槽位。
     *
     * @param side 方向枚举值
     * @return 包含所有槽位索引的整型数组
     */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return IntStream.range(0, getContainerSize()).toArray();
    }

    /**
     * 检查指定槽位是否可以放置指定物品。
     * <p>
     * 实现漏斗接口所需的方法，当前实现允许放置任何物品到任何槽位。
     *
     * @param slot  槽位索引
     * @param stack 要放置的物品堆
     * @return 总是返回true，表示可以放置
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    /**
     * 绑定玩家到自动售货箱。
     * <p>
     * 将指定玩家的UUID与自动售货箱关联，用于访问权限控制。
     *
     * @param playerUUID 要绑定的玩家UUID
     */
    public void bindPlayer(UUID playerUUID) {
        this.boundPlayerUUID = playerUUID;
        setChanged();
    }

    /**
     * 检查玩家是否可以访问自动售货箱。
     * <p>
     * 如果自动售货箱未绑定任何玩家（boundPlayerUUID为null），
     * 或者访问玩家的UUID与绑定的玩家UUID匹配，则允许访问。
     *
     * @param player 要检查访问权限的玩家
     * @return 如果玩家可以访问则返回true，否则返回false
     */
    public boolean canPlayerAccess(Player player) {
        return boundPlayerUUID == null || player.getUUID().equals(boundPlayerUUID);
    }

    /**
     * 自动售货箱的周期性更新方法。
     * <p>
     * 此方法在服务器端每tick调用，负责检测是否满足兑换条件并执行兑换操作。
     * 兑换时间窗口为每日的0-180游戏刻（即黎明时分），且每天只能兑换一次。
     */
    public void tick() {
        if (level == null || level.isClientSide) return;

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;

        // 检查容器中是否存在物品
        boolean hasItems = false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                hasItems = true;
                break;
            }
        }

        if (!hasItems) return;

        // 检测兑换时间窗口（每日0-180游戏刻）
        if (timeOfDay >= 0 && timeOfDay <= 180) {
            long timeSinceLastExchange = dayTime - (lastExchangeDay * 24000);

            // 检查是否距离上次兑换已超过一天或从未兑换过
            if (timeSinceLastExchange >= 24000 || lastExchangeDay == -1L) {
                try {
                    performExchange(dayTime / 24000);
                } catch (Exception e) {
                    // 异常处理
                }
            }
        }
    }

    /**
     * 创建自动售货箱的容器菜单实例
     * <p>
     * 实现BaseContainerBlockEntity抽象方法，为自动售货箱方块实体
     * 创建对应的GUI菜单界面，供玩家与容器进行交互。
     *
     * @param id 菜单唯一标识符
     * @param inventory 玩家物品栏对象
     * @return 新创建的自动售货箱菜单实例
     */
    @Override
    protected @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        return new AutoShippingBoxMenu(id, inventory, this);
    }
}
