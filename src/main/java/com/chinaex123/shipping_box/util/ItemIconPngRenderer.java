package com.chinaex123.shipping_box.util;

import com.chinaex123.shipping_box.web.EditorIconCacheManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 物品图标PNG渲染器 — 26.2 重构版。
 *
 * 旧版使用基于 FBO + 3D 投影的耗时 GL 渲染流水线。
 * MC 26.2 已完全重写渲染系统 (Vulkan/VertexFormat/BindGroup),旧 API 全失效。
 *
 * 26.2 改用简化策略:
 *   - 直接从 item 对应 TextureAtlas 的 Sprite 里读像素(如果有)
 *   - 如果 sprite 不在可访问资源中,回退到占位 magenta 16x16 PNG
 *   - 优点: 不依赖渲染线程,不依赖旧 GL 接口,批量生成快
 */
public class ItemIconPngRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemIconPngRenderer.class);

    public static final int DEFAULT_SIZE = 32;

    /** 把物品渲染成 PNG。主接口。 */
    public static byte[] renderStackToPng(ItemStack stack, int size) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        try {
            TextureAtlasSprite sprite = resolveSprite(stack, mc);
            if (sprite == null) {
                return EditorIconCacheManager.createPlaceholderPng(size, stack.hashCode());
            }
            return extractSpritePng(mc, sprite, size);
        } catch (Exception e) {
            LOGGER.warn("[IconRenderer] Failed to render icon for {}", stack.getHoverName().getString(), e);
            return EditorIconCacheManager.createPlaceholderPng(size, stack.hashCode());
        }
    }

    /** 取物品对应的 TextureAtlasSprite。 */
    private static TextureAtlasSprite resolveSprite(ItemStack stack, Minecraft mc) {
        Item item = stack.getItem();
        // 尝试通过 vanilla atlas
        try {
            var atlas = mc.textureManager.getTextureAtlas(Identifier.withDefaultNamespace("textures/atlas/blocks.png"));
            TextureAtlasSprite sprite = atlas.getSprite(BuiltInRegistries.ITEM.getKey(item));
            if (sprite != null && sprite.contents() != null && sprite.contents().name() != null) {
                return sprite;
            }
        } catch (Exception ignored) {}
        // 回退: 用 missing sprite
        try {
            return mc.textureManager.getTextureAtlas(
                    Identifier.withDefaultNamespace("textures/atlas/blocks.png"))
                    .getSprite(Identifier.withDefaultNamespace("missingno"));
        } catch (Exception ignored) {}
        return null;
    }

    /** 从 sprite 读 PNG 像素。如果 sprite 内容在 resource 包里找不到，则回退到占位图。 */
    private static byte[] extractSpritePng(Minecraft mc, TextureAtlasSprite sprite, int targetSize) {
        SpriteContents contents = sprite.contents();
        Identifier name = contents.name();
        if (name == null) return EditorIconCacheManager.createPlaceholderPng(targetSize, 0);

        // 资源条目的路径是 "<namespace>:textures/<path>.png"
        Identifier textureId = Identifier.fromNamespaceAndPath(
                name.getNamespace(),
                "textures/" + name.getPath() + ".png");

        var resOpt = mc.getResourceManager().getResource(textureId);
        if (resOpt.isEmpty()) {
            return EditorIconCacheManager.createPlaceholderPng(targetSize, name.hashCode());
        }

        try (Resource res = resOpt.get();
             InputStream in = res.open()) {
            BufferedImage buffered = ImageIO.read(in);
            if (buffered == null) {
                return EditorIconCacheManager.createPlaceholderPng(targetSize, name.hashCode());
            }

            int sw = contents.width();
            int sh = contents.height();
            int sx = sprite.getX();
            int sy = sprite.getY();

            // 裁剪子矩形
            int cropW = Math.min(sw, buffered.getWidth() - sx);
            int cropH = Math.min(sh, buffered.getHeight() - sy);
            if (cropW <= 0 || cropH <= 0) {
                return EditorIconCacheManager.createPlaceholderPng(targetSize, name.hashCode());
            }
            BufferedImage cropped = buffered.getSubimage(sx, sy, cropW, cropH);

            // 放大
            int outSize = Math.max(targetSize, 1);
            BufferedImage finalImg = cropped;
            if (outSize != cropW || outSize != cropH) {
                finalImg = new BufferedImage(outSize, outSize, BufferedImage.TYPE_INT_ARGB);
                finalImg.getGraphics().drawImage(cropped.getScaledInstance(outSize, outSize, java.awt.Image.NEARESTRESTO), 0, 0, null);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(finalImg, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            LOGGER.warn("Failed to load sprite {}: {}", textureId, e.getMessage());
            return EditorIconCacheManager.createPlaceholderPng(targetSize, name.hashCode());
        }
    }

    /** 旧 API — 26.2 后无实现。 */
    public static void disposeRenderTarget() {
        // no-op
    }
}

