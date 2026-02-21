package com.chinaex123.shipping_box;

import com.chinaex123.shipping_box.block.ModBlocks;
import com.chinaex123.shipping_box.block.entity.ModBlockEntities;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.item.ModItems;
import com.chinaex123.shipping_box.menu.ModMenuTypes;
import com.chinaex123.shipping_box.network.ShippingBoxNetworking;
import com.chinaex123.shipping_box.tooltip.TooltipEventHandler;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// 这里的值应与 META-INF/neoforge.mods.toml 文件中的条目对应
@Mod(ShippingBox.MOD_ID)
public class ShippingBox {
    // 在公共位置定义模组ID，供所有地方引用
    public static final String MOD_ID = "shipping_box";
    // 直接引用 slf4j 日志记录器
    public static final Logger LOGGER = LogUtils.getLogger();

    // 模组类的构造函数是模组加载时运行的第一段代码
    // FML 会自动识别某些参数类型（如 IEventBus 或 ModContainer）并传入
    public ShippingBox(IEventBus modEventBus, ModContainer modContainer) {
        // 为模组加载注册 commonSetup 方法
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);

        // 注册网络数据包处理器
        modEventBus.addListener(ShippingBoxNetworking::register);

        ModCreativeTabs.register(modEventBus); // 创造模式物品栏

        ModBlocks.register(modEventBus);      // 注册方块
        ModItems.register(modEventBus);        // 注册物品
        ModBlockEntities.register(modEventBus); // 注册方块实体

        ModMenuTypes.register(modEventBus);    // 注册菜单类型

        NeoForge.EVENT_BUS.register(TooltipEventHandler.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 通用设置逻辑
    }

    // 可以使用 @SubscribeEvent 并让事件总线自动发现要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 服务器启动时执行某些操作
        LOGGER.info("HELLO from server starting");
    }
}
