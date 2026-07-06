package com.chinaex123.shipping_box;

import com.chinaex123.shipping_box.client.screen.AutoShippingBoxScreen;
import com.chinaex123.shipping_box.client.screen.ShippingBoxScreen;
import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.init.ModBlocks;
import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.init.ModBlockEntities;
import com.chinaex123.shipping_box.config.CommonConfig;
import com.chinaex123.shipping_box.event.DynamicPricingManager;
import com.chinaex123.shipping_box.init.ModCreativeTabs;
import com.chinaex123.shipping_box.init.ModItems;
import com.chinaex123.shipping_box.init.ModMenuTypes;
import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import com.chinaex123.shipping_box.client.tooltip.TooltipEventHandler;
import com.chinaex123.shipping_box.web.WebEditorLocalServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import com.chinaex123.shipping_box.command.ModCommands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(ShippingBox.MOD_ID)
public class ShippingBox {
    // 在公共位置定义模组ID，供所有地方引用
    public static final String MOD_ID = "shipping_box";

    public ShippingBox(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(this::onServerStopping); // 添加服务器停止事件监听器
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn); // 注册玩家登录事件监听器
        NeoForge.EVENT_BUS.addListener(this::registerCommands); // 注册命令

        modEventBus.addListener(this::registerCapabilities); // 能力注册事件
        modEventBus.addListener(ShippingBoxNetworking::register); // 注册网络数据包处理器
        modEventBus.addListener(this::registerScreens); // 注册自定义 Screen
        // 注册配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);

        ModCreativeTabs.register(modEventBus); // 注册自定义创造模式物品栏
        ModBlocks.register(modEventBus); // 注册方块
        ModItems.register(modEventBus); // 注册物品
        ModBlockEntities.register(modEventBus); // 注册方块实体
        ModMenuTypes.register(modEventBus); // 注册自定义 MenuType
        ModAttributes.ATTRIBUTES.register(modEventBus); // 注册自定义属性系统
        NeoForge.EVENT_BUS.register(TooltipEventHandler.class); // 注册工具提示事件处理器
    }

    /**
     * 注册命令事件监听器
     *
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    /**
     * 玩家登录事件监听器
     * <p>
     * 当玩家成功连接到服务器时调用此方法，用于执行登录后的初始化操作。
     * 主要负责向新登录的服务器玩家同步兑换配方数据，确保客户端能够正确显示和使用兑换功能。
     *
     * @param event 玩家登录事件对象，包含登录玩家的实体信息
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 延迟一小段时间再同步，确保客户端完全加载
            serverPlayer.getServer().execute(() -> {
                ShippingBoxNetworking.syncRecipesToClient(serverPlayer);
            });
        }
    }

    /**
     * 服务器启动事件监听器
     * <p>
     * 当 Minecraft 服务器启动时调用此方法，用于初始化模组所需的各种注册表和系统组件。
     * 主要负责初始化动态定价管理器的销售数据，确保统计数据在服务器重启后能够正确恢复。
     *
     * @param event 服务器启动事件对象，包含服务器实例和其他启动相关信息
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 不再需要手动初始化附魔注册表
        // 现在会在需要时自动从 ServerLifecycleHooks 获取
    }

    /**
     * 服务器停止事件监听器
     * <p>
     * 当 Minecraft 服务器即将停止时调用此方法，用于执行必要的清理和数据保存操作。
     * 主要负责保存动态定价管理器的销售数据，确保统计数据在服务器重启后能够正确恢复。
     *
     * @param event 服务器停止事件对象，包含服务器停止的相关信息
     */
    private void onServerStopping(ServerStoppingEvent event) {
        DynamicPricingManager.saveData();
        WebEditorLocalServer.stop();
    }

    /**
     * 注册模组能力事件监听器
     * <p>
     * 当NeoForge注册能力系统时调用此方法，用于注册自动售货箱方块的物品处理能力。
     * 通过能力系统，其他模组可以与自动售货箱进行交互，访问其物品存储功能。
     *
     * @param event 能力注册事件对象，用于注册各种能力提供者
     */
    @SubscribeEvent
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 注册自动售货箱的能力
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof AutoShippingBoxBlockEntity autoBox) {
                        return autoBox.getCapabilityHandler();
                    }
                    return null;
                },
                ModBlocks.AUTO_SHIPPING_BOX.get()
        );
    }

    /**
     * 注册自定义 Screen 与 MenuType 的绑定（仅客户端触发）
     */
    @SubscribeEvent
    public void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SHIPPING_BOX.get(), ShippingBoxScreen::new);
        event.register(ModMenuTypes.AUTO_SHIPPING_BOX.get(), AutoShippingBoxScreen::new);
    }
}
