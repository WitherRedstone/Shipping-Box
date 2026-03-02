package com.chinaex123.shipping_box;

import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.block.ModBlocks;
import com.chinaex123.shipping_box.block.entity.AutoShippingBoxBlockEntity;
import com.chinaex123.shipping_box.block.entity.ModBlockEntities;
import com.chinaex123.shipping_box.event.DynamicPricingManager;
import com.chinaex123.shipping_box.event.ExchangeRuleComponents;
import com.chinaex123.shipping_box.item.ModItems;
import com.chinaex123.shipping_box.network.ClientSoldCountCache;
import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import com.chinaex123.shipping_box.tooltip.TooltipEventHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(ShippingBox.MOD_ID)
public class ShippingBox {
    // 在公共位置定义模组ID，供所有地方引用
    public static final String MOD_ID = "shipping_box";

    public ShippingBox(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(this::onServerStopping); // 添加服务器停止事件监听器

        modEventBus.addListener(this::registerCapabilities); // 能力注册事件
        modEventBus.addListener(ShippingBoxNetworking::register); // 注册网络数据包处理器

        ModCreativeTabs.register(modEventBus); // 注册自定义创造模式物品栏
        ModBlocks.register(modEventBus); // 注册方块
        ModItems.register(modEventBus); // 注册物品
        ModBlockEntities.register(modEventBus); // 注册方块实体
        ModAttributes.ATTRIBUTES.register(modEventBus); // 注册自定义属性系统
        NeoForge.EVENT_BUS.register(TooltipEventHandler.class); // 注册工具提示事件处理器
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
     * 玩家登出事件监听器
     * <p>
     * 当玩家从服务器断开连接时调用此方法，用于执行客户端相关的清理操作。
     * 主要负责清除客户端的销售数量缓存，释放内存资源并确保数据一致性。
     *
     * @param event 玩家登出事件对象，包含登出玩家的相关信息
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 清理客户端缓存
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSoldCountCache.clearCache();
        }
    }

    /**
     * 服务器启动事件监听器
     * <p>
     * 当Minecraft服务器启动时调用此方法，用于初始化模组所需的各种注册表和系统组件。
     * 主要负责初始化附魔注册表，确保兑换规则组件能够正确访问游戏内的附魔数据。
     *
     * @param event 服务器启动事件对象，包含服务器实例和其他启动相关信息
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 初始化附魔注册表
        RegistryAccess registryAccess = event.getServer().registryAccess();
        ExchangeRuleComponents.initEnchantmentRegistry(registryAccess);
    }

    /**
     * 服务器停止事件监听器
     * <p>
     * 当Minecraft服务器即将停止时调用此方法，用于执行必要的清理和数据保存操作。
     * 主要负责保存动态定价管理器的销售数据，确保统计数据在服务器重启后能够正确恢复。
     *
     * @param event 服务器停止事件对象，包含服务器停止的相关信息
     */
    private void onServerStopping(ServerStoppingEvent event) {
        DynamicPricingManager.saveData();
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
}
