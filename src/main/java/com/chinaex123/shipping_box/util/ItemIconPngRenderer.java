package com.chinaex123.shipping_box.util;

import com.chinaex123.shipping_box.web.EditorIconCacheManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 物品图标PNG渲染器
 *
 * <p>功能说明：</p>
 * <ul>
 *   <li>将 Minecraft 的 ItemStack 渲染为 PNG 图片字节数组</li>
 *   <li>使用离屏渲染（Off-screen Rendering）避免影响游戏主画面</li>
 *   <li>支持自定义输出尺寸，采用最近邻放大保持像素锐利</li>
 *   <li>渲染尺寸固定为16x16（原生物品GUI尺寸），然后放大到目标尺寸</li>
 * </ul>
 *
 * <p>技术实现：</p>
 * <ul>
 *   <li>创建独立的 RenderTarget 进行离屏渲染</li>
 *   <li>设置正交投影矩阵，在16x16的虚拟画布上绘制物品</li>
 *   <li>使用 GuiGraphics 的 renderFakeItem 方法渲染物品</li>
 *   <li>从 RenderTarget 读取像素数据并编码为PNG</li>
 *   <li>采用最近邻插值算法放大图像，保持像素风格</li>
 * </ul>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>为Web编辑器生成物品/方块图标缓存</li>
 *   <li>需要在游戏外展示游戏内物品图标</li>
 *   <li>批量生成图标时的渲染引擎</li>
 * </ul>
 *
 * @see EditorIconCacheManager 使用此渲染器生成图标缓存
 * @see GuiGraphics #renderFakeItem 底层渲染方法
 */
