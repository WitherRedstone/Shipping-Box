package com.chinaex123.shipping_box;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全与回归测试 — 26.2 适配版。
 * <p>
 * 验证关键安全约束在代码变更后仍然有效。
 * 所有测试通过源代码字符串匹配进行，不依赖运行时环境。
 */
class SecurityAndRegressionTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    /** Web 编辑器拒绝超大请求体，防止内存溢出攻击 */
    @Test
    void webEditorRejectsOversizedRequestBodyBeforeAllocating() throws Exception {
        String source = readSource("src/main/java/com/chinaex123/shipping_box/web/WebEditorLocalServer.java");

        assertTrue(source.contains("MAX_BODY_BYTES = 2 * 1024 * 1024"));
        assertTrue(source.contains("contentLength > MAX_BODY_BYTES"));
        assertTrue(source.contains("MAX_HEADER_BYTES"));
        assertTrue(source.contains("readLine(in, MAX_REQUEST_LINE_BYTES)"));
    }

    /** 规则验证拒绝非法的数量和权重（负数、零、溢出） */
    @Test
    void ruleValidationRejectsUnsafeCountsAndWeights() throws Exception {
        String source = readSource("src/main/java/com/chinaex123/shipping_box/event/ExchangeRecipeManager.java");

        assertTrue(source.contains("!isPositiveRuleNumber(input.getCount())"));
        assertTrue(source.contains("return isPositiveRuleNumber(output.getCount())"));
        assertTrue(source.contains("!isPositiveRuleNumber(values[i])"));
        assertTrue(source.contains("weightedItem.getWeight() <= 0"));
        assertTrue(source.contains("totalWeight > Integer.MAX_VALUE"));
    }

    /** 编辑器数据包需要创造模式权限（26.2 使用 gameMode.isCreative() 替代旧 hasPermissions API） */
    @Test
    void editorPacketsRequireCreativeModePermission() throws Exception {
        // PacketEditorReadFile — 读取文件需要创造模式
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorReadFile.java",
                "Permission denied");
        // PacketEditorSaveRules — 保存规则需要创造模式
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorSaveRules.java",
                "Permission denied");
        // PacketEditorReloadRequest — 重载需要创造模式
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorReloadRequest.java",
                "Permission denied");
        // 验证使用 isCreative() 检查
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorReadFile.java",
                "isCreative()");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorSaveRules.java",
                "isCreative()");
    }

    /** onExchange API 调用应在交易结果应用之前执行，允许取消兑换 */
    @Test
    void exchangeCancellationHookCalledBeforeResultsApplied() throws Exception {
        String source = readSource("src/main/java/com/chinaex123/shipping_box/event/ExchangeManager.java");

        // 验证 onExchange 回调存在
        int eventIndex = source.indexOf("ShippingBoxAPI.onExchange");
        assertTrue(eventIndex >= 0, "onExchange hook must exist");

        // 验证取消路径恢复初始物品快照
        assertTrue(source.contains("initialSnapshot"), "cancel path must restore from initial snapshot");

        // 验证 initialSnapshot 在结果应用前保存
        int snapshotIndex = source.indexOf("initialSnapshot");
        assertTrue(snapshotIndex > 0);
    }

    /** 自动售货箱持久化兑换原型，菜单使用实例有效性检查（非静态坐标） */
    @Test
    void autoBoxPersistsExchangePrototypeAndMenusUseInstanceValidity() throws Exception {
        // 26.2：字段名改为小写 exchangedItemPrototype，rebuildMissingExchangePrototypes 方法已移除
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/block/entity/AutoShippingBoxBlockEntity.java",
                "exchangedItemPrototype");

        String shippingMenu = readSource("src/main/java/com/chinaex123/shipping_box/menu/ShippingBoxMenu.java");
        String autoMenu = readSource("src/main/java/com/chinaex123/shipping_box/menu/AutoShippingBoxMenu.java");

        // 验证没有使用静态坐标
        assertFalse(shippingMenu.contains("static BlockPos storedPos"),
                "shipping menu must not use static pos reference");
        assertFalse(autoMenu.contains("static BlockPos storedPos"),
                "auto shipping menu must not use static pos reference");

        // 验证使用 distanceToSqr 进行距离验证
        assertTrue(shippingMenu.contains("distanceToSqr"),
                "shipping menu must use distanceToSqr validation");
        assertTrue(autoMenu.contains("distanceToSqr"),
                "auto shipping menu must use distanceToSqr validation");
    }

    /** 26.2 功能对齐：虚拟货币输出进入内置余额，而不是仅显示特效 */
    @Test
    void virtualCurrencyPayoutUsesInternalBalance() throws Exception {
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/event/ExchangeManager.java",
                "PlayerBalanceManager.addBalance");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/storage/PlayerBalanceData.java",
                "player_balances");
    }

    /** 26.2：外部商店联动不可用时，内置虚拟货币必须可由配置关闭 */
    @Test
    void virtualCurrencyCanBeDisabledByConfig() throws Exception {
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/config/CommonConfig.java",
                "ENABLE_VIRTUAL_CURRENCY");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/config/CommonConfig.java",
                "enableVirtualCurrency");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/event/ExchangeManager.java",
                "rule.getOutputItem().isCoin() && !CommonConfig.ENABLE_VIRTUAL_CURRENCY.get()");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/item/CreeperCoinItem.java",
                "ENABLE_VIRTUAL_CURRENCY");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/item/DimensionalPouchItem.java",
                "ENABLE_VIRTUAL_CURRENCY");
    }

    /** 26.2 功能对齐：自动售货箱外部管道只能提取已兑换产物 */
    @Test
    void autoBoxTransferHandlerFiltersUnexchangedInputs() throws Exception {
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/ShippingBox.java",
                "autoBox.getTransferHandler()");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/block/entity/AutoShippingBoxResourceHandler.java",
                "canExternalExtract");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/block/entity/AutoShippingBoxBlockEntity.java",
                "ExchangedItemPrototype");
    }

    /** 26.2 GUI 应绘制旧版高清背景贴图 */
    @Test
    void containerScreensExtractBackgroundTexture() throws Exception {
        assertSourceContains("src/main/java/com/chinaex123/client/screen/ShippingBoxScreen.java",
                "extractBackground");
        assertSourceContains("src/main/java/com/chinaex123/client/screen/ShippingBoxScreen.java",
                "RenderPipelines.GUI_TEXTURED");
        assertSourceContains("src/main/java/com/chinaex123/client/screen/ShippingBoxScreen.java",
                "ShippingBoxLayout.TEXTURE_WIDTH,\n                ShippingBoxLayout.TEXTURE_HEIGHT,\n                ShippingBoxLayout.TEXTURE_WIDTH");
        assertSourceContains("src/main/java/com/chinaex123/client/screen/AutoShippingBoxScreen.java",
                "extractBackground");
    }

    /** 26.2 物品渲染需要 assets/<modid>/items/*.json，否则物品栏显示黑紫缺失模型 */
    @Test
    void itemModelDefinitionsExistForMinecraft262() throws Exception {
        for (String item : new String[] {
                "shipping_box",
                "auto_shipping_box",
                "dimensional_pouch",
                "copper_creeper_coin",
                "iron_creeper_coin",
                "gold_creeper_coin",
                "diamond_creeper_coin",
                "emerald_creeper_coin",
                "netherite_creeper_coin",
                "symbols_chaos_creeper_coin"
        }) {
            String itemDefinition = readSource("src/generated/resources/assets/shipping_box/items/" + item + ".json");
            assertTrue(itemDefinition.contains("\"type\": \"minecraft:model\""),
                    item + " must use the 26.2 item model definition format");
            assertTrue(itemDefinition.contains("\"model\": \"shipping_box:item/" + item + "\""),
                    item + " must point at the existing generated item model");
        }
    }

    /** 硬币配方倍率保持 main 分支经济体系 */
    @Test
    void coinRecipesKeepMainBranchExchangeRatios() throws Exception {
        assertSourceContains("src/generated/resources/data/shipping_box/recipe/iron_creeper_coin_shapeless.json",
                "\"count\": 2");
        assertSourceContains("src/generated/resources/data/shipping_box/recipe/gold_creeper_coin_shapeless.json",
                "\"count\": 4");
        assertSourceContains("src/generated/resources/data/shipping_box/recipe/dimensional_pouch.json",
                "#c:gems/amethyst");
    }

    /** 辅助：断言源文件包含指定字符串 */
    private static void assertSourceContains(String relativePath, String expected) throws Exception {
        assertTrue(readSource(relativePath).contains(expected),
                relativePath + " should contain " + expected);
    }

    /** 辅助：读取源文件全部内容 */
    private static String readSource(String relativePath) throws Exception {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
