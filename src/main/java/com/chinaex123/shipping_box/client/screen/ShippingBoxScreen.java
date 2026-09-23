package com.chinaex123.shipping_box.client.screen;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.client.gui.ShippingBoxLayout;
import com.chinaex123.shipping_box.client.menu.ShippingBoxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * 普通售货箱 GUI 屏幕。
 * <p>
 * 根据游戏语言环境自动选择中文或英文贴图资源。
 * 继承自 AbstractContainerScreen，使用 ShippingBoxLayout 中的布局常量
 * 确定各个 UI 元素的位置。
 */
public class ShippingBoxScreen extends AbstractContainerScreen<ShippingBoxMenu> {

    /** 中文版贴图资源路径 */
    private static final Identifier TEXTURE_ZH = ShippingBox.id("textures/gui/shipping_box_zh_cn.png");

    /** 英文版贴图资源路径 */
    private static final Identifier TEXTURE_EN = ShippingBox.id("textures/gui/shipping_box_en_us.png");

    /**
     * 根据当前游戏语言选择对应的贴图。
     *
     * @return 对应语言的贴图资源位置
     */
    private static Identifier selectTexture() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        return "zh_cn".equals(lang) ? TEXTURE_ZH : TEXTURE_EN;
    }

    /**
     * 构造普通售货箱 GUI 屏幕。
     *
     * @param m   普通售货箱菜单实例
     * @param inv 玩家物品栏
     * @param t   屏幕标题
     */
    public ShippingBoxScreen(ShippingBoxMenu m, Inventory inv, Component t) {
        super(m, inv, t, ShippingBoxLayout.IMAGE_WIDTH, ShippingBoxLayout.IMAGE_HEIGHT);
    }

    /**
     * 提取并绘制 GUI 背景。
     * <p>
     * 将 780×1016 的高清贴图缩放渲染为 195×254 的界面，
     * 使用 blit 方法进行纹理采样与缩放。
     *
     * @param graphics    图形提取器
     * @param mouseX      鼠标 X 坐标
     * @param mouseY      鼠标 Y 坐标
     * @param partialTick 部分刻（用于平滑动画）
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // 参数依次为：渲染管线、贴图资源、屏幕位置、UV 起始坐标、渲染尺寸、纹理采样尺寸与 PNG 实际尺寸
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

    /**
     * 提取并绘制标签（标题和物品栏文字）。
     * <p>
     * 覆盖父类方法，不渲染任何标签文字。
     * 因为贴图中已经包含了标题和文字，无需额外渲染。
     *
     * @param graphics 图形提取器
     * @param mouseX   鼠标 X 坐标
     * @param mouseY   鼠标 Y 坐标
     */
    @Override
    public void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }
}