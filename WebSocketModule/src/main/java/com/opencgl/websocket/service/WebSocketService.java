package com.opencgl.websocket.service;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * WebSocket 服务
 * 支持 WebSocket 连接、发送和接收消息
 *
 * @author OpenCGL
 */
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);

    private WebSocketClient client;
    private final Queue<MessageInfo> messageHistory = new ConcurrentLinkedQueue<>();
    private Consumer<MessageInfo> onMessageCallback;
    private Consumer<String> onStatusCallback;
    private volatile boolean isConnected = false;

    /**
     * 连接到 WebSocket 服务器
     */
    public void connect(String url, Map<String, String> headers) throws Exception {
        disconnect();
        
        URI uri = new URI(url);
        
        client = new WebSocketClient(uri, headers != null ? headers : new HashMap<>()) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                isConnected = true;
                String status = "已连接: " + handshake.getHttpStatus() + " " + handshake.getHttpStatusMessage();
                if (onStatusCallback != null) {
                    onStatusCallback.accept(status);
                }
            }

            @Override
            public void onMessage(String message) {
                MessageInfo msg = new MessageInfo("RECV", message, System.currentTimeMillis());
                messageHistory.offer(msg);
                if (messageHistory.size() > 1000) {
                    messageHistory.poll();
                }
                if (onMessageCallback != null) {
                    onMessageCallback.accept(msg);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                isConnected = false;
                String status = String.format("已断开: code=%d, reason=%s, remote=%s", code, reason, remote);
                if (onStatusCallback != null) {
                    onStatusCallback.accept(status);
                }
            }

            @Override
            public void onError(Exception ex) {
                if (onStatusCallback != null) {
                    onStatusCallback.accept("错误: " + ex.getMessage());
                }
                logger.error("WebSocket 错误", ex);
            }
        };
        
        client.connectBlocking();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (client != null) {
            try {
                client.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            client = null;
        }
        isConnected = false;
    }

    public void dispose() {
        disconnect();
        onMessageCallback = null;
        onStatusCallback = null;
        messageHistory.clear();
    }

    /**
     * 发送消息
     */
    public void send(String message) {
        if (client == null || !isConnected) {
            throw new IllegalStateException("未连接");
        }
        
        client.send(message);
        
        MessageInfo msg = new MessageInfo("SEND", message, System.currentTimeMillis());
        messageHistory.offer(msg);
        if (messageHistory.size() > 1000) {
            messageHistory.poll();
        }
        if (onMessageCallback != null) {
            onMessageCallback.accept(msg);
        }
    }

    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return isConnected && client != null && client.isOpen();
    }

    /**
     * 设置消息回调
     */
    public void setOnMessage(Consumer<MessageInfo> callback) {
        this.onMessageCallback = callback;
    }

    /**
     * 设置状态回调
     */
    public void setOnStatus(Consumer<String> callback) {
        this.onStatusCallback = callback;
    }

    /**
     * 获取消息历史
     */
    public List<MessageInfo> getHistory() {
        return new ArrayList<>(messageHistory);
    }

    /**
     * 清空历史
     */
    public void clearHistory() {
        messageHistory.clear();
    }

    public record MessageInfo(String type, String content, long timestamp) {}
}
