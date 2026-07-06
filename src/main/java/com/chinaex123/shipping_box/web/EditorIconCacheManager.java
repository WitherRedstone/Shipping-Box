package com.chinaex123.shipping_box.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.chinaex123.shipping_box.ShippingBox;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import com.chinaex123.shipping_box.util.ItemIconPngRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端图标缓存管理器
 *
 * <p>功能说明：</p>
 * <ul>
 *   <li>将游戏中的所有物品和方块图标渲染为PNG图片并保存到磁盘</li>
 *   <li>按游戏刻(Tick)限制处理数量，避免在缓存生成时造成客户端卡顿</li>
 *   <li>生成的缓存图片可供Web编辑器使用，提升Web界面加载速度</li>
 *   <li>支持断点续传，已生成的图标不会重复生成</li>
 * </ul>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>Web编辑器需要显示物品/方块图标时，从此缓存读取</li>
 *   <li>首次启动或更新游戏版本后，需要重新生成缓存</li>
 *   <li>玩家可以通过命令手动触发缓存生成</li>
 * </ul>
 *
 * @see WebEditorLocalServer Web编辑器服务器，会使用此缓存
 * @see ItemIconPngRenderer 图标渲染工具类
 */
public class EditorIconCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditorIconCacheManager.class);

    // JSON序列化工具，用于生成格式化的清单文件
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int ICON_SIZE = 32; // 生成的图标尺寸
    public static final int ICONS_PER_TICK = 3; // 每个游戏刻最大处理的图标数量

    // 单例实例
    private static final EditorIconCacheManager INSTANCE = new EditorIconCacheManager();

    // 缓存文件存储路径
    private final Path cacheRoot; // 缓存根目录: config/shipping_box/editor_icon_cache/
    private final Path itemsDir; // 物品图标目录
    private final Path blocksDir; // 方块图标目录
    private final Path manifestFile; // 清单文件路径

    // 待处理的条目列表
    private final List<CacheEntry> pendingEntries = new ArrayList<>();

    // 已处理数量和总数量
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);

    // 当前状态
    private volatile Status status = Status.IDLE;
    private volatile String errorMessage = null;

    // 运行状态标志
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean forceMode = false;

    // Boss条ID，用于显示进度
    private UUID bossBarId;

    /**
     * 私有构造函数，初始化缓存目录并加载已有状态
     * 使用单例模式确保全局只有一个缓存管理器实例
     */
    private EditorIconCacheManager() {
        // 缓存根目录: .minecraft/config/shipping_box/editor_icon_cache/
        this.cacheRoot = FMLPaths.GAMEDIR.get()
                .resolve("config")
                .resolve(ShippingBox.MOD_ID)
                .resolve("editor_icon_cache");
        this.itemsDir = cacheRoot.resolve("items");   // 物品图标子目录
        this.blocksDir = cacheRoot.resolve("blocks"); // 方块图标子目录
        this.manifestFile = cacheRoot.resolve("manifest.json"); // 清单文件

        // 启动时尝试从已有的清单文件恢复状态
        loadStatusFromManifest();
    }

    /**
     * 更新Boss条进度显示
     * 在游戏界面上方显示一个Boss条，实时反映缓存生成进度
     */
    private void updateBossBar() {
        if (bossBarId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;

        // 计算进度百分比
        float progress = total.get() > 0 ? (float) processed.get() / total.get() : 0f;
        // 构建标题文本（使用可翻译文本）
        Component title = Component.translatable(
                "command.shipping_box.icon_cache.bossbar.title",
                processed.get(), total.get());

        // 创建Boss条事件
        LerpingBossEvent event = new LerpingBossEvent(
                bossBarId,
                title,
                progress,
                BossEvent.BossBarColor.BLUE, // 蓝色进度条
                BossEvent.BossBarOverlay.PROGRESS, // 进度条样式
                false, false, false
        );

        // 通过反射将Boss条添加到游戏界面
        try {
            Field eventsField = BossHealthOverlay.class.getDeclaredField("events");
            eventsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, LerpingBossEvent> events = (Map<UUID, LerpingBossEvent>) eventsField.get(mc.gui.getBossOverlay());
            events.put(bossBarId, event);
        } catch (Exception e) {
            // 反射失败则忽略，不影响功能
        }
    }

    /**
     * 移除Boss条
     * 缓存任务完成或停止时，从游戏界面移除进度显示
     */
    private void removeBossBar() {
        if (bossBarId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;

        try {
            Field eventsField = BossHealthOverlay.class.getDeclaredField("events");
            eventsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, LerpingBossEvent> events = (Map<UUID, LerpingBossEvent>) eventsField.get(mc.gui.getBossOverlay());
            events.remove(bossBarId);
        } catch (Exception e) {
            // 忽略异常
        }
        bossBarId = null;
    }

    /**
     * 从清单文件加载持久化状态
     * 如果上次缓存已完成，则直接标记为完成状态，避免重复生成
     */
    private void loadStatusFromManifest() {
        try {
            if (Files.exists(manifestFile)) {
                String json = Files.readString(manifestFile, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                String statusStr = obj.has("status") ? obj.get("status").getAsString() : "";

                // 如果清单状态为 ready 或 done，说明缓存已存在
                if ("ready".equals(statusStr) || "done".equals(statusStr)) {
                    this.status = Status.DONE;
                    int itemCount = obj.has("items") ? obj.getAsJsonArray("items").size() : 0;
                    int blockCount = obj.has("blocks") ? obj.getAsJsonArray("blocks").size() : 0;
                    this.total.set(itemCount + blockCount);
                    this.processed.set(this.total.get());
                    LOGGER.info("[IconCache] 从 manifest 加载就绪状态 ({} items, {} blocks)", itemCount, blockCount);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[IconCache] 加载 manifest 状态失败: {}", e.getMessage());
        }
    }

    /**
     * 获取单例实例
     * @return 缓存管理器唯一实例
     */
    public static EditorIconCacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * 缓存任务状态枚举
     */
    public enum Status {
        IDLE, // 空闲状态
        RUNNING, // 正在运行
        DONE, // 已完成
        ERROR // 出错状态
    }

    /**
     * 缓存条目记录
     * @param id 物品/方块的资源位置
     * @param isBlock 是否为方块
     * @param displayName 显示名称
     * @param fileName 安全的文件名
     */
    private record CacheEntry(ResourceLocation id, boolean isBlock, String displayName, String fileName) {}

    /**
     * 启动缓存生成任务
     * @param force 是否强制重新生成
     */
    public void startCache(boolean force) {
        // 如果已经在运行，根据force参数决定是否重启
        if (running.get()) {
            if (force) {
                // 强制模式：停止当前任务并清空缓存
                stopCurrentTask();
                clearCacheInternal(false);
            } else {
                // 非强制模式：直接返回
                return;
            }
        }

        // 确保缓存目录存在
        ensureDirectories();

        // 如果是强制模式，清空已有缓存
        if (force) {
            forceMode = true;
            clearCacheInternal(false);
        } else {
            forceMode = false;
        }

        // 准备待处理的条目列表
        prepareEntries();

        // 如果没有需要处理的条目，直接完成
        if (pendingEntries.isEmpty()) {
            status = Status.DONE;
            total.set(0);
            processed.set(0);
            writeManifest();
            return;
        }

        // 初始化任务状态
        total.set(pendingEntries.size());
        processed.set(0);
        status = Status.RUNNING;
        errorMessage = null;
        running.set(true);

        // 显示Boss条进度
        bossBarId = UUID.randomUUID();
        updateBossBar();

        LOGGER.info("[IconCache] 开始缓存任务，共 {} 个条目", total.get());
    }

    /**
     * 停止当前缓存任务
     */
    public void stopCurrentTask() {
        if (running.compareAndSet(true, false)) {
            status = Status.IDLE;
            removeBossBar();
            LOGGER.info("[IconCache] 缓存任务已停止");
        }
    }

    /**
     * 清空所有缓存
     */
    public void clearCache() {
        stopCurrentTask();
        clearCacheInternal(true);
        LOGGER.info("[IconCache] 缓存已清除");
    }

    /**
     * 确保缓存目录存在
     * 如果目录不存在则创建
     */
    private void ensureDirectories() {
        try {
            Files.createDirectories(itemsDir);
            Files.createDirectories(blocksDir);
        } catch (Exception e) {
            LOGGER.error("[IconCache] 创建缓存目录失败", e);
        }
    }

    /**
     * 清空缓存内部实现
     * @param resetStatus 是否重置状态为IDLE
     */
    private void clearCacheInternal(boolean resetStatus) {
        // 移除Boss条
        removeBossBar();

        // 删除所有缓存文件
        try {
            if (Files.exists(cacheRoot)) {
                Files.walk(cacheRoot)
                        .sorted((a, b) -> -a.compareTo(b)) // 先删除子文件再删除目录
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                        });
            }
            // 重新创建空目录
            Files.createDirectories(itemsDir);
            Files.createDirectories(blocksDir);
        } catch (Exception e) {
            LOGGER.error("[IconCache] 清空缓存失败", e);
        }

        // 重置计数
        pendingEntries.clear();
        processed.set(0);
        total.set(0);

        // 重置状态
        if (resetStatus) {
            status = Status.IDLE;
            errorMessage = null;
        }
    }

    /**
     * 准备待处理的条目列表
     * 遍历所有物品和方块，生成缓存条目
     */
    private void prepareEntries() {
        pendingEntries.clear();

        // 1. 处理所有物品
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item == Items.AIR) return; // 跳过空气
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) return;
            String fileName = toSafeFileName(id);
            String display = new ItemStack(item).getHoverName().getString();
            pendingEntries.add(new CacheEntry(id, false, display, fileName));
        });

        // 2. 处理所有方块（使用对应的物品图标）
        BuiltInRegistries.BLOCK.forEach(block -> {
            Item item = block.asItem();
            if (item == Items.AIR) return; // 跳过没有物品形式的方块
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) return;
            String fileName = toSafeFileName(id);
            String display = new ItemStack(item).getHoverName().getString();
            pendingEntries.add(new CacheEntry(id, true, display, fileName));
        });
    }

    /**
     * 将ResourceLocation转换为安全的文件名
     * @param id 资源位置
     * @return 安全的文件名（不含扩展名）
     */
    private String toSafeFileName(ResourceLocation id) {
        // 替换可能引起路径问题的字符
        return (id.getNamespace() + "_" + id.getPath()).replace(':', '_').replace('/', '_');
    }

    /**
     * 客户端tick回调，每游戏刻执行一次
     * 处理有限数量的图标生成，避免卡顿
     */
    public void tick() {
        if (!running.get() || status != Status.RUNNING) return;

        int processedThisTick = 0;
        // 每刻最多处理 ICONS_PER_TICK 个条目
        while (processedThisTick < ICONS_PER_TICK && !pendingEntries.isEmpty()) {
            CacheEntry entry = pendingEntries.remove(0);

            try {
                // 获取物品栈
                ItemStack stack = entry.isBlock()
                        ? new ItemStack(BuiltInRegistries.BLOCK.get(entry.id()).asItem())
                        : new ItemStack(BuiltInRegistries.ITEM.get(entry.id()));

                // 渲染图标为PNG
                byte[] png = ItemIconPngRenderer.renderStackToPng(stack, ICON_SIZE);
                if (png == null || png.length == 0) {
                    // 渲染失败，使用占位图
                    png = createPlaceholderPng(ICON_SIZE, entry.id().hashCode());
                    LOGGER.warn("[IconCache] 使用占位符图标 for {}", entry.id());
                }

                // 保存到文件
                Path targetDir = entry.isBlock() ? blocksDir : itemsDir;
                Path targetFile = targetDir.resolve(entry.fileName() + ".png");
                Files.write(targetFile, png);

            } catch (Exception e) {
                LOGGER.warn("[IconCache] 处理 {} 失败: {}", entry.id(), e.getMessage());
            } finally {
                processed.incrementAndGet();
                processedThisTick++;
            }
        }

        // 检查是否所有条目都已处理完成
        if (pendingEntries.isEmpty()) {
            running.set(false);
            status = Status.DONE;
            writeManifest();          // 生成清单文件
            removeBossBar();          // 移除Boss条
            LOGGER.info("[IconCache] 缓存任务完成，共处理 {} 个", processed.get());
        } else if (running.get()) {
            updateBossBar();          // 更新进度显示
        }
    }

    /**
     * 生成清单文件（manifest.json）
     * 记录所有已缓存的图标信息，供Web编辑器使用
     *
     * <p>清单包含以下信息：</p>
     * <ul>
     *   <li>版本信息和生成时间</li>
     *   <li>所有已缓存的物品和方块列表</li>
     *   <li>所有标签及其对应的代表性图标</li>
     * </ul>
     */
    private void writeManifest() {
        try {
            Files.createDirectories(cacheRoot);

            // 构建根JSON对象
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("modid", ShippingBox.MOD_ID);
            root.addProperty("iconSize", ICON_SIZE);
            root.addProperty("iconsPerTick", ICONS_PER_TICK);
            root.addProperty("status", "ready");
            root.addProperty("generatedAt", System.currentTimeMillis());

            // 1. 收集所有已缓存的物品
            JsonArray itemsArr = new JsonArray();
            BuiltInRegistries.ITEM.forEach(item -> {
                if (item == Items.AIR) return;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) return;
                String fileName = toSafeFileName(id) + ".png";
                Path file = itemsDir.resolve(fileName);
                if (Files.exists(file)) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", id.toString());
                    entry.addProperty("displayName", new ItemStack(item).getHoverName().getString());
                    entry.addProperty("path", "/icon/cache/items/" + fileName);
                    itemsArr.add(entry);
                }
            });

            // 2. 收集所有已缓存的方块
            JsonArray blocksArr = new JsonArray();
            BuiltInRegistries.BLOCK.forEach(block -> {
                Item item = block.asItem();
                if (item == Items.AIR) return;
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (id == null) return;
                String fileName = toSafeFileName(id) + ".png";
                Path file = blocksDir.resolve(fileName);
                if (Files.exists(file)) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", id.toString());
                    entry.addProperty("displayName", new ItemStack(item).getHoverName().getString());
                    entry.addProperty("path", "/icon/cache/blocks/" + fileName);
                    blocksArr.add(entry);
                }
            });

            root.add("items", itemsArr);
            root.add("blocks", blocksArr);

            // 3. 收集标签及其代表性图标
            JsonArray tagsArr = new JsonArray();
            JsonObject tagIcons = new JsonObject();
            BuiltInRegistries.ITEM.getTags().forEach(pair -> {
                var tagKey = pair.getFirst();
                ResourceLocation loc = tagKey.location();
                if (loc == null) return;
                String tagId = "#" + loc.getNamespace() + ":" + loc.getPath();
                tagsArr.add(tagId);

                // 查找标签中第一个有缓存的物品作为代表性图标
                var holderSet = BuiltInRegistries.ITEM.getTag(tagKey);
                if (holderSet.isPresent()) {
                    for (var holder : holderSet.get()) {
                        Item item = holder.value();
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                        if (itemId == null) continue;
                        String fileName = toSafeFileName(itemId) + ".png";
                        if (Files.exists(itemsDir.resolve(fileName))) {
                            tagIcons.addProperty(tagId, "/icon/cache/items/" + fileName);
                            break;
                        }
                    }
                }
            });
            root.add("tags", tagsArr);
            root.add("tagIcons", tagIcons);

            // 写入文件
            Files.writeString(manifestFile, GSON.toJson(root), StandardCharsets.UTF_8);
            LOGGER.info("[IconCache] manifest.json 已生成");

        } catch (Exception e) {
            LOGGER.error("[IconCache] 写入 manifest 失败", e);
            status = Status.ERROR;
            errorMessage = e.getMessage();
        }
    }

    public Status getStatus() {
        return status;
    }

    public int getProcessed() {
        return processed.get();
    }

    public int getTotal() {
        return total.get();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRunning() {
        return running.get();
    }

    public Path getCacheRoot() {
        return cacheRoot;
    }

    public Path getManifestFile() {
        return manifestFile;
    }

    /**
     * 创建占位符PNG图标
     * 当正常渲染失败时，生成一个基于种子颜色的简单方块作为替代
     *
     * @param size 图标尺寸
     * @param seed 种子值，用于生成颜色
     * @return PNG图片的字节数组
     */
    public static byte[] createPlaceholderPng(int size, int seed) {
        try {
            // 创建NativeImage
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
            // 根据种子生成RGB颜色
            int r = (seed >> 16) & 0xFF;
            int g = (seed >> 8) & 0xFF;
            int b = seed & 0xFF;
            int color = 0xFF000000 | (r << 16) | (g << 8) | b;
            // 填充整个图片
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    image.setPixelRGBA(x, y, color);
                }
            }
            // 编码为PNG
            byte[] bytes = encodePng(image);
            image.close();
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }
        } catch (Exception e) {
            LOGGER.warn("[IconCache] 创建占位图失败", e);
        }
        return new byte[0];
    }

    /**
     * 将NativeImage编码为PNG格式
     * 通过临时文件的方式实现编码
     *
     * @param image 要编码的图像
     * @return PNG字节数组，失败返回null
     */
    private static byte[] encodePng(NativeImage image) {
        try {
            // 创建临时文件
            Path tempFile = Files.createTempFile("icon_", ".png");
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
            // 忽略异常，返回null
        }
        return null;
    }
}