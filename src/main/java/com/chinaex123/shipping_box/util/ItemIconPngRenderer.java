package com.chinaex123.shipping_box.util;

import com.chinaex123.shipping_box.ShippingBox;
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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 物品图标 PNG 渲染器。
 * <p>
 * 将物品堆渲染为 PNG 图像数据，供 Web 编辑器图标缓存使用。
 * 渲染在独立的离屏 RenderTarget 上完成，默认以 16×16 渲染后
 * 按需最近邻放大到目标尺寸，并在渲染失败时回退为占位图。
 */
public class ItemIconPngRenderer {

    /** 默认输出图标尺寸 */
    public static final int DEFAULT_SIZE = 32;
    /** 离屏渲染尺寸 */
    public static final int RENDER_SIZE = 16;

    /** 离屏渲染目标，按需创建并复用 */
    private static RenderTarget renderTarget;

    /** 当前渲染目标尺寸，用于判断是否需要重建 */
    private static int currentSize = 0;

    /**
     * 将物品堆渲染为 PNG 字节数组。
     * <p>
     * 物品为空时返回 null；渲染异常时回退为占位图。
     *
     * @param stack 待渲染的物品堆
     * @param size  目标图标尺寸
     * @return PNG 字节数组，物品为空时返回 null
     */
    public static byte[] renderStackToPng(ItemStack stack, int size) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();

        try {
            return renderInternal(stack, size, mc);
        } catch (Exception e) {
            ShippingBox.LOGGER.warn("[ItemIconPngRenderer.renderStackToPng] 渲染物品图标失败，物品: {}", stack.getHoverName().getString(), e);
            return EditorIconCacheManager.createPlaceholderPng(size, stack.hashCode());
        }
    }

    /**
     * 释放离屏渲染目标占用的资源。
     * <p>
     * 通常在不再需要渲染图标时调用，以释放显存。
     */
    public static void disposeRenderTarget() {
        if (renderTarget != null) {
            renderTarget.destroyBuffers();
            renderTarget = null;
            currentSize = 0;
        }
    }

    /**
     * 内部渲染实现。
     * <p>
     * 仅允许在渲染线程执行；否则直接回退为占位图。
     * 渲染流程包括：备份矩阵与渲染状态、绑定离屏目标并清屏、
     * 设置正交投影、绘制伪物品图标、下载纹理并按需放大、
     * 编码为 PNG，最后恢复原有渲染状态并绑定回主渲染目标。
     *
     * @param stack      待渲染的物品堆
     * @param targetSize 目标图标尺寸
     * @param mc         Minecraft 客户端实例
     * @return PNG 字节数组，无法渲染时返回占位图
     */
    private static byte[] renderInternal(ItemStack stack, int targetSize, Minecraft mc) {
        if (!RenderSystem.isOnRenderThread()) {
            return EditorIconCacheManager.createPlaceholderPng(targetSize, stack.hashCode());
        }

        ensureRenderTarget(RENDER_SIZE, mc);

        RenderTarget mainTarget = mc.getMainRenderTarget();
        NativeImage raw16 = null;
        NativeImage finalImage = null;

        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            renderTarget.bindWrite(true);

            RenderSystem.clearColor(0f, 0f, 0f, 0f);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

            Matrix4f projectionMatrix = new Matrix4f().setOrtho(
                    0.0F, RENDER_SIZE, RENDER_SIZE, 0.0F,
                    -10000.0F, 10000.0F
            );
            RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);

            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            GuiGraphics guiGraphics = new GuiGraphics(mc, buffers);

            Lighting.setupFor3DItems();
            guiGraphics.renderFakeItem(stack, 0, 0);
            guiGraphics.flush();
            Lighting.setupLevel();

            renderTarget.bindRead();
            raw16 = new NativeImage(NativeImage.Format.RGBA, RENDER_SIZE, RENDER_SIZE, false);
            raw16.downloadTexture(0, false);
            raw16.flipY();

            if (targetSize == RENDER_SIZE) {
                finalImage = raw16;
            } else {
                finalImage = upscaleNearest(raw16, targetSize);
            }

            byte[] png = encodePng(finalImage);
            if (png == null || png.length == 0) {
                return EditorIconCacheManager.createPlaceholderPng(targetSize, stack.hashCode());
            }
            return png;
        } finally {
            if (finalImage != null && finalImage != raw16) {
                finalImage.close();
            }
            if (raw16 != null) {
                raw16.close();
            }
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.disableBlend();
            mainTarget.bindWrite(true);
        }
    }

    /**
     * 使用最近邻算法放大图像。
     * <p>
     * 保持像素风格清晰，不进行插值。
     *
     * @param src        源图像
     * @param targetSize 目标边长
     * @return 放大后的新图像
     */
    private static NativeImage upscaleNearest(NativeImage src, int targetSize) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        NativeImage dst = new NativeImage(NativeImage.Format.RGBA, targetSize, targetSize, false);

        for (int y = 0; y < targetSize; y++) {
            int srcY = y * srcH / targetSize;
            for (int x = 0; x < targetSize; x++) {
                int srcX = x * srcW / targetSize;
                dst.setPixelRGBA(x, y, src.getPixelRGBA(srcX, srcY));
            }
        }

        return dst;
    }

    /**
     * 确保离屏渲染目标已按指定尺寸创建。
     * <p>
     * 若现有目标尺寸一致则直接复用，否则销毁并重建。
     *
     * @param size 目标尺寸
     * @param mc   Minecraft 客户端实例
     */
    private static void ensureRenderTarget(int size, Minecraft mc) {
        if (renderTarget != null && currentSize == size) {
            return;
        }
        disposeRenderTarget();

        renderTarget = new RenderTarget(true) {};
        renderTarget.createBuffers(size, size, Minecraft.ON_OSX);
        currentSize = size;
    }

    /**
     * 将图像编码为 PNG 字节数组。
     * <p>
     * 通过临时文件写出并读取字节，最后删除临时文件。
     *
     * @param image 待编码图像
     * @return PNG 字节数组，编码失败返回 null
     */
    private static byte[] encodePng(NativeImage image) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("shipping_box_icon_", ".png");
            image.writeToFile(tempFile);
            byte[] result = Files.readAllBytes(tempFile);
            if (result.length > 0) {
                return result;
            }
        } catch (Exception e) {
            ShippingBox.LOGGER.error("[ItemIconPngRenderer.encodePng] 编码 PNG 失败", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    ShippingBox.LOGGER.warn("[ItemIconPngRenderer.encodePng] 删除临时文件失败，路径: {}", tempFile, e);
                }
            }
        }
        return null;
    }
}