package com.chinaex123.shipping_box.web;

import com.chinaex123.shipping_box.network.PacketEditorReadFile;
import com.chinaex123.shipping_box.network.PacketEditorSaveRules;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web编辑器请求追踪器
 *
 * <p>功能说明：</p>
 * <ul>
 *   <li>用于追踪和管理Web编辑器发起的异步请求</li>
 *   <li>将请求ID与CompletableFuture关联，实现请求-响应的配对</li>
 *   <li>支持多线程并发访问，使用ConcurrentHashMap保证线程安全</li>
 * </ul>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>Web服务器收到前端请求后，生成唯一的请求ID</li>
 *   <li>调用 {@link #create(String)} 创建CompletableFuture并注册</li>
 *   <li>通过网络包将请求发送到游戏服务端</li>
 *   <li>服务端处理完成后，通过响应包返回结果</li>
 *   <li>响应处理器调用 {@link #complete(String, Response)} 完成Future</li>
 *   <li>Web服务器从Future获取结果并返回给前端</li>
 * </ol>
 *
 * @see WebEditorLocalServer 使用此追踪器处理HTTP请求
 * @see PacketEditorReadFile 读取文件的网络包
 * @see PacketEditorSaveRules 保存规则的网络包
 */
public final class WebEditorRequestTracker {

    /**
     * 存储所有待处理的请求
     * Key: 请求ID (UUID字符串)
     * Value: 对应的CompletableFuture，用于等待响应
     * <p>
     * 使用ConcurrentHashMap保证多线程环境下的线程安全
     */
    private static final Map<String, CompletableFuture<Response>> PENDING = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止实例化
     * 该类只提供静态方法，不需要实例
     */
    private WebEditorRequestTracker() {}

    /**
     * 创建一个新的请求追踪条目
     *
     * <p>生成一个CompletableFuture并注册到待处理列表中，
     * 后续可以通过请求ID完成这个Future</p>
     *
     * @param requestId 请求的唯一标识符（通常使用UUID）
     * @return 刚创建的CompletableFuture，调用方可以等待它完成
     */
    public static CompletableFuture<Response> create(String requestId) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        PENDING.put(requestId, future);
        return future;
    }

    /**
     * 完成一个请求并返回响应
     *
     * <p>当服务端处理完请求后，调用此方法将结果传递给等待的Web服务器</p>
     * <p>注意：如果请求ID不存在（可能已超时或被取消），则忽略此次完成操作</p>
     *
     * @param requestId 请求的唯一标识符
     * @param response 响应对象，包含处理结果
     */
    public static void complete(String requestId, Response response) {
        CompletableFuture<Response> future = PENDING.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * 响应记录类
     *
     * <p>封装了请求处理的响应结果</p>
     *
     * @param ok 是否成功
     * @param payload 成功时的数据内容（如文件内容、保存路径等）
     * @param error 失败时的错误信息
     */
    public record Response(boolean ok, String payload, String error) { }
}