public class ItemIconPngRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemIconPngRenderer.class);

    public static final int DEFAULT_SIZE = 32; // 默认输出尺寸
    public static final int RENDER_SIZE = 16; // 内部渲染尺寸

    /**
     * 离屏渲染目标（RenderTarget）
     * 用于在独立缓冲区中渲染，不影响主画面
     * 使用静态变量实现复用，避免频繁创建销毁
     */
    private static RenderTarget renderTarget;

    /**
     * 当前渲染目标的尺寸
     * 用于判断是否需要重新创建 RenderTarget
     */
    private static int currentSize = 0;

    /**
     * 将给定的物品栈渲染为PNG图片
     *
     * <p>这是对外的主要接口，处理了异常情况并自动降级到占位图</p>
     *
     * @param stack 要渲染的物品栈（不能为空）
     * @param size 输出图片的尺寸（像素），建议使用 DEFAULT_SIZE
     * @return PNG图片的字节数组，如果渲染失败返回占位图
     */
    public static byte[] renderStackToPng(ItemStack stack, int size) {
        // 参数验证
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }

        try {
            // 执行实际渲染
            return renderInternal(stack, size, mc);
        } catch (Exception e) {
            // 渲染失败，使用占位图作为降级方案
            LOGGER.warn("[IconRenderer] Failed to render icon for {}", stack.getHoverName().getString(), e);
            return EditorIconCacheManager.createPlaceholderPng(size, stack.hashCode());
        }
    }

    /**
     * 释放当前缓存的 RenderTarget
     *
     * <p>在游戏关闭或资源重载时调用，释放GPU内存</p>
     * 调用时机：
     * <ul>
     *   <li>游戏关闭时</li>
     *   <li>切换资源包后</li>
     *   <li>渲染目标尺寸变化时</li>
     * </ul>
     */
    public static void disposeRenderTarget() {
        if (renderTarget != null) {
            renderTarget.destroyBuffers();
            renderTarget = null;
            currentSize = 0;
        }
    }

    /**
     * 内部渲染实现
     *
     * <p>渲染流程：</p>
     * <ol>
     *   <li>检查是否在渲染线程中执行</li>
     *   <li>创建/复用 16x16 的 RenderTarget</li>
     *   <li>清空为透明背景</li>
     *   <li>设置16x16的正交投影矩阵</li>
     *   <li>使用 GuiGraphics 渲染物品</li>
     *   <li>从 RenderTarget 读取像素数据</li>
     *   <li>放大到目标尺寸（如需要）</li>
     *   <li>编码为PNG格式</li>
     * </ol>
     *
     * @param stack 要渲染的物品栈
     * @param targetSize 目标输出尺寸
     * @param mc Minecraft客户端实例
     * @return PNG字节数组，失败返回null
     */
    private static byte[] renderInternal(ItemStack stack, int targetSize, Minecraft mc) {
        // 确保在渲染线程中执行
        if (!RenderSystem.isOnRenderThread()) {
            return EditorIconCacheManager.createPlaceholderPng(targetSize, stack.hashCode());
        }

        // 准备渲染目标
        ensureRenderTarget(RENDER_SIZE, mc);

        // 保存主渲染目标引用
        RenderTarget mainTarget = mc.getMainRenderTarget();
        NativeImage raw16 = null;
        NativeImage finalImage = null;

        // 备份和保存渲染状态
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            // ===== 绑定离屏渲染目标 =====
            renderTarget.bindWrite(true);

            // ===== 清屏为透明背景 =====
            RenderSystem.clearColor(0f, 0f, 0f, 0f);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

            // ===== 设置16x16正交投影 =====
            // 坐标范围：(0,0) 到 (16,16)，Y轴向下为正
            Matrix4f projectionMatrix = new Matrix4f().setOrtho(
                    0.0F, RENDER_SIZE, RENDER_SIZE, 0.0F,
                    -10000.0F, 10000.0F
            );
            RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);

            // ===== 重置模型视图矩阵 =====
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            // ===== 配置渲染状态 =====
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableBlend();          // 启用混合（支持透明度）
            RenderSystem.defaultBlendFunc();     // 默认混合函数
            RenderSystem.enableDepthTest();      // 启用深度测试
            RenderSystem.depthMask(true);        // 写入深度缓冲

            // ===== 渲染物品 =====
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            GuiGraphics guiGraphics = new GuiGraphics(mc, buffers);

            // 在(0,0)位置渲染物品，不进行缩放
            Lighting.setupFor3DItems(); // 设置3D物品光照
            guiGraphics.renderFakeItem(stack, 0, 0);
            guiGraphics.flush(); // 刷新所有待处理的渲染
            Lighting.setupLevel(); // 恢复普通光照

            // 读取16x16的像素数据
            renderTarget.bindRead();
            raw16 = new NativeImage(NativeImage.Format.RGBA, RENDER_SIZE, RENDER_SIZE, false);
            raw16.downloadTexture(0, false); // 从GPU下载纹理数据
            raw16.flipY(); // 翻转Y轴

            // ===== 放大到目标尺寸 =====
            if (targetSize == RENDER_SIZE) {
                // 尺寸一致，直接使用
                finalImage = raw16;
            } else {
                // 最近邻放大，保持像素锐利
                finalImage = upscaleNearest(raw16, targetSize);
            }

            // ===== 编码为PNG =====
            byte[] png = encodePng(finalImage);
            if (png == null || png.length == 0) {
                return EditorIconCacheManager.createPlaceholderPng(targetSize, stack.hashCode());
            }
            return png;
        } finally {
            // ===== 清理资源 =====
            if (finalImage != null && finalImage != raw16) {
                finalImage.close();
            }
            if (raw16 != null) {
                raw16.close();
            }
            // 恢复渲染状态
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.disableBlend();
            // 恢复主渲染目标
            mainTarget.bindWrite(true);
        }
    }

    /**
     * 最近邻插值放大图像
     *
     * @param src 源图像（16x16）
     * @param targetSize 目标尺寸
     * @return 放大后的图像
     */
    private static NativeImage upscaleNearest(NativeImage src, int targetSize) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        // 创建目标图像
        NativeImage dst = new NativeImage(NativeImage.Format.RGBA, targetSize, targetSize, false);

        // 最近邻放大
        for (int y = 0; y < targetSize; y++) {
            // 计算在源图像中对应的Y坐标（整数除法实现最近邻）
            int srcY = y * srcH / targetSize;
            for (int x = 0; x < targetSize; x++) {
                // 计算在源图像中对应的X坐标
                int srcX = x * srcW / targetSize;
                // 直接复制像素值
                dst.setPixelRGBA(x, y, src.getPixelRGBA(srcX, srcY));
            }
        }

        return dst;
    }

    /**
     * 确保渲染目标存在且尺寸正确
     *
     * <p>如果 RenderTarget 不存在或尺寸不匹配，则重新创建</p>
     *
     * @param size 需要的尺寸
     * @param mc Minecraft客户端实例
     */
    private static void ensureRenderTarget(int size, Minecraft mc) {
        // 如果已存在且尺寸匹配，直接复用
        if (renderTarget != null && currentSize == size) {
            return;
        }
        // 否则释放旧的并创建新的
        disposeRenderTarget();

        // 创建带深度附件的 RenderTarget
        renderTarget = new RenderTarget(true) {};
        renderTarget.createBuffers(size, size, Minecraft.ON_OSX);
        currentSize = size;
    }

    /**
     * 将 NativeImage 编码为 PNG 格式
     *
     * @param image 要编码的图像
     * @return PNG字节数组，失败返回null
     */
    private static byte[] encodePng(NativeImage image) {
        try {
            // 创建临时文件
            Path tempFile = Files.createTempFile("shipping_box_icon_", ".png");
            // 写入PNG
            image.writeToFile(tempFile);
            // 读取字节
            byte[] result = Files.readAllBytes(tempFile);
            // 删除临时文件
            Files.deleteIfExists(tempFile);
            if (result.length > 0) {
                return result;
            }
        } catch (Exception e) {
            LOGGER.error("[IconRenderer] Failed to encode PNG", e);
        }
        return null;
    }
}