package com.opencgl.aiqa.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Embedding 服务 - 调用 OpenAI 兼容 API 生成文本向量
 */
public class EmbeddingService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);
    
    private String baseUrl = "http://localhost:8181";
    private String apiKey = "";
    private String model = "text-embedding-ada-002";
    
    private final OkHttpClient client;
    
    public EmbeddingService() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }
    
    public void configure(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        if (apiKey != null) this.apiKey = apiKey;
        if (model != null && !model.isEmpty()) this.model = model;
    }
    
    /**
     * 获取文本的 Embedding 向量
     */
    public double[] embed(String text) throws IOException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", text);
        body.put("encoding_format", "float");  // 兼容更多服务
        
        Request.Builder requestBuilder = new Request.Builder()
            .url(baseUrl + "/v1/embeddings")
            .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
            .header("Content-Type", "application/json");
        
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }
        
        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Embedding 请求失败: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body().string();
            JSONObject json = JSON.parseObject(responseBody);
            JSONArray data = json.getJSONArray("data");
            
            if (data != null && !data.isEmpty()) {
                JSONArray embedding = data.getJSONObject(0).getJSONArray("embedding");
                double[] vector = new double[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = embedding.getDoubleValue(i);
                }
                return vector;
            }
            
            throw new IOException("无效的 Embedding 响应");
        }
    }
    
    /**
     * 计算两个向量的余弦相似度
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) return 0;
        
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    public void close() {
        cleanup("active calls", () -> client.dispatcher().cancelAll());
        cleanup("HTTP dispatcher", () -> client.dispatcher().executorService().shutdown());
        cleanup("HTTP connection pool", () -> client.connectionPool().evictAll());
        cleanup("HTTP cache", this::closeCache);
    }

    private void closeCache() {
        if (client.cache() == null) return;
        try {
            client.cache().close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cleanup(String resource, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            logger.warn("Failed to close embedding {}", resource, e);
        }
    }
}
