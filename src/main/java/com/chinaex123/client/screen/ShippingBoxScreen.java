package com.chinaex123.client.screen;

import com.chinaex123.shipping_box.client.gui.ShippingBoxLayout;
import com.chinaex123.shipping_box.menu.ShippingBoxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * 普通售货箱 GUI — 26.2 extract 渲染 API。
 */
public class ShippingBoxScreen extends AbstractContainerScreen<ShippingBoxMenu> {

    private static final Identifier TEXTURE_ZH =
            Identifier.fromNamespaceAndPath("shipping_box", "textures/gui/shipping_box_zh_cn.png");
    private static final Identifier TEXTURE_EN =
            Identifier.fromNamespaceAndPath("shipping_box", "textures/gui/shipping_box_en_us.png");

    private static Identifier selectTexture() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        return "zh_cn".equals(lang) ? TEXTURE_ZH : TEXTURE_EN;
    }

    public ShippingBoxScreen(ShippingBoxMenu m, Inventory inv, Component t) {
        super(m, inv, t, ShippingBoxLayout.IMAGE_WIDTH, ShippingBoxLayout.IMAGE_HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, selectTexture(),
                this.leftPos, this.topPos,
                0.0F, 0.0F,
                ShippingBoxLayout.IMAGE_WIDTH,
                ShippingBoxLayout.IMAGE_HEIGHT,
                ShippingBoxLayout.TEXTURE_WIDTH,
                ShippingBoxLayout.TEXTURE_HEIGHT,
                ShippingBoxLayout.TEXTURE_WIDTH,
                ShippingBoxLayout.TEXTURE_HEIGHT);
    }

    @Override
    public void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }
}
