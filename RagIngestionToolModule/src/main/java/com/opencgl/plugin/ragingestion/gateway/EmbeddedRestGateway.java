package com.opencgl.plugin.ragingestion.gateway;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.opencgl.plugin.ragingestion.engine.EmbeddingEngine;
import com.opencgl.plugin.ragingestion.model.SearchResult;
import com.opencgl.plugin.ragingestion.storage.VectorStorageDriver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * 内嵌轻量级 HTTP REST 网关。
 * 开启后对外暴露 /api/rag/embed 与 /api/rag/search 接口，支持外部工具、脚本与 Agent 直接调用。
 */
public class EmbeddedRestGateway {

    private HttpServer server;
    private ExecutorService executor;
    private int port = 18088;
    private boolean running = false;

    private EmbeddingEngine embeddingEngine;
    private VectorStorageDriver storageDriver;
    private String targetName;

    public synchronized void start(int port, EmbeddingEngine embeddingEngine,
                                   VectorStorageDriver storageDriver, String targetName) throws IOException {
        if (running) {
            stop();
        }
        this.port = port > 0 ? port : 18088;
        this.embeddingEngine = embeddingEngine;
        this.storageDriver = storageDriver;
        this.targetName = targetName;

        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", this.port), 0);
        this.server.createContext("/api/rag/embed", new EmbedHandler());
        this.server.createContext("/api/rag/search", new SearchHandler());
        this.executor = Executors.newFixedThreadPool(4);
        this.server.setExecutor(executor);
        this.server.start();
        this.running = true;
    }

    public synchronized void stop() {
        HttpServer serverToStop = server;
        ExecutorService executorToStop = executor;
        server = null;
        executor = null;
        running = false;
        if (serverToStop != null) {
            try { serverToStop.stop(0); } catch (RuntimeException ignored) { }
        }
        if (executorToStop != null) executorToStop.shutdownNow();
        embeddingEngine = null;
        storageDriver = null;
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    private class EmbedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }
            try {
                String body = readBody(exchange.getRequestBody());
                JSONObject json = JSON.parseObject(body);
                JSONArray textsArr = json.getJSONArray("texts");
                List<String> texts = new ArrayList<>();
                if (textsArr != null) {
                    for (int i = 0; i < textsArr.size(); i++) {
                        texts.add(textsArr.getString(i));
                    }
                }
                List<float[]> vectors = embeddingEngine.embedBatch(texts);
                sendJsonResponse(exchange, 200, Map.of("code", 200, "data", vectors));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("code", 500, "error", e.getMessage()));
            }
        }
    }

    private class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }
            try {
                String body = readBody(exchange.getRequestBody());
                JSONObject json = JSON.parseObject(body);
                String query = json.getString("query");
                Integer topKObj = json.getInteger("topK");
                int topK = topKObj != null ? topKObj : 5;
                Double minScoreObj = json.getDouble("minScore");
                double minScore = minScoreObj != null ? minScoreObj : 0.65;

                List<float[]> queryVecs = embeddingEngine.embedBatch(List.of(query));
                if (queryVecs.isEmpty()) {
                    sendJsonResponse(exchange, 400, Map.of("error", "Failed to embed query"));
                    return;
                }
                List<SearchResult> results = storageDriver.searchSimilar(targetName, queryVecs.get(0), topK, minScore);
                sendJsonResponse(exchange, 200, Map.of("code", 200, "data", results));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, Map.of("code", 500, "error", e.getMessage()));
            }
        }
    }

    private String readBody(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = JSON.toJSONString(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
