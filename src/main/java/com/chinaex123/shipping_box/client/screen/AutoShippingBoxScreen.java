package com.chinaex123.shipping_box.client.screen;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.client.gui.ShippingBoxLayout;
import com.chinaex123.shipping_box.menu.AutoShippingBoxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * 自动售货箱的 GUI 屏幕类
 * <p>
 * 负责渲染自动售货箱的图形界面，包括：
 * - 背景贴图的绘制（支持中英文双语）
 * - 物品槽位的显示
 * - 鼠标悬停提示
 * <p>
 * 贴图规格：780×1016（4倍高清），渲染为 195×254
 */
public class AutoShippingBoxScreen extends AbstractContainerScreen<AutoShippingBoxMenu> {

    // ==================== 贴图资源常量 ====================

    /** 中文版贴图资源路径 */
    private static final ResourceLocation TEXTURE_ZH = ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "textures/gui/shipping_box_zh_cn.png");

    /** 英文版贴图资源路径 */
    private static final ResourceLocation TEXTURE_EN = ResourceLocation.fromNamespaceAndPath(ShippingBox.MOD_ID, "textures/gui/shipping_box_en_us.png");

    /**
     * 根据当前游戏语言选择对应的贴图
     *
     * @return 对应语言的贴图资源位置
     */
    private static ResourceLocation selectTexture() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        return "zh_cn".equals(lang) ? TEXTURE_ZH : TEXTURE_EN;
    }

    /**
     * 构造函数
     *
     * @param menu 自动售货箱菜单实例
     * @param playerInventory  玩家物品栏
     * @param title 屏幕标题
     */
    public AutoShippingBoxScreen(AutoShippingBoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = ShippingBoxLayout.IMAGE_WIDTH;   // 195
        this.imageHeight = ShippingBoxLayout.IMAGE_HEIGHT; // 254
    }

    /**
     * 渲染标签（标题和物品栏文字）
     * <p>
     * 覆盖父类方法，不渲染任何标签文字。
     * 因为贴图中已经包含了标题和文字，无需额外渲染。
     *
     * @param graphics 图形渲染上下文
     * @param mouseX   鼠标 X 坐标
     * @param mouseY   鼠标 Y 坐标
     */
    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {}

    /**
     * 渲染 GUI 背景
     * <p>
     * 将 780×1016 的高清贴图缩放渲染为 195×254 的 GUI 界面。
     * 使用 blit 方法进行纹理采样和缩放。
     *
     * @param graphics    图形渲染上下文
     * @param partialTick 部分刻（用于平滑动画）
     * @param mouseX      鼠标 X 坐标
     * @param mouseY      鼠标 Y 坐标
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 获取 GUI 左上角在屏幕上的位置
        int x = this.leftPos;
        int y = this.topPos;

        // 参数说明：
        // 1. 贴图资源（根据语言选择）
        // 2-3. 屏幕绘制位置 (x, y)
        // 4-5. 绘制宽度和高度 (195, 254) - GUI 显示尺寸
        // 6-7. UV 起始坐标 (0.0, 0.0) - 从贴图左上角开始采样
        // 8-9. UV 采样宽度和高度 (780, 1016) - 贴图原始尺寸
        // 10-11. 贴图文件实际尺寸 (780, 1016)
        graphics.blit(
                selectTexture(), // 贴图资源
                x, y, // 屏幕绘制位置
                ShippingBoxLayout.IMAGE_WIDTH, // 屏幕渲染宽度 195
                ShippingBoxLayout.IMAGE_HEIGHT, // 屏幕渲染高度 254
                0.0F, 0.0F, // UV 起始坐标
                ShippingBoxLayout.TEXTURE_WIDTH, // 纹理采样宽度 780
                ShippingBoxLayout.TEXTURE_HEIGHT, // 纹理采样高度 1016
                ShippingBoxLayout.TEXTURE_WIDTH, // PNG 真实宽度 780
                ShippingBoxLayout.TEXTURE_HEIGHT // PNG 真实高度 1016
        );
    }

    /**
     * 主渲染方法
     * <p>
     * 先调用父类方法渲染基础内容（背景、物品槽位等），
     * 再渲染鼠标悬停时的物品提示。
     *
     * @param graphics 图形渲染上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param partialTick 部分刻（用于平滑动画）
     */
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景、物品槽位等基础内容
        super.render(graphics, mouseX, mouseY, partialTick);

        // 渲染鼠标悬停提示（显示物品名称和属性）
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}