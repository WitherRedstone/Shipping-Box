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
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;

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
 * 编辑器图标缓存管理器。
 * <p>
 * 负责分批渲染并缓存所有物品与方块的图标 PNG，供 Web 编辑器使用。
 * 缓存任务按每刻固定数量推进，并通过 Boss 条显示进度；
 * 完成后生成 manifest.json 描述图标索引、标签及标签代表图标。
 * 通过缓存版本号判断清单是否可用，并通过单例提供全局访问。
 */
public class EditorIconCacheManager {

    /** JSON 序列化器 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 图标输出尺寸 */
    public static final int ICON_SIZE = 32;
    /** 每游戏刻处理的图标数量 */
    public static final int ICONS_PER_TICK = 3;
    /** 缓存清单版本号，用于判断旧版清单是否需要重建 */
    public static final int CACHE_VERSION = 5;

    /** 单例实例 */
    private static final EditorIconCacheManager INSTANCE = new EditorIconCacheManager();

    /** 缓存根目录 */
    private final Path cacheRoot;
    /** 物品图标目录 */
    private final Path itemsDir;
    /** 方块图标目录 */
    private final Path blocksDir;
    /** 清单文件路径 */
    private final Path manifestFile;

    /** 待处理的缓存条目列表 */
    private final List<CacheEntry> pendingEntries = new ArrayList<>();

    /** 已处理数量 */
    private final AtomicInteger processed = new AtomicInteger(0);
    /** 总条目数量 */
    private final AtomicInteger total = new AtomicInteger(0);

    /** 当前任务状态 */
    private volatile Status status = Status.IDLE;
    /** 错误信息（发生错误时） */
    private volatile String errorMessage = null;

    /** 任务是否正在运行 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 是否为强制模式（忽略已有缓存重新生成） */
    private boolean forceMode = false;

    /** 进度 Boss 条的唯一标识 */
    private UUID bossBarId;

    /**
     * 私有构造函数，初始化缓存目录并加载已有清单状态。
     */
    private EditorIconCacheManager() {
        this.cacheRoot = FMLPaths.GAMEDIR.get()
                .resolve("config")
                .resolve(ShippingBox.MOD_ID)
                .resolve("editor_icon_cache");
        this.itemsDir = cacheRoot.resolve("items");
        this.blocksDir = cacheRoot.resolve("blocks");
        this.manifestFile = cacheRoot.resolve("manifest.json");

        loadStatusFromManifest();
    }

