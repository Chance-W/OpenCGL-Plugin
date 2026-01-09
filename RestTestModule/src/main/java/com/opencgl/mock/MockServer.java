package com.opencgl.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * 内置 Mock 服务器
 * 基于 JDK 内置 HttpServer，无需额外依赖
 */
public class MockServer {
    
    private static final Logger logger = LoggerFactory.getLogger(MockServer.class);
    
    private HttpServer server;
    private ExecutorService executor;
    private int port;
    private boolean running = false;
    
    private final ObservableList<MockRule> rules = FXCollections.observableArrayList();
    private final ObservableList<MockRequestLog> requestLogs = FXCollections.observableArrayList();
    private Consumer<MockRequestLog> onRequestCallback;
    
    public MockServer() {
        this(8888);
    }
    
    public MockServer(int port) {
        this.port = port;
    }
    
    /**
     * 启动服务器
     */
    public boolean start() {
        if (running) {
            return true;
        }
        
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new MockHandler());
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.start();
            running = true;
            logger.info("Mock server started on port {}", port);
            return true;
        } catch (IOException e) {
            logger.error("Failed to start mock server on port {}", port, e);
            return false;
        }
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        HttpServer serverToStop = server;
        ExecutorService executorToStop = executor;
        server = null;
        executor = null;
        running = false;
        if (serverToStop != null) {
            try { serverToStop.stop(0); } catch (RuntimeException e) { logger.error("Failed to stop mock server", e); }
        }
        if (executorToStop != null) {
            executorToStop.shutdownNow();
            logger.info("Mock server stopped");
        }
    }
    
    /**
     * 添加 Mock 规则
     */
    public void addRule(MockRule rule) {
        rules.add(rule);
    }
    
    /**
     * 删除 Mock 规则
     */
    public void removeRule(MockRule rule) {
        rules.remove(rule);
    }
    
    /**
     * 清空所有规则
     */
    public void clearRules() {
        rules.clear();
    }
    
    /**
     * 获取所有规则
     */
    public ObservableList<MockRule> getRules() {
        return rules;
    }
    
    /**
     * 获取请求日志
     */
    public ObservableList<MockRequestLog> getRequestLogs() {
        return requestLogs;
    }
    
    /**
     * 设置请求回调
     */
    public void setOnRequest(Consumer<MockRequestLog> callback) {
        this.onRequestCallback = callback;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    /**
     * 请求处理器
     */
    private class MockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            
            // 读取请求体
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            
            // 记录请求
            MockRequestLog log = new MockRequestLog(method, path, query, requestBody);
            Platform.runLater(() -> {
                requestLogs.add(0, log);
                // 限制日志数量
                while (requestLogs.size() > 100) {
                    requestLogs.remove(requestLogs.size() - 1);
                }
                if (onRequestCallback != null) {
                    onRequestCallback.accept(log);
                }
            });
            
            // 查找匹配的规则
            MockRule matchedRule = findMatchingRule(method, path);
            
            if (matchedRule != null) {
                // 应用延迟
                if (matchedRule.getDelayMs() > 0) {
                    try {
                        Thread.sleep(matchedRule.getDelayMs());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                // 设置响应头
                if (matchedRule.getResponseHeaders() != null) {
                    matchedRule.getResponseHeaders().forEach((key, value) -> 
                        exchange.getResponseHeaders().add(key, value)
                    );
                }
                
                // 设置 Content-Type
                if (matchedRule.getContentType() != null) {
                    exchange.getResponseHeaders().add("Content-Type", matchedRule.getContentType());
                }
                
                // 发送响应
                byte[] responseBytes = matchedRule.getResponseBody().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(matchedRule.getStatusCode(), responseBytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
                
                log.setMatched(true);
                log.setMatchedRule(matchedRule.getName());
            } else {
                // 没有匹配的规则，返回 404
                String notFound = "{\"error\": \"No matching mock rule found\", \"path\": \"" + path + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, notFound.length());
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                
                log.setMatched(false);
            }
        }
        
        private MockRule findMatchingRule(String method, String path) {
            for (MockRule rule : rules) {
                if (!rule.isEnabled()) continue;
                
                // 方法匹配
                if (!rule.getMethod().equals("*") && !rule.getMethod().equalsIgnoreCase(method)) {
                    continue;
                }
                
                // 路径匹配
                if (rule.isRegex()) {
                    if (path.matches(rule.getPath())) {
                        return rule;
                    }
                } else {
                    if (rule.getPath().equals(path) || rule.getPath().equals("*")) {
                        return rule;
                    }
                }
            }
            return null;
        }
    }
}
