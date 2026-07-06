package com.chinaex123.shipping_box;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAndRegressionTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    @Test
    void webEditorRejectsOversizedRequestBodyBeforeAllocating() throws Exception {
        String source = readSource("src/main/java/com/chinaex123/shipping_box/web/WebEditorLocalServer.java");

        assertTrue(source.contains("MAX_BODY_BYTES = 2 * 1024 * 1024"));
        assertTrue(source.contains("contentLength > MAX_BODY_BYTES"));
        assertTrue(source.contains("MAX_HEADER_BYTES"));
        assertTrue(source.contains("readLine(in, MAX_REQUEST_LINE_BYTES)"));
    }

    @Test
    void ruleValidationRejectsUnsafeCountsAndWeights() throws Exception {
        String source = readSource("src/main/java/com/chinaex123/shipping_box/event/ExchangeRecipeManager.java");

        assertTrue(source.contains("!isPositiveRuleNumber(input.getCount())"));
        assertTrue(source.contains("return isPositiveRuleNumber(output.getCount())"));
        assertTrue(source.contains("!isPositiveRuleNumber(values[i])"));
        assertTrue(source.contains("weightedItem.getWeight() <= 0"));
        assertTrue(source.contains("totalWeight > Integer.MAX_VALUE"));
    }

    @Test
    void editorPacketsRequireOperatorPermission() throws Exception {
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorReadFile.java", "hasPermissions(2)");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorSaveRules.java", "hasPermissions(2)");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorReloadRequest.java", "hasPermissions(2)");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorReadFile.java", "Permission denied");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorSaveRules.java", "Permission denied");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/network/PacketEditorReloadRequest.java", "Permission denied");
    }

    @Test
    void exchangeCancellationDoesNotApplyResultsOrCurrencyFirst() throws Exception {
        String source = readSource("src/main/java/com/chinaex123/shipping_box/event/ExchangeManager.java");
        int eventIndex = source.indexOf("ShippingBoxAPI.onExchange");
        int addMoneyIndex = source.indexOf("ViScriptShopUtil.addMoney");

        assertTrue(eventIndex >= 0);
        assertTrue(addMoneyIndex > eventIndex, "currency payout must happen after cancellation hook");
        assertFalse(source.contains("items.set(i, consumedItems"), "cancel path must not restore consumed items only");
        assertTrue(source.contains("initialSnapshot"));
    }

    @Test
    void autoBoxPersistsExchangePrototypeAndMenusUseInstanceValidity() throws Exception {
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/block/entity/AutoShippingBoxBlockEntity.java", "ExchangedItemPrototype");
        assertSourceContains("src/main/java/com/chinaex123/shipping_box/block/entity/AutoShippingBoxBlockEntity.java", "rebuildMissingExchangePrototypes");

        String shippingMenu = readSource("src/main/java/com/chinaex123/shipping_box/menu/ShippingBoxMenu.java");
        String autoMenu = readSource("src/main/java/com/chinaex123/shipping_box/menu/AutoShippingBoxMenu.java");
        assertFalse(shippingMenu.contains("static BlockPos storedPos"));
        assertFalse(autoMenu.contains("static BlockPos storedPos"));
        assertTrue(shippingMenu.contains("distanceToSqr"));
        assertTrue(autoMenu.contains("distanceToSqr"));
    }

    private static void assertSourceContains(String relativePath, String expected) throws Exception {
        assertTrue(readSource(relativePath).contains(expected), relativePath + " should contain " + expected);
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}