    /**
     * 更新进度 Boss 条。
     * <p>
     * 根据已处理与总量计算进度，并通过反射将事件写入原版 Boss 条事件表。
     * 反射失败时仅记录 trace 日志，不影响缓存流程。
     */
    private void updateBossBar() {
        if (bossBarId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;

        float progress = total.get() > 0 ? (float) processed.get() / total.get() : 0f;
        Component title = Component.translatable(
                "command.shipping_box.icon_cache.bossbar.title",
                processed.get(), total.get());

        LerpingBossEvent event = new LerpingBossEvent(
                bossBarId,
                title,
                progress,
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS,
                false, false, false
        );

        try {
            Field eventsField = BossHealthOverlay.class.getDeclaredField("events");
            eventsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, LerpingBossEvent> events = (Map<UUID, LerpingBossEvent>) eventsField.get(mc.gui.hud.getBossOverlay());
            events.put(bossBarId, event);
        } catch (Exception e) {
            ShippingBox.LOGGER.trace("[EditorIconCacheManager.updateBossBar] 更新 Boss 条失败（可能是映射名变化）", e);
        }
    }

    /**
     * 移除进度 Boss 条。
     * <p>
     * 通过反射从原版 Boss 条事件表中移除本任务的进度条，
     * 并清空任务标识。反射失败时仅记录 trace 日志。
     */
    private void removeBossBar() {
        if (bossBarId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;

        try {
            Field eventsField = BossHealthOverlay.class.getDeclaredField("events");
            eventsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, LerpingBossEvent> events = (Map<UUID, LerpingBossEvent>) eventsField.get(mc.gui.hud.getBossOverlay());
            events.remove(bossBarId);
        } catch (Exception e) {
            ShippingBox.LOGGER.trace("[EditorIconCacheManager.removeBossBar] 移除 Boss 条失败（可能是映射名变化）", e);
        }
        bossBarId = null;
    }

    /**
     * 从清单文件加载缓存的就绪状态。
     * <p>
     * 仅当清单版本号与当前缓存版本一致且标记为 ready/done 时，
     * 才将状态置为 DONE 并以物品与方块数量填充处理计数；
     * 版本不一致时记录日志并等待重建。
     */
    private void loadStatusFromManifest() {
        try {
            if (Files.exists(manifestFile)) {
                String json = Files.readString(manifestFile, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                String statusStr = obj.has("status") ? obj.get("status").getAsString() : "";
                int version = obj.has("version") ? obj.get("version").getAsInt() : 0;

                if (version == CACHE_VERSION && ("ready".equals(statusStr) || "done".equals(statusStr))) {
                    this.status = Status.DONE;
                    int itemCount = obj.has("items") ? obj.getAsJsonArray("items").size() : 0;
                    int blockCount = obj.has("blocks") ? obj.getAsJsonArray("blocks").size() : 0;
                    this.total.set(itemCount + blockCount);
                    this.processed.set(this.total.get());
                    ShippingBox.LOGGER.info("[EditorIconCacheManager.loadStatusFromManifest] 从 manifest 加载就绪状态，物品: {}，方块: {}", itemCount, blockCount);
                } else if ("ready".equals(statusStr) || "done".equals(statusStr)) {
                    ShippingBox.LOGGER.info("[EditorIconCacheManager.loadStatusFromManifest] 忽略旧版 manifest，缓存版本: {}，等待重建", version);
                }
            }
        } catch (Exception e) {
            ShippingBox.LOGGER.warn("[EditorIconCacheManager.loadStatusFromManifest] 加载 manifest 状态失败", e);
        }
    }

    /**
     * 获取单例实例。
     *
     * @return 图标缓存管理器实例
     */
    public static EditorIconCacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * 缓存任务状态枚举。
     * <p>
     * IDLE 表示空闲，RUNNING 表示进行中，DONE 表示已完成，ERROR 表示出错。
     */
    public enum Status {
        IDLE,
        RUNNING,
        DONE,
        ERROR
    }

    /**
     * 缓存条目记录。
     *
     * @param id          物品或方块的标识符
     * @param isBlock     是否为方块条目
     * @param displayName 显示名称
     * @param fileName    输出文件名（不含扩展名）
     */
    private record CacheEntry(Identifier id, boolean isBlock, String displayName, String fileName) {}

    /**
     * 启动图标缓存任务。
     * <p>
     * 若任务已在运行：强制模式下会先停止当前任务并清空缓存，否则直接返回。
     * 随后按是否需要强制重建准备条目；无待处理条目时直接标记完成并写入清单，
     * 否则进入运行状态并创建进度 Boss 条。
     *
     * @param force 是否强制重建（忽略已有缓存）
     */
    public void startCache(boolean force) {
        if (running.get()) {
            if (force) {
                stopCurrentTask();
                clearCacheInternal(false);
            } else {
                return;
            }
        }

        ensureDirectories();

        if (force) {
            forceMode = true;
            clearCacheInternal(false);
        } else {
            forceMode = false;
        }

        prepareEntries();

        if (pendingEntries.isEmpty()) {
            status = Status.DONE;
            total.set(0);
            processed.set(0);
            writeManifest();
            return;
        }

        total.set(pendingEntries.size());
        processed.set(0);
        status = Status.RUNNING;
        errorMessage = null;
        running.set(true);

        bossBarId = UUID.randomUUID();
        updateBossBar();

        ShippingBox.LOGGER.info("[EditorIconCacheManager.startCache] 开始缓存任务，共 {} 个条目", total.get());
    }

    /**
     * 停止当前缓存任务。
     * <p>
     * 通过原子比较避免重复停止，停止后移除进度 Boss 条。
     */
    public void stopCurrentTask() {
        if (running.compareAndSet(true, false)) {
            status = Status.IDLE;
            removeBossBar();
            ShippingBox.LOGGER.info("[EditorIconCacheManager.stopCurrentTask] 缓存任务已停止");
        }
    }

    /**
     * 清空缓存并重置状态。
     * <p>
     * 先停止当前任务，再删除缓存目录内容并重置状态为 IDLE。
     */
    public void clearCache() {
        stopCurrentTask();
        clearCacheInternal(true);
        ShippingBox.LOGGER.info("[EditorIconCacheManager.clearCache] 缓存已清除");
    }

    /**
     * 确保缓存目录存在。
     */
    private void ensureDirectories() {
        try {
            Files.createDirectories(itemsDir);
            Files.createDirectories(blocksDir);
        } catch (Exception e) {
            ShippingBox.LOGGER.error("[EditorIconCacheManager.ensureDirectories] 创建缓存目录失败", e);
        }
    }

    /**
     * 清空缓存目录内容并重置计数。
     * <p>
     * 按深度逆序删除缓存目录下的所有文件，随后重建物品与方块目录；
     * 是否重置状态由参数控制。
     *
     * @param resetStatus 是否同时重置状态为 IDLE
     */
    private void clearCacheInternal(boolean resetStatus) {
        removeBossBar();

        try {
            if (Files.exists(cacheRoot)) {
                Files.walk(cacheRoot)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                ShippingBox.LOGGER.warn("[EditorIconCacheManager.clearCacheInternal] 删除缓存文件失败，路径: {}", path, e);
                            }
                        });
            }
            Files.createDirectories(itemsDir);
            Files.createDirectories(blocksDir);
        } catch (Exception e) {
            ShippingBox.LOGGER.error("[EditorIconCacheManager.clearCacheInternal] 清空缓存失败", e);
        }

        pendingEntries.clear();
        processed.set(0);
        total.set(0);

        if (resetStatus) {
            status = Status.IDLE;
            errorMessage = null;
        }
    }

