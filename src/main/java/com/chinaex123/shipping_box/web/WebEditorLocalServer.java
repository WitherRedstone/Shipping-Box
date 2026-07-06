package com.chinaex123.shipping_box.web;

import com.chinaex123.shipping_box.ShippingBox;
import com.chinaex123.shipping_box.event.ExchangeRecipeManager;
import com.chinaex123.shipping_box.network.PacketEditorReadFile;
import com.chinaex123.shipping_box.network.PacketEditorReloadRequest;
import com.chinaex123.shipping_box.network.PacketEditorSaveRules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 本地Web编辑器服务器
 * <p>
 * 在本地启动一个HTTP服务器，提供基于Web的规则编辑界面
 * 通过REST API与游戏服务端进行交互
 */
public final class WebEditorLocalServer {

    // JSON序列化工具，用于格式化输出
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 安全的随机数生成器，用于生成访问令牌
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MAX_REQUEST_LINE_BYTES = 4096;
    private static final int MAX_HEADER_LINE_BYTES = 8192;
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    // 服务器套接字，用于监听HTTP请求
    private static volatile ServerSocket serverSocket;

    // 接受客户端连接的线程
    private static volatile Thread acceptThread;

    // 线程池，用于处理客户端请求
    private static volatile ExecutorService executor;

    // 绑定的端口号
    private static volatile int boundPort = -1;

    // 访问令牌，用于验证请求合法性
    private static volatile String token;

    // 服务器运行状态标志
    private static volatile boolean running = false;

    // 私有构造函数，防止实例化
    private WebEditorLocalServer() {}

    /**
     * 启动本地Web服务器
     * @param desiredToken 期望的令牌，如果为空则自动生成
     * @return 访问URL，包含令牌参数
     * @throws IllegalStateException 如果无法绑定端口
     */
    public static synchronized String start(String desiredToken) {
        // 设置或生成访问令牌
        if (desiredToken == null || desiredToken.isBlank()) {
            token = generateToken();
        } else {
            token = desiredToken;
        }

        // 如果服务器已在运行，直接返回URL
        if (running) {
            return buildUrl();
        }

        // 尝试在38888-38950端口范围内绑定
        int port = 38888;
        ServerSocket created = null;
        while (port <= 38950) {
            try {
                created = new ServerSocket();
                created.setReuseAddress(true);
                created.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
                break;
            } catch (IOException e) {
                if (created != null) {
                    try { created.close(); } catch (IOException ignored) {}
                }
                created = null;
                port++;
            } catch (Exception e) {
                if (created != null) {
                    try { created.close(); } catch (IOException ignored) {}
                }
                created = null;
                port++;
            }
        }

        // 如果所有端口都被占用，抛出异常
        if (created == null) {
            throw new IllegalStateException("Failed to bind web editor to localhost ports 38888-38950");
        }

        // 初始化服务器状态
        serverSocket = created;
        boundPort = port;
        running = true;

        // 创建线程池和接受线程
        executor = Executors.newCachedThreadPool();
        acceptThread = new Thread(WebEditorLocalServer::acceptLoop, "shipping_box_web_editor");
        acceptThread.setDaemon(true);
        acceptThread.start();

        return buildUrl();
    }

    /**
     * 停止本地Web服务器
     * 关闭所有资源，释放端口
     */
    public static synchronized void stop() {
        running = false;
        boundPort = -1;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        acceptThread = null;
    }

    /**
     * 轮换访问令牌，生成新的令牌
     * @return 新的令牌
     */
    public static synchronized String rotateToken() {
        token = generateToken();
        return token;
    }

    /**
     * 构建访问URL
     * @return 完整的URL地址
     */
    private static String buildUrl() {
        return "http://127.0.0.1:" + boundPort + "/?token=" + token;
    }

