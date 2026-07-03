package com.chinaex123.shipping_box.util;

import com.chinaex123.shipping_box.web.EditorIconCacheManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 物品图标 PNG 渲染器。
 *
 * <p>26.2 的 item 模型入口变成 assets/<namespace>/items/*.json。这里不再从 atlas
 * 坐标反裁剪单张材质，因为 atlas 坐标和源 PNG 坐标不是同一个坐标系。</p>
 */
public class ItemIconPngRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemIconPngRenderer.class);

    public static final int DEFAULT_SIZE = 32;

    private static final String[] PREFERRED_TEXTURE_KEYS = {
            "layer0", "all", "particle", "side", "top", "front", "end", "texture"
    };

    /** 把物品对应的模型主材质导出为 PNG。 */
    public static byte[] renderStackToPng(ItemStack stack, int size) {
        return renderStackToPng(stack, size, false);
    }

    /** 把物品或方块栈导出为 PNG。方块缓存使用等距立方体合成，避免退化成平面材质。 */
    public static byte[] renderStackToPng(ItemStack stack, int size, boolean renderAsBlock) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return null;
        }

        try {
            if (renderAsBlock) {
                byte[] blockIcon = renderBlockIcon(mc, itemId, size);
                if (blockIcon != null && blockIcon.length > 0) {
                    return blockIcon;
                }
            }
            Identifier textureId = resolveItemTexture(mc, itemId);
            if (textureId == null) {
                return EditorIconCacheManager.createPlaceholderPng(size, itemId.hashCode());
            }
            return textureToPng(mc, textureId, size, itemId.hashCode());
        } catch (Exception e) {
            LOGGER.warn("[IconRenderer] Failed to export icon for {}", itemId, e);
            return EditorIconCacheManager.createPlaceholderPng(size, itemId.hashCode());
        }
    }

    private static byte[] renderBlockIcon(Minecraft mc, Identifier itemId, int size) {
        Identifier modelId = resolveItemModelId(mc, itemId);
        if (modelId == null) {
            modelId = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "block/" + itemId.getPath());
        }
        if (!isCubeLikeModel(mc, modelId, 0)) {
            return null;
        }

        Map<String, String> textures = new HashMap<>();
        collectModelTextures(mc, modelId, textures, 0);
        if (textures.isEmpty()) {
            return null;
        }

        BufferedImage top = loadTextureImage(mc, pickTexture(textures, "top", "all", "end", "side", "particle"));
        BufferedImage side = loadTextureImage(mc, pickTexture(textures, "side", "all", "end", "front", "particle", "top"));
        BufferedImage end = loadTextureImage(mc, pickTexture(textures, "end", "side", "all", "front", "particle", "top"));
        if (top == null || side == null || end == null) {
            return null;
        }

        try {
            BufferedImage icon = drawIsometricCube(firstSquareFrame(top), firstSquareFrame(side), firstSquareFrame(end), Math.max(size, 1));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(icon, "png", output);
            return output.toByteArray();
        } catch (Exception e) {
            LOGGER.debug("[IconRenderer] Failed to compose block icon for {}: {}", itemId, e.getMessage());
            return null;
        }
    }

    private static boolean isCubeLikeModel(Minecraft mc, Identifier modelId, int depth) {
        if (modelId == null || depth > 12) {
            return false;
        }
        if (isCubeTemplate(modelId)) {
            return true;
        }

        Identifier modelResourceId = Identifier.fromNamespaceAndPath(
                modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
        Optional<Resource> resource = mc.getResourceManager().getResource(modelResourceId);
        if (resource.isEmpty()) {
            return false;
        }

        try (InputStream in = resource.get().open();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("parent") || !root.get("parent").isJsonPrimitive()) {
                return false;
            }
            return isCubeLikeModel(mc, Identifier.tryParse(root.get("parent").getAsString()), depth + 1);
        } catch (Exception e) {
            LOGGER.debug("[IconRenderer] Failed to inspect model {}: {}", modelResourceId, e.getMessage());
            return false;
        }
    }

    private static boolean isCubeTemplate(Identifier modelId) {
        if (!"minecraft".equals(modelId.getNamespace())) {
            return false;
        }
        String path = modelId.getPath();
        if (path.startsWith("block/template_")) {
            return path.contains("slab")
                    || path.contains("stairs")
                    || path.contains("wall")
                    || path.contains("fence")
                    || path.contains("pane")
                    || path.contains("rail");
        }
        return path.equals("block/cube")
                || path.equals("block/cube_all")
                || path.equals("block/cube_column")
                || path.equals("block/cube_column_horizontal")
                || path.equals("block/cube_bottom_top")
                || path.equals("block/orientable")
                || path.equals("block/orientable_vertical")
                || path.equals("block/slab")
                || path.equals("block/slab_top")
                || path.equals("block/stairs")
                || path.equals("block/inner_stairs")
                || path.equals("block/outer_stairs")
                || path.equals("block/wall_inventory")
                || path.equals("block/fence_inventory")
                || path.equals("block/fence_post")
                || path.equals("block/fence_side")
                || path.equals("block/pane_noside")
                || path.equals("block/pane_side")
                || path.equals("block/rail_flat")
                || path.equals("block/rail_raised_ne")
                || path.equals("block/rail_raised_sw");
    }

    private static Identifier resolveItemTexture(Minecraft mc, Identifier itemId) {
        Identifier modelId = resolveItemModelId(mc, itemId);
        if (modelId == null) {
            modelId = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "item/" + itemId.getPath());
        }

        Map<String, String> textures = new HashMap<>();
        collectModelTextures(mc, modelId, textures, 0);

        for (String key : PREFERRED_TEXTURE_KEYS) {
            String texture = resolveTextureReference(textures, textures.get(key), 0);
            Identifier textureId = textureResourceId(texture);
            if (textureId != null && mc.getResourceManager().getResource(textureId).isPresent()) {
                return textureId;
            }
        }

        for (String texture : textures.values()) {
            String resolved = resolveTextureReference(textures, texture, 0);
            Identifier textureId = textureResourceId(resolved);
            if (textureId != null && mc.getResourceManager().getResource(textureId).isPresent()) {
                return textureId;
            }
        }
        return null;
    }

    private static Identifier resolveItemModelId(Minecraft mc, Identifier itemId) {
        Identifier definitionId = Identifier.fromNamespaceAndPath(
                itemId.getNamespace(), "items/" + itemId.getPath() + ".json");
        Optional<Resource> resource = mc.getResourceManager().getResource(definitionId);
        if (resource.isEmpty()) {
            return null;
        }

        try (InputStream in = resource.get().open();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject model = root.has("model") && root.get("model").isJsonObject()
                    ? root.getAsJsonObject("model")
                    : null;
            if (model == null || !model.has("model")) {
                return null;
            }
            JsonElement modelValue = model.get("model");
            if (!modelValue.isJsonPrimitive()) {
                return null;
            }
            return Identifier.tryParse(modelValue.getAsString());
        } catch (Exception e) {
            LOGGER.debug("[IconRenderer] Failed to read item definition {}: {}", definitionId, e.getMessage());
            return null;
        }
    }

    private static void collectModelTextures(Minecraft mc, Identifier modelId, Map<String, String> textures, int depth) {
        if (modelId == null || depth > 12) {
            return;
        }

        Identifier modelResourceId = Identifier.fromNamespaceAndPath(
                modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
        Optional<Resource> resource = mc.getResourceManager().getResource(modelResourceId);
        if (resource.isEmpty()) {
            return;
        }

        try (InputStream in = resource.get().open();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("parent")) {
                collectModelTextures(mc, Identifier.tryParse(root.get("parent").getAsString()), textures, depth + 1);
            }

            if (root.has("textures") && root.get("textures").isJsonObject()) {
                JsonObject textureObject = root.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> entry : textureObject.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        textures.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[IconRenderer] Failed to read model {}: {}", modelResourceId, e.getMessage());
        }
    }

    private static String resolveTextureReference(Map<String, String> textures, String texture, int depth) {
        if (texture == null || depth > 12) {
            return texture;
        }
        if (!texture.startsWith("#")) {
            return texture;
        }
        return resolveTextureReference(textures, textures.get(texture.substring(1)), depth + 1);
    }

    private static Identifier textureResourceId(String texture) {
        if (texture == null || texture.isBlank() || texture.startsWith("#")) {
            return null;
        }

        Identifier id = Identifier.tryParse(texture);
        if (id == null) {
            return null;
        }
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
    }

    private static String pickTexture(Map<String, String> textures, String... keys) {
        for (String key : keys) {
            String texture = resolveTextureReference(textures, textures.get(key), 0);
            if (texture != null) {
                return texture;
            }
        }
        for (String texture : textures.values()) {
            String resolved = resolveTextureReference(textures, texture, 0);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static BufferedImage loadTextureImage(Minecraft mc, String texture) {
        Identifier textureId = textureResourceId(texture);
        if (textureId == null) {
            return null;
        }
        Optional<Resource> resource = mc.getResourceManager().getResource(textureId);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            return ImageIO.read(in);
        } catch (Exception e) {
            LOGGER.debug("[IconRenderer] Failed to load texture {}: {}", textureId, e.getMessage());
            return null;
        }
    }

    private static byte[] textureToPng(Minecraft mc, Identifier textureId, int targetSize, int fallbackSeed) {
        Optional<Resource> resource = mc.getResourceManager().getResource(textureId);
        if (resource.isEmpty()) {
            return EditorIconCacheManager.createPlaceholderPng(targetSize, fallbackSeed);
        }

        try (InputStream in = resource.get().open()) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                return EditorIconCacheManager.createPlaceholderPng(targetSize, fallbackSeed);
            }

            BufferedImage frame = firstSquareFrame(source);
            BufferedImage scaled = scaleNearest(frame, Math.max(targetSize, 1));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", output);
            return output.toByteArray();
        } catch (Exception e) {
            LOGGER.debug("[IconRenderer] Failed to read texture {}: {}", textureId, e.getMessage());
            return EditorIconCacheManager.createPlaceholderPng(targetSize, fallbackSeed);
        }
    }

    private static BufferedImage firstSquareFrame(BufferedImage source) {
        int frameSize = Math.min(source.getWidth(), source.getHeight());
        if (source.getWidth() == frameSize && source.getHeight() == frameSize) {
            return source;
        }
        return source.getSubimage(0, 0, frameSize, frameSize);
    }

    private static BufferedImage scaleNearest(BufferedImage source, int size) {
        if (source.getWidth() == size && source.getHeight() == size) {
            return source;
        }

        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source, 0, 0, size, size, null);
        graphics.dispose();
        return output;
    }

    private static BufferedImage drawIsometricCube(BufferedImage top, BufferedImage left, BufferedImage right, int size) {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        double scale = size / 32.0D;
        int centerX = scale(16, scale);
        int topY = scale(2, scale);
        int leftX = scale(3, scale);
        int rightX = scale(29, scale);
        int midY = scale(9, scale);
        int centerY = scale(16, scale);
        int lowerY = scale(30, scale);
        int sideBottomY = scale(23, scale);

        Polygon leftFace = new Polygon(
                new int[] {leftX, centerX, centerX, leftX},
                new int[] {midY, centerY, lowerY, sideBottomY},
                4);
        Polygon rightFace = new Polygon(
                new int[] {centerX, rightX, rightX, centerX},
                new int[] {centerY, midY, sideBottomY, lowerY},
                4);
        Polygon topFace = new Polygon(
                new int[] {centerX, rightX, centerX, leftX},
                new int[] {topY, midY, centerY, midY},
                4);

        drawTexturedFace(graphics, left, leftFace, leftX, midY, centerX, centerY, leftX, sideBottomY, 0.72F);
        drawTexturedFace(graphics, right, rightFace, centerX, centerY, rightX, midY, centerX, lowerY, 0.86F);
        drawTexturedFace(graphics, top, topFace, centerX, topY, rightX, midY, leftX, midY, 1.0F);

        graphics.dispose();
        return output;
    }

    private static int scale(int value, double scale) {
        return (int) Math.round(value * scale);
    }

    private static void drawTexturedFace(Graphics2D graphics, BufferedImage texture, Polygon clip,
                                         int originX, int originY, int xAxisX, int xAxisY,
                                         int yAxisX, int yAxisY, float shade) {
        var oldClip = graphics.getClip();
        graphics.setClip(clip);
        AffineTransform transform = new AffineTransform(
                (xAxisX - originX) / (double) texture.getWidth(),
                (xAxisY - originY) / (double) texture.getWidth(),
                (yAxisX - originX) / (double) texture.getHeight(),
                (yAxisY - originY) / (double) texture.getHeight(),
                originX,
                originY);
        graphics.drawImage(texture, transform, null);
        if (shade < 1.0F) {
            graphics.setColor(new java.awt.Color(0.0F, 0.0F, 0.0F, 1.0F - shade));
            graphics.fillPolygon(clip);
        }
        graphics.setClip(oldClip);
    }

    /** 旧 API — 26.2 后无实现。 */
    public static void disposeRenderTarget() {
        // no-op
    }
}
