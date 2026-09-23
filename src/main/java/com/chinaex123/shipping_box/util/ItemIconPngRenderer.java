package com.chinaex123.shipping_box.util;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.web.EditorIconCacheManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

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
 * <p>
 * 从资源包中解析物品模型与材质，将物品或方块的主材质导出为 PNG。
 * 方块缓存通过等距立方体合成，避免退化为平面材质；
 * 解析失败时回退为占位图。
 */
public class ItemIconPngRenderer {

    /** 默认输出图标尺寸 */
    public static final int DEFAULT_SIZE = 32;

    /** 解析模型材质时按优先级尝试的键名 */
    private static final String[] PREFERRED_TEXTURE_KEYS = {
            "layer0", "all", "particle", "side", "top", "front", "end", "texture"
    };

    /**
     * 把物品对应的模型主材质导出为 PNG。
     *
     * @param stack 待渲染的物品堆
     * @param size  目标图标尺寸
     * @return PNG 字节数组，物品为空时返回 null
     */
    public static byte[] renderStackToPng(ItemStack stack, int size) {
        return renderStackToPng(stack, size, false);
    }

    /**
     * 把物品或方块栈导出为 PNG。
     * <p>
     * 方块缓存使用等距立方体合成，避免退化成平面材质；
     * 非方块或方块合成失败时回退为物品材质导出。
     *
     * @param stack         待渲染的物品堆
     * @param size          目标图标尺寸
     * @param renderAsBlock 是否按方块等距立方体渲染
     * @return PNG 字节数组，物品为空时返回 null
     */
    public static byte[] renderStackToPng(ItemStack stack, int size, boolean renderAsBlock) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

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
            ShippingBox.LOGGER.warn("[ItemIconPngRenderer.renderStackToPng] 导出物品图标失败，物品: {}", itemId, e);
            return EditorIconCacheManager.createPlaceholderPng(size, itemId.hashCode());
        }
    }

    /**
     * 将方块渲染为等距立方体图标。
     * <p>
     * 先解析物品模型并判断其是否类似立方体，随后收集模型材质，
     * 分别取顶面、侧面与端面材质合成等距立方体。
     * 任一步骤失败时返回 null，由调用方回退处理。
     *
     * @param mc     Minecraft 客户端实例
     * @param itemId 物品标识符
     * @param size   目标图标尺寸
     * @return PNG 字节数组，无法合成时返回 null
     */
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
            ShippingBox.LOGGER.debug("[ItemIconPngRenderer.renderBlockIcon] 合成方块图标失败，物品: {}", itemId, e);
            return null;
        }
    }

    /**
     * 判断模型是否类似立方体。
     * <p>
     * 逐级向上追溯模型的 parent，直到命中立方体模板或超出深度限制。
     *
     * @param mc      Minecraft 客户端实例
     * @param modelId 模型标识符
     * @param depth   当前递归深度
     * @return 类似立方体返回 true
     */
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
            ShippingBox.LOGGER.debug("[ItemIconPngRenderer.isCubeLikeModel] 检查模型是否为立方体失败，模型: {}", modelResourceId, e);
            return false;
        }
    }

    /**
     * 判断模型标识符是否对应原版立方体或可视为立方体的模板。
     *
     * @param modelId 模型标识符
     * @return 是立方体模板返回 true
     */
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

    /**
     * 解析物品对应的主材质标识符。
     * <p>
     * 先解析物品模型，收集其材质映射并按优先键顺序查找，
     * 若均未命中则遍历全部材质，返回首个可用的材质资源。
     *
     * @param mc     Minecraft 客户端实例
     * @param itemId 物品标识符
     * @return 主材质标识符，无法解析时返回 null
     */
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

    /**
     * 从物品定义文件解析其模型标识符。
     *
     * @param mc     Minecraft 客户端实例
     * @param itemId 物品标识符
     * @return 模型标识符，无法解析时返回 null
     */
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
            ShippingBox.LOGGER.debug("[ItemIconPngRenderer.resolveItemModelId] 读取物品定义失败，定义文件: {}", definitionId, e);
            return null;
        }
    }

    /**
     * 递归收集模型及其父模型定义的材质映射。
     * <p>
     * 先递归父模型，再以当前模型的材质覆盖同名键。
     *
     * @param mc       Minecraft 客户端实例
     * @param modelId  模型标识符
     * @param textures 材质映射收集容器
     * @param depth    当前递归深度
     */
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
            ShippingBox.LOGGER.debug("[ItemIconPngRenderer.collectModelTextures] 读取模型纹理失败，模型: {}", modelResourceId, e);
        }
    }

    /**
     * 解析材质引用，支持以 "#" 开头的间接引用。
     *
     * @param textures 材质映射
     * @param texture  待解析的材质引用
     * @param depth    当前递归深度
     * @return 解析后的材质路径
     */
    private static String resolveTextureReference(Map<String, String> textures, String texture, int depth) {
        if (texture == null || depth > 12) {
            return texture;
        }
        if (!texture.startsWith("#")) {
            return texture;
        }
        return resolveTextureReference(textures, textures.get(texture.substring(1)), depth + 1);
    }

    /**
     * 将材质路径转换为资源管理器中的材质资源标识符。
     *
     * @param texture 材质路径
     * @return 材质资源标识符，无法转换时返回 null
     */
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

    /**
     * 按给定键顺序从材质映射中选取首个可解析的材质。
     * <p>
     * 若指定键均未命中，则遍历全部材质返回首个可解析项。
     *
     * @param textures 材质映射
     * @param keys     优先尝试的键名
     * @return 解析后的材质路径，未找到返回 null
     */
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

    /**
     * 从资源管理器加载材质图片。
     *
     * @param mc      Minecraft 客户端实例
     * @param texture 材质路径
     * @return 材质图片，加载失败返回 null
     */
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
            ShippingBox.LOGGER.debug("[ItemIconPngRenderer.loadTextureImage] 加载纹理图片失败，纹理: {}", textureId, e);
            return null;
        }
    }

    /**
     * 将材质资源缩放并编码为 PNG 字节数组。
     * <p>
     * 先截取首个正方形帧，再按最近邻算法缩放至目标尺寸；
     * 资源缺失或编码失败时回退为占位图。
     *
     * @param mc           Minecraft 客户端实例
     * @param textureId    材质资源标识符
     * @param targetSize   目标图标尺寸
     * @param fallbackSeed 占位图生成所需的种子
     * @return PNG 字节数组
     */
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
            ShippingBox.LOGGER.debug("[ItemIconPngRenderer.textureToPng] 将图片转换为PNG失败，纹理: {}", textureId, e);
            return EditorIconCacheManager.createPlaceholderPng(targetSize, fallbackSeed);
        }
    }

    /**
     * 截取图像左上角的首个正方形帧。
     * <p>
     * 若图像本身为正方形则原样返回。
     *
     * @param source 源图像
     * @return 正方形帧图像
     */
    private static BufferedImage firstSquareFrame(BufferedImage source) {
        int frameSize = Math.min(source.getWidth(), source.getHeight());
        if (source.getWidth() == frameSize && source.getHeight() == frameSize) {
            return source;
        }
        return source.getSubimage(0, 0, frameSize, frameSize);
    }

    /**
     * 使用最近邻算法将图像缩放至指定尺寸。
     * <p>
     * 若尺寸一致则原样返回，保持像素风格清晰。
     *
     * @param source 源图像
     * @param size   目标边长
     * @return 缩放后的图像
     */
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

    /**
     * 将顶面、左侧面与右侧面材质合成为等距立方体图标。
     * <p>
     * 根据基准尺寸换算各顶点位置，构造顶面、左侧面与右侧面多边形，
     * 并分别按不同的明暗系数绘制纹理。
     *
     * @param top   顶面材质
     * @param left  左侧面材质
     * @param right 右侧面材质
     * @param size  目标图标尺寸
     * @return 合成后的等距立方体图像
     */
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

    /**
     * 按缩放系数换算基准坐标。
     *
     * @param value 基准坐标
     * @param scale 缩放系数
     * @return 换算后的坐标
     */
    private static int scale(int value, double scale) {
        return (int) Math.round(value * scale);
    }

    /**
     * 在多边形裁剪区域内绘制带明暗的纹理面。
     * <p>
     * 通过仿射变换将纹理映射到多边形，并按明暗系数叠加半透明黑色蒙层。
     *
     * @param graphics  图形上下文
     * @param texture   纹理图像
     * @param clip      裁剪多边形
     * @param originX   原点 X 坐标
     * @param originY   原点 Y 坐标
     * @param xAxisX    X 轴方向终点 X 坐标
     * @param xAxisY    X 轴方向终点 Y 坐标
     * @param yAxisX    Y 轴方向终点 X 坐标
     * @param yAxisY    Y 轴方向终点 Y 坐标
     * @param shade     明暗系数（小于 1 时叠加暗色）
     */
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
}