    /**
     * 生成随机的Base64编码令牌
     * @return 生成的令牌字符串
     */
    private static String generateToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 检查请求是否已授权
     * @param uri 请求URI
     * @return true如果token匹配
     */
    private static boolean isAuthorized(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return false;
        }
        // 解析查询参数，查找token
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String key = part.substring(0, idx);
            String value = part.substring(idx + 1);
            if ("token".equals(key) && Objects.equals(value, token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析URI中的查询参数
     * @param uri 请求URI
     * @return 参数键值对映射
     */
    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return map;
        }
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String key = part.substring(0, idx);
            String value = part.substring(idx + 1);
            try {
                map.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            } catch (Exception e) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * 从URI中获取请求的文件名
     * @param uri 请求URI
     * @return 文件名，默认为"editor.json"
     */
    private static String getRequestedFile(URI uri) {
        String file = parseQuery(uri).getOrDefault("file", "editor.json");
        if (file == null || file.isBlank()) {
            return "editor.json";
        }
        return file;
    }

    /**
     * 从输入流中读取所有字节
     */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    /**
     * 接受客户端连接的主循环
     * 在独立线程中运行
     */
    private static void acceptLoop() {
        while (running) {
            ServerSocket socket = serverSocket;
            if (socket == null) {
                return;
            }
            try {
                // 接受客户端连接
                Socket client = socket.accept();
                client.setSoTimeout(8000);
                ExecutorService exec = executor;
                if (exec != null) {
                    // 在线程池中处理客户端请求
                    exec.execute(() -> handleClient(client));
                } else {
                    client.close();
                }
            } catch (IOException e) {
                if (running) {
                    continue;
                }
                return;
            }
        }
    }

    /**
     * 处理单个客户端请求
     * @param client 客户端Socket
     */
    private static void handleClient(Socket client) {
        try (client;
             InputStream rawIn = new BufferedInputStream(client.getInputStream());
             OutputStream out = client.getOutputStream()) {

            // 读取HTTP请求
            Request req = readRequest(rawIn);
            if (req == null) {
                writeText(out, 400, "Bad Request");
                return;
            }

            // 解析URI
            URI uri;
            try {
                uri = URI.create(req.pathWithQuery);
            } catch (Exception e) {
                writeText(out, 400, "Bad Request");
                return;
            }

            // 处理预检请求（CORS）
            if ("OPTIONS".equals(req.method)) {
                writeBytes(out, 204, "text/plain; charset=utf-8", new byte[0]);
                return;
            }

            // 验证授权
            if (!isAuthorized(uri)) {
                writeText(out, 401, "Unauthorized");
                return;
            }

            // 获取请求路径
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                writeText(out, 404, "Not Found");
                return;
            }

            // ===== 路由处理 =====

            // 1. 提供Web界面首页
            if ("GET".equals(req.method) && ("/".equals(path) || "/index.html".equals(path))) {
                byte[] html = readClasspath("/assets/shipping_box/web/index.html");
                if (html == null) {
                    writeText(out, 500, "Missing web resource");
                    return;
                }
                writeBytes(out, 200, "text/html; charset=utf-8", html);
                return;
            }

            // 2. 获取所有规则
            if ("GET".equals(req.method) && "/api/rules".equals(path)) {
                String rulesJson = ExchangeRecipeManager.serializeRulesToJson();
                writeBytes(out, 200, "application/json; charset=utf-8",
                        rulesJson.getBytes(StandardCharsets.UTF_8));
                return;
            }

            // 3. 获取注册表数据（物品和标签列表）
            if ("GET".equals(req.method) && "/api/registry".equals(path)) {
                handleRegistry(out);
                return;
            }

            // 4. 获取物品图标
            if ("GET".equals(req.method) && "/api/icon".equals(path)) {
                handleIcon(out, uri);
                return;
            }

            // 5. 获取缓存清单
            if ("GET".equals(req.method) && "/manifest".equals(path)) {
                handleManifest(out, uri);
                return;
            }

            // 6. 获取缓存状态
            if ("GET".equals(req.method) && "/cache_status".equals(path)) {
                handleCacheStatus(out, uri);
                return;
            }

            // 7. 获取缓存的图标文件
            if ("GET".equals(req.method) && path.startsWith("/icon/cache/")) {
                handleCachedIcon(out, uri, path);
                return;
            }

            // 8. 加载规则文件
            if ("GET".equals(req.method) && "/api/load".equals(path)) {
                handleLoad(out, uri);
                return;
            }

            // 9. 保存规则文件
            if ("POST".equals(req.method) && "/api/save".equals(path)) {
                handleSave(out, uri, req.bodyBytes);
                return;
            }

            // 10. 重新加载规则
            if ("POST".equals(req.method) && "/api/reload".equals(path)) {
                PacketDistributor.sendToServer(new PacketEditorReloadRequest());
                writeBytes(out, 200, "application/json; charset=utf-8",
                        "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            // 未匹配到任何路由
            writeText(out, 404, "Not Found");
        } catch (IOException ignored) {
            // 忽略IO异常
        }
    }

    /**
     * 处理注册表请求，返回所有物品和标签列表
     */
    private static void handleRegistry(OutputStream out) throws IOException {
        // 收集所有物品ID
        JsonArray items = new JsonArray();
        BuiltInRegistries.ITEM.forEach(item -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                items.add(id.toString());
            }
        });

        // 收集所有标签
        JsonArray tags = new JsonArray();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            var tagKey = pair.getFirst();
            ResourceLocation loc = tagKey.location();
            if (loc != null) {
                tags.add("#" + loc.getNamespace() + ":" + loc.getPath());
            }
        });

        // 构建JSON响应
        JsonObject resp = new JsonObject();
        resp.add("items", items);
        resp.add("tags", tags);
        writeBytes(out, 200, "application/json; charset=utf-8",
                GSON.toJson(resp).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 处理图标请求，优先从缓存获取
     */
    private static void handleIcon(OutputStream out, URI uri) throws IOException {
        String itemId = parseQuery(uri).getOrDefault("id", "");
        if (itemId.isBlank()) {
            itemId = parseQuery(uri).getOrDefault("item", "");
        }

        // 尝试从缓存目录获取图标
        if (!itemId.isBlank()) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null) {
                String fileName = (rl.getNamespace() + "_" + rl.getPath())
                        .replace(':', '_').replace('/', '_') + ".png";
                Path cacheDir = EditorIconCacheManager.getInstance().getCacheRoot();

                // 检查物品缓存
                Path inItems = cacheDir.resolve("items").resolve(fileName);
                if (Files.exists(inItems)) {
                    writeBytes(out, 200, "image/png", Files.readAllBytes(inItems));
                    return;
                }

                // 检查方块缓存
                Path inBlocks = cacheDir.resolve("blocks").resolve(fileName);
                if (Files.exists(inBlocks)) {
                    writeBytes(out, 200, "image/png", Files.readAllBytes(inBlocks));
                    return;
                }
            }
        }

        // 如果缓存不存在，返回透明PNG作为占位
        byte[] transparent = generateTransparentPng();
        writeBytes(out, 200, "image/png", transparent);
    }

    /**
     * 生成一个有效的透明PNG图片（1x1像素）
     * 优先使用NativeImage生成，失败则使用硬编码的Base64数据
     */
    private static byte[] generateTransparentPng() {
        try {
            // 创建1x1透明图像
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);
            img.setPixelRGBA(0, 0, 0);
            Path tempFile = Files.createTempFile("sbox_transparent_", ".png");
            img.writeToFile(tempFile);
            byte[] result = Files.readAllBytes(tempFile);
            Files.deleteIfExists(tempFile);
            img.close();
            if (result.length > 0) {
                return result;
            }
        } catch (Exception e) {
            // 忽略生成透明PNG失败
        }
        // 硬编码的最小有效透明PNG（Base64编码）
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO5WvKsAAAAASUVORK5CYII=");
    }

    /**
     * 处理清单请求，返回图标缓存清单
     */
    private static void handleManifest(OutputStream out, URI uri) throws IOException {
        if (!isAuthorized(uri)) {
            writeText(out, 401, "Unauthorized");
            return;
        }

        Path manifest = EditorIconCacheManager.getInstance().getManifestFile();
        if (Files.exists(manifest)) {
            // 返回已有的清单
            byte[] bytes = Files.readAllBytes(manifest);
            writeBytes(out, 200, "application/json; charset=utf-8", bytes);
        } else {
            // 返回缺失提示
            JsonObject resp = new JsonObject();
            resp.addProperty("status", "missing_cache");
            resp.addProperty("message", "Icon cache has not been generated yet. " +
                    "Please run /" + ShippingBox.MOD_ID + " editor cache_icons in game.");
            writeBytes(out, 200, "application/json; charset=utf-8",
                    GSON.toJson(resp).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 处理缓存状态请求，返回实时进度
     */
    private static void handleCacheStatus(OutputStream out, URI uri) throws IOException {
        if (!isAuthorized(uri)) {
            writeText(out, 401, "Unauthorized");
            return;
        }

        var mgr = EditorIconCacheManager.getInstance();
        JsonObject resp = new JsonObject();
        resp.addProperty("status", mgr.getStatus().name().toLowerCase());
        resp.addProperty("processed", mgr.getProcessed());
        resp.addProperty("total", mgr.getTotal());
        if (mgr.getErrorMessage() != null) {
            resp.addProperty("error", mgr.getErrorMessage());
        }
        writeBytes(out, 200, "application/json; charset=utf-8",
                GSON.toJson(resp).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 处理缓存图标文件请求，包含安全检查
     */
    private static void handleCachedIcon(OutputStream out, URI uri, String fullPath) throws IOException {
        if (!isAuthorized(uri)) {
            writeText(out, 401, "Unauthorized");
            return;
        }

        // 安全验证：防止路径遍历攻击
        String filename = fullPath.substring(fullPath.lastIndexOf('/') + 1);
        if (!filename.endsWith(".png") || filename.contains("..") ||
                filename.contains("/") || filename.contains("\\")) {
            writeText(out, 400, "Invalid filename");
            return;
        }

        // 确定文件位置
        Path cacheRoot = EditorIconCacheManager.getInstance().getCacheRoot();
        Path target;
        if (fullPath.contains("/items/")) {
            target = cacheRoot.resolve("items").resolve(filename);
        } else if (fullPath.contains("/blocks/")) {
            target = cacheRoot.resolve("blocks").resolve(filename);
        } else {
            writeText(out, 404, "Not Found");
            return;
        }

        // 检查文件存在且是普通文件
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            writeText(out, 404, "Not Found");
            return;
        }

        // 返回图片文件
        byte[] bytes = Files.readAllBytes(target);
        writeBytes(out, 200, "image/png", bytes);
    }

    /**
     * 处理文件加载请求
     */
    private static void handleLoad(OutputStream out, URI uri) throws IOException {
        String file = getRequestedFile(uri);
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<WebEditorRequestTracker.Response> future = WebEditorRequestTracker.create(requestId);
        PacketDistributor.sendToServer(new PacketEditorReadFile(requestId, file));

        // 等待服务器响应，超时5秒
        WebEditorRequestTracker.Response response;
        try {
            response = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            writeText(out, 504, "Timed out waiting for server");
            return;
        }

        if (!response.ok()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("ok", false);
            obj.addProperty("error", response.error());
            writeBytes(out, 200, "application/json; charset=utf-8",
                    GSON.toJson(obj).getBytes(StandardCharsets.UTF_8));
            return;
        }

        // 解析并返回JSON内容
        String content = response.payload();
        try {
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            writeBytes(out, 200, "application/json; charset=utf-8",
                    GSON.toJson(obj).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 如果JSON无效，返回错误信息
            JsonObject fallback = new JsonObject();
            fallback.addProperty("ok", false);
            fallback.addProperty("error", "Invalid JSON in file: " + file);
            fallback.addProperty("raw", content);
            writeBytes(out, 200, "application/json; charset=utf-8",
                    GSON.toJson(fallback).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 处理文件保存请求
     */
    private static void handleSave(OutputStream out, URI uri, byte[] bodyBytes) throws IOException {
        // 解析请求体
        String body = new String(bodyBytes == null ? new byte[0] : bodyBytes, StandardCharsets.UTF_8);
        JsonObject obj;
        try {
            obj = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonParseException e) {
            writeText(out, 400, "Invalid JSON");
            return;
        }

        // 验证请求格式
        if (!obj.has("rules") || !obj.get("rules").isJsonArray()) {
            writeText(out, 400, "Expected {\"rules\": [...]}");
            return;
        }

        // 发送保存请求到服务端
        String file = getRequestedFile(uri);
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<WebEditorRequestTracker.Response> future = WebEditorRequestTracker.create(requestId);
        PacketDistributor.sendToServer(new PacketEditorSaveRules(requestId, file, GSON.toJson(obj)));

        // 等待响应
        WebEditorRequestTracker.Response response;
        try {
            response = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            writeText(out, 504, "Timed out waiting for server");
            return;
        }

        // 返回保存结果
        JsonObject res = new JsonObject();
        res.addProperty("ok", response.ok());
        if (response.ok()) {
            res.addProperty("savedPath", response.payload());
        } else {
            res.addProperty("error", response.error());
        }
        writeBytes(out, 200, "application/json; charset=utf-8",
                GSON.toJson(res).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从类路径读取资源文件
     */
    private static byte[] readClasspath(String path) throws IOException {
        try (InputStream in = WebEditorLocalServer.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return readAllBytes(in);
        }
    }

    /**
     * 发送文本响应
     */
    private static void writeText(OutputStream out, int status, String text) throws IOException {
        writeBytes(out, status, "text/plain; charset=utf-8",
                text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 发送字节响应（默认禁止缓存）
     */
    private static void writeBytes(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        writeBytes(out, status, contentType, body, "no-store");
    }

    /**
     * 发送HTTP响应
     * @param status HTTP状态码
     * @param contentType 内容类型
     * @param body 响应体
     * @param cacheControl 缓存控制头
     */
    private static void writeBytes(OutputStream out, int status, String contentType,
                                   byte[] body, String cacheControl) throws IOException {
        // 状态码对应的描述
        String reason = switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            case 504 -> "Gateway Timeout";
            default -> "OK";
        };

        byte[] bytes = body == null ? new byte[0] : body;
        // 构建HTTP响应头
        String headers =
                "HTTP/1.1 " + status + " " + reason + "\r\n" +
                        "Content-Type: " + contentType + "\r\n" +
                        "Cache-Control: " + (cacheControl == null || cacheControl.isBlank() ? "no-store" : cacheControl) + "\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type\r\n" +
                        "Access-Control-Max-Age: 86400\r\n" +
                        "Connection: close\r\n" +
                        "Content-Length: " + bytes.length + "\r\n" +
                        "\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    /**
     * 读取HTTP请求
     * @param in 输入流
     * @return 请求对象，包含方法、路径和请求体
     */
    private static Request readRequest(InputStream in) throws IOException {
        String requestLine = readLine(in, MAX_REQUEST_LINE_BYTES);
        if (requestLine == null || requestLine.isBlank()) {
            return null;
        }
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return null;
        }
        String method = parts[0].trim().toUpperCase();
        String pathWithQuery = parts[1].trim();

        int contentLength = 0;
        boolean hasContentLength = false;
        int headerBytes = requestLine.length() + 2;
        while (true) {
            String line = readLine(in, MAX_HEADER_LINE_BYTES);
            if (line == null) {
                return null;
            }
            headerBytes += line.length() + 2;
            if (headerBytes > MAX_HEADER_BYTES) {
                return null;
            }
            if (line.isEmpty()) {
                break;
            }
            int idx = line.indexOf(':');
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if ("Content-Length".equalsIgnoreCase(key)) {
                    try {
                        contentLength = Integer.parseInt(value);
                        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                            return null;
                        }
                        hasContentLength = true;
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        }

        byte[] body = null;
        if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) && hasContentLength) {
            body = readFixedBytes(in, contentLength);
            if (body.length != contentLength) {
                return null;
            }
        }
        return new Request(method, pathWithQuery, body);
    }

    /**
     * 浠庤緭鍏ユ祦璇诲彇涓€琛屾枃鏈?
     */
    private static String readLine(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(128, maxBytes));
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (baos.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                if (baos.size() >= maxBytes) {
                    return null;
                }
                baos.write(b);
            }
        }
        return baos.toString(StandardCharsets.ISO_8859_1);
    }

    /**
     * 浠庤緭鍏ユ祦璇诲彇鎸囧畾闀垮害鐨勫瓧鑺?
     */
    private static byte[] readFixedBytes(InputStream in, int length) throws IOException {
        if (length <= 0) {
            return new byte[0];
        }
        if (length > MAX_BODY_BYTES) {
            return new byte[0];
        }
        byte[] buf = new byte[length];
        int off = 0;
        while (off < length) {
            int read = in.read(buf, off, length - off);
            if (read == -1) {
                break;
            }
            off += read;
        }
        if (off == length) {
            return buf;
        }
        byte[] partial = new byte[off];
        System.arraycopy(buf, 0, partial, 0, off);
        return partial;
    }

    /**
     * HTTP请求记录类
     */
    private record Request(String method, String pathWithQuery, byte[] bodyBytes) {}
}