    /**
     * 准备待处理的缓存条目。
     * <p>
     * 遍历物品与方块注册表（跳过空气与无有效 ID 的条目），
     * 为每个条目生成文件名与显示名称加入待处理列表。
     */
    private void prepareEntries() {
        pendingEntries.clear();

        BuiltInRegistries.ITEM.forEach(item -> {
            if (item == Items.AIR) return;
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) return;
            String fileName = toSafeFileName(id);
            String display = new ItemStack(item).getHoverName().getString();
            pendingEntries.add(new CacheEntry(id, false, display, fileName));
        });

        BuiltInRegistries.BLOCK.forEach(block -> {
            Item item = block.asItem();
            if (item == Items.AIR) return;
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) return;
            String fileName = toSafeFileName(id);
            String display = new ItemStack(item).getHoverName().getString();
            pendingEntries.add(new CacheEntry(id, true, display, fileName));
        });
    }

    /**
     * 将标识符转换为安全的文件名。
     *
     * @param id 物品或方块标识符
     * @return 安全的文件名（不含扩展名）
     */
    private String toSafeFileName(Identifier id) {
        return (id.getNamespace() + "_" + id.getPath()).replace(':', '_').replace('/', '_');
    }

    /**
     * 每游戏刻推进缓存任务。
     * <p>
     * 按每刻固定数量依次处理待处理条目：渲染图标 PNG 并写入对应目录，
     * 渲染失败时以占位图代替，无法解析为物品的条目标记处理后跳过。
     * 全部处理完成后写入清单、移除进度条并标记完成；否则更新进度条。
     */
    public void tick() {
        if (!running.get() || status != Status.RUNNING) return;

        int processedThisTick = 0;
        while (processedThisTick < ICONS_PER_TICK && !pendingEntries.isEmpty()) {
            CacheEntry entry = pendingEntries.remove(0);

            try {
                ItemStack stack = entry.isBlock()
                        ? BuiltInRegistries.BLOCK.get(entry.id())
                          .map(Holder::value)
                          .map(Block::asItem)
                          .map(ItemStack::new)
                          .orElse(ItemStack.EMPTY)
                        : BuiltInRegistries.ITEM.get(entry.id())
                          .map(Holder::value)
                          .map(ItemStack::new)
                          .orElse(ItemStack.EMPTY);

                if (stack.isEmpty()) {
                    processed.incrementAndGet();
                    processedThisTick++;
                    continue;
                }

                byte[] png = ItemIconPngRenderer.renderStackToPng(stack, ICON_SIZE, entry.isBlock());
                if (png == null || png.length == 0) {
                    png = createPlaceholderPng(ICON_SIZE, entry.id().hashCode());
                    ShippingBox.LOGGER.warn("[EditorIconCacheManager.tick] 渲染失败，使用占位图，物品: {}", entry.id());
                }

                Path targetDir = entry.isBlock() ? blocksDir : itemsDir;
                Path targetFile = targetDir.resolve(entry.fileName() + ".png");
                Files.write(targetFile, png);

            } catch (Exception e) {
                ShippingBox.LOGGER.warn("[EditorIconCacheManager.tick] 处理条目失败，物品: {}", entry.id(), e);
            } finally {
                processed.incrementAndGet();
                processedThisTick++;
            }
        }

        if (pendingEntries.isEmpty()) {
            running.set(false);
            status = Status.DONE;
            writeManifest();
            removeBossBar();
            ShippingBox.LOGGER.info("[EditorIconCacheManager.tick] 缓存任务完成，共处理 {} 个", processed.get());
        } else if (running.get()) {
            updateBossBar();
        }
    }

    /**
     * 写入清单文件。
     * <p>
     * 汇总已生成的物品与方块图标路径、标签列表及标签代表图标，
     * 输出为 manifest.json。写入失败时将状态置为 ERROR 并记录错误信息。
     */
    private void writeManifest() {
        try {
            Files.createDirectories(cacheRoot);

            JsonObject root = new JsonObject();
            root.addProperty("version", CACHE_VERSION);
            root.addProperty("modid", ShippingBox.MOD_ID);
            root.addProperty("iconSize", ICON_SIZE);
            root.addProperty("iconsPerTick", ICONS_PER_TICK);
            root.addProperty("status", "ready");
            root.addProperty("generatedAt", System.currentTimeMillis());

            JsonArray itemsArr = new JsonArray();
            BuiltInRegistries.ITEM.forEach(item -> {
                if (item == Items.AIR) return;
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
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

            JsonArray blocksArr = new JsonArray();
            BuiltInRegistries.BLOCK.forEach(block -> {
                Item item = block.asItem();
                if (item == Items.AIR) return;
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
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

            JsonArray tagsArr = new JsonArray();
            JsonObject tagIcons = new JsonObject();
            BuiltInRegistries.ITEM.getTags().forEach(named -> {
                var tagKey = named.key();
                Identifier loc = tagKey.location();
                if (loc == null) return;
                String tagId = "#" + loc.getNamespace() + ":" + loc.getPath();
                tagsArr.add(tagId);

                var holders = BuiltInRegistries.ITEM.getTagOrEmpty(tagKey);
                for (var holder : holders) {
                    Item item = holder.value();
                    Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                    if (itemId == null) continue;
                    String fileName = toSafeFileName(itemId) + ".png";
                    if (Files.exists(itemsDir.resolve(fileName))) {
                        tagIcons.addProperty(tagId, "/icon/cache/items/" + fileName);
                        break;
                    }
                }
            });
            root.add("tags", tagsArr);
            root.add("tagIcons", tagIcons);

            Files.writeString(manifestFile, GSON.toJson(root), StandardCharsets.UTF_8);
            ShippingBox.LOGGER.info("[EditorIconCacheManager.writeManifest] manifest.json 已生成");

        } catch (Exception e) {
            ShippingBox.LOGGER.error("[EditorIconCacheManager.writeManifest] 写入 manifest 失败", e);
            status = Status.ERROR;
            errorMessage = e.getMessage();
        }
    }

    /**
     * 获取当前任务状态。
     *
     * @return 任务状态
     */
    public Status getStatus() {
        return status;
    }

    /**
     * 获取已处理数量。
     *
     * @return 已处理数量
     */
    public int getProcessed() {
        return processed.get();
    }

    /**
     * 获取总条目数量。
     *
     * @return 总条目数量
     */
    public int getTotal() {
        return total.get();
    }

    /**
     * 获取错误信息。
     *
     * @return 错误信息，无错误时返回 null
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 判断任务是否正在运行。
     *
     * @return 正在运行返回 true
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取缓存根目录。
     *
     * @return 缓存根目录
     */
    public Path getCacheRoot() {
        return cacheRoot;
    }

    /**
     * 获取清单文件路径。
     *
     * @return 清单文件路径
     */
    public Path getManifestFile() {
        return manifestFile;
    }

    /**
     * 生成占位图 PNG。
     * <p>
     * 依据种子生成单色图像并编码为 PNG，用于渲染失败时的替代图标。
     *
     * @param size 图像边长
     * @param seed 颜色种子
     * @return PNG 字节数组，生成失败时返回空数组
     */
    public static byte[] createPlaceholderPng(int size, int seed) {
        try {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
            int r = (seed >> 16) & 0xFF;
            int g = (seed >> 8) & 0xFF;
            int b = seed & 0xFF;
            int color = 0xFF000000 | (r << 16) | (g << 8) | b;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    image.setPixel(x, y, color);
                }
            }
            byte[] bytes = encodePng(image);
            image.close();
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }
        } catch (Exception e) {
            ShippingBox.LOGGER.warn("[EditorIconCacheManager.createPlaceholderPng] 创建占位图失败", e);
        }
        return new byte[0];
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
            tempFile = Files.createTempFile("icon_", ".png");
            image.writeToFile(tempFile);
            byte[] result = Files.readAllBytes(tempFile);
            if (result.length > 0) {
                return result;
            }
        } catch (Exception e) {
            ShippingBox.LOGGER.warn("[EditorIconCacheManager.encodePng] 编码 PNG 失败", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    ShippingBox.LOGGER.warn("[EditorIconCacheManager.encodePng] 删除临时文件失败，路径: {}", tempFile, e);
                }
            }
        }
        return null;
    }
}