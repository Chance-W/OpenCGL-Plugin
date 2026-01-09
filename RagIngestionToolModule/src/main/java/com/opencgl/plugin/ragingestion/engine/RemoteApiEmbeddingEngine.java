package com.opencgl.plugin.ragingestion.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模式 A：远程 API 向量化引擎（兼容 OpenAI 标准 /v1/embeddings 接口，支持 Ollama、阿里云百炼、智谱等）。
 * 支持手工输入指定输出向量维度 dimensions。
 */
public class RemoteApiEmbeddingEngine implements EmbeddingEngine {

    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final int dimension;
    private final HttpClient httpClient;

    public RemoteApiEmbeddingEngine(String apiUrl, String apiKey, String modelName, int dimension) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey != null ? apiKey : "";
        this.modelName = modelName != null && !modelName.isEmpty() ? modelName : "text-embedding-3-small";
        this.dimension = dimension > 0 ? dimension : 768;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelName);
        payload.put("input", texts);
        if (dimension > 0) {
            payload.put("dimensions", dimension);
        }

        String jsonBody = JSON.toJSONString(payload);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");

        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("调用远程 Embedding API 失败 [HTTP " + response.statusCode() + "]: " + response.body());
        }

        JSONObject respJson = JSON.parseObject(response.body());
        JSONArray dataArray = respJson.getJSONArray("data");
        if (dataArray == null) {
            throw new RuntimeException("响应报文缺少 data 字段: " + response.body());
        }

        List<float[]> results = new ArrayList<>();
        for (int i = 0; i < dataArray.size(); i++) {
            JSONObject item = dataArray.getJSONObject(i);
            JSONArray vecArray = item.getJSONArray("embedding");
            float[] vec = new float[vecArray.size()];
            for (int j = 0; j < vecArray.size(); j++) {
                vec[j] = vecArray.getFloatValue(j);
            }
            results.add(vec);
        }
        return results;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getEngineName() {
        return "模式A-远程API(" + modelName + ")";
    }
}
