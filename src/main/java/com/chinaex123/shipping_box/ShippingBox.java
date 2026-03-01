package com.chinaex123.shipping_box;

import com.chinaex123.shipping_box.attribute.ModAttributes;
import com.chinaex123.shipping_box.block.ModBlocks;
import com.chinaex123.shipping_box.block.entity.ModBlockEntities;
import com.chinaex123.shipping_box.event.DynamicPricingManager;
import com.chinaex123.shipping_box.event.ExchangeManager;
import com.chinaex123.shipping_box.event.ExchangeRuleComponents;
import com.chinaex123.shipping_box.item.ModItems;
import com.chinaex123.shipping_box.network.ClientSoldCountCache;
import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import com.chinaex123.shipping_box.tooltip.TooltipEventHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// 这里的值应与 META-INF/neoforge.mods.toml 文件中的条目对应
@Mod(ShippingBox.MOD_ID)
public class ShippingBox {
    // 在公共位置定义模组ID，供所有地方引用
    public static final String MOD_ID = "shipping_box";

    // 模组类的构造函数是模组加载时运行的第一段代码
    // FML 会自动识别某些参数类型（如 IEventBus 或 ModContainer）并传入
    public ShippingBox(IEventBus modEventBus, ModContainer modContainer) {
        // 注册服务器事件
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        // 注册网络数据包处理器
        modEventBus.addListener(ShippingBoxNetworking::register);

        ModCreativeTabs.register(modEventBus); // 创造模式物品栏

        ModBlocks.register(modEventBus);      // 注册方块
        ModItems.register(modEventBus);        // 注册物品
        ModBlockEntities.register(modEventBus); // 注册方块实体

        ModAttributes.ATTRIBUTES.register(modEventBus); // 注册属性

        NeoForge.EVENT_BUS.register(TooltipEventHandler.class);
    }

    // 添加玩家登录事件处理方法
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 延迟一小段时间再同步，确保客户端完全加载
            serverPlayer.getServer().execute(() -> {
                ShippingBoxNetworking.syncRecipesToClient(serverPlayer);
            });
        }
    }

    // 添加玩家登出事件处理
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 清理客户端缓存
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSoldCountCache.clearCache();
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        DynamicPricingManager.saveData();
    }

    // 可以使用 @SubscribeEvent 并让事件总线自动发现要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 初始化附魔注册表
        RegistryAccess registryAccess = event.getServer().registryAccess();
        ExchangeRuleComponents.initEnchantmentRegistry(registryAccess);
    }
}
