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
 * 普通售货箱 GUI 屏幕
 * <p>
 * 使用 Minecraft 26.2 的 extract 渲染 API 绘制普通售货箱的界面。
 * 根据游戏语言环境自动选择中文或英文贴图资源。
 * 继承自 AbstractContainerScreen，使用 ShippingBoxLayout 中的布局常量
 * 确定各个 UI 元素的位置。
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
