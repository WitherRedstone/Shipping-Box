package com.chinaex123.shipping_box.event;

import com.chinaex123.shipping_box.ShippingBox;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 模组数据包处理器。
 * <p>
 * 在数据包查找阶段向游戏注册本模组内置的数据包，
 * 通过事件订阅自动注册到游戏事件总线。
 */
@EventBusSubscriber(modid = "shipping_box")
public class ModPackHandler {

    /**
     * 添加数据包查找器。
     * <p>
     * 使用自定义的包来源并置于列表顶部，且不自动启用。
     *
     * @param event 数据包查找器添加事件
     */
    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        PackSource packSource = new DefaultPackSource();

        event.addPackFinders(
                ShippingBox.id("default_packet/shipping_box_vanilla"),
                PackType.SERVER_DATA,
                Component.literal("Shipping Box - Vanilla"),
                packSource,
                false,
                Pack.Position.TOP
        );
    }

    /**
     * 默认数据包来源。
     * <p>
     * 不修改数据包名称，且不自动启用，需由玩家或整合包手动开启。
     */
    public static class DefaultPackSource implements PackSource {

        /**
         * 装饰数据包名称。
         *
         * @param name 原始名称
         * @return 未经修改的名称
         */
        @Override
        public @NotNull Component decorate(Component name) {
            return name;
        }

        /**
         * 是否自动添加该数据包。
         *
         * @return 恒为 false，表示不自动启用
         */
        @Override
        public boolean shouldAddAutomatically() {
            return false;
        }
    }
}