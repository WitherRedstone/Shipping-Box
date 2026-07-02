package com.chinaex123.shipping_box.item;

import com.chinaex123.shipping_box.client.tooltip.TooltipItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 次元钱包物品类 — 26.2 重构版。
 *
 * 1.21.1 版硬币兑换依赖 ViScriptShop 联动模组。
 * 26.2 版本作者未升级,因此硬币不再走外部货币体系。
 * 次元钱袋保留"物品"注册,但 use() 逻辑改为仅提示玩家:
 *  - 右击:显示"请直接将硬币放入 Shipping Box 方块"
 *  - 不再清空玩家背包硬币
 *  - 旧存档里的硬币物品仍保持 ID,不会消失
 *
 * 后续重写硬币功能时,应通过 DataComponent 或 SavedData 完全内置实现。
 */
public class DimensionalPouchItem extends TooltipItems {

    public DimensionalPouchItem(Properties properties) {
        super(properties, () -> List.of(
                Component.translatable("tooltip.item.shipping_box.dimensional_pouch.info_26_2")
        ));
    }

    /**
     * MC 26.2:Item.use 不再返回 InteractionResultHolder,
     * 改为返回 InteractionResult;手里的物品直接通过 setItemInHand 调整。
     */
    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            // 26.2:硬币转换已内置到 Shipping Box 方块
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.shipping_box.dimensional_pouch.use_block_hint"));
            serverPlayer.playSound(SoundEvents.NOTE_BLOCK_HARP.value());
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * 工具方法:获取物品对应的硬币价值。
     * 由其他系统(ExchangeRecipe / DataComponent 驱动)调用。
     */
    public static int getCoinValue(Item item) {
        if (item == null) return 0;
        String id = item.getDescriptionId();
        return switch (id) {
            case "item.shipping_box.copper_creeper_coin" -> 1;
            case "item.shipping_box.iron_creeper_coin" -> 8;
            case "item.shipping_box.gold_creeper_coin" -> 16;
            case "item.shipping_box.diamond_creeper_coin" -> 64;
            case "item.shipping_box.emerald_creeper_coin" -> 256;
            case "item.shipping_box.netherite_creeper_coin" -> 512;
            case "item.shipping_box.symbols_chaos_creeper_coin" -> 4096;
            default -> 0;
        };
    }

    /**
     * 扫描容器中所有硬币的总价值。
     * @return 总硬币价值(不修改容器)
     */
    public static int scanContainerCoins(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            int coin = getCoinValue(s.getItem());
            if (coin > 0) total += coin * s.getCount();
        }
        return total;
    }

    /**
     * 从容器中移除所有硬币(实体货币上交)。
     * @return 移除的总价值
     */
    public static int removeContainerCoins(Container container) {
        int total = 0;
        List<Integer> cleared = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            int coin = getCoinValue(s.getItem());
            if (coin > 0) {
                total += coin * s.getCount();
                cleared.add(i);
            }
        }
        for (int i : cleared) container.setItem(i, ItemStack.EMPTY);
        return total;
    }

    /**
     * 从背包中移除所有硬币。
     */
    public static int removeInventoryCoins(Inventory inventory) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            int coin = getCoinValue(s.getItem());
            if (coin > 0) {
                total += coin * s.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        return total;
    }
}
