package com.opencgl.plugin.ragingestion.storage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.opencgl.plugin.ragingestion.model.DocumentChunk;
import com.opencgl.plugin.ragingestion.model.SearchResult;

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
 * Elasticsearch 7.x/8.x 向量存储驱动。
 * 自动检测和建立含 dense_vector 类型字段的 Index Mapping，并支持原生 KNN / script_score 向量检索。
 */
public class ElasticsearchVectorDriver implements VectorStorageDriver {

    private final String esBaseUrl;
    private final String apiKeyOrAuthHeader;
    private final HttpClient httpClient;

    public ElasticsearchVectorDriver(String esBaseUrl, String apiKeyOrAuthHeader) {
        this.esBaseUrl = esBaseUrl.endsWith("/") ? esBaseUrl.substring(0, esBaseUrl.length() - 1) : esBaseUrl;
        this.apiKeyOrAuthHeader = apiKeyOrAuthHeader != null ? apiKeyOrAuthHeader : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public void ensureTableOrIndexExists(String targetName, int dimension) throws Exception {
        String url = esBaseUrl + "/" + targetName;
        HttpRequest checkReq = buildReq(url).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> checkResp = httpClient.send(checkReq, HttpResponse.BodyHandlers.ofString());

        if (checkResp.statusCode() == 200) {
            return;
        }

        String mappingJson = "{"
                + "\"mappings\": {"
                + "  \"properties\": {"
                + "    \"chunk_id\": {\"type\": \"keyword\"},"
                + "    \"file_id\": {\"type\": \"keyword\"},"
                + "    \"file_name\": {\"type\": \"keyword\"},"
                + "    \"chunk_index\": {\"type\": \"integer\"},"
                + "    \"content\": {\"type\": \"text\"},"
                + "    \"content_hash\": {\"type\": \"keyword\"},"
                + "    \"char_length\": {\"type\": \"integer\"},"
                + "    \"embedding\": {"
                + "      \"type\": \"dense_vector\","
                + "      \"dims\": " + dimension + ","
                + "      \"index\": true,"
                + "      \"similarity\": \"cosine\""
                + "    }"
                + "  }"
                + "}}";

        HttpRequest createReq = buildReq(url)
                .PUT(HttpRequest.BodyPublishers.ofString(mappingJson))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("创建 ES Index 失败: " + resp.body());
        }
    }

    @Override
    public Map<String, String> fetchExistingHashes(String targetName, String fileId) throws Exception {
        Map<String, String> map = new HashMap<>();
        String url = esBaseUrl + "/" + targetName + "/_search?size=10000";
        String queryJson = "{\"_source\":[\"chunk_id\",\"content_hash\"],\"query\":{\"term\":{\"file_id\":\"" + fileId + "\"}}}";

        HttpRequest req = buildReq(url)
                .POST(HttpRequest.BodyPublishers.ofString(queryJson))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            JSONObject root = JSON.parseObject(resp.body());
            JSONArray hits = root.getJSONObject("hits").getJSONArray("hits");
            if (hits != null) {
                for (int i = 0; i < hits.size(); i++) {
                    JSONObject source = hits.getJSONObject(i).getJSONObject("_source");
                    if (source != null) {
                        map.put(source.getString("chunk_id"), source.getString("content_hash"));
                    }
                }
            }
        }
        return map;
    }

    @Override
    public int upsertChunks(String targetName, List<DocumentChunk> chunks) throws Exception {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }
        StringBuilder bulk = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            bulk.append("{\"index\":{\"_index\":\"").append(targetName).append("\",\"_id\":\"").append(chunk.getChunkId()).append("\"}}\n");
            bulk.append(JSON.toJSONString(chunk)).append("\n");
        }

        HttpRequest req = buildReq(esBaseUrl + "/_bulk")
                .POST(HttpRequest.BodyPublishers.ofString(bulk.toString()))
                .header("Content-Type", "application/x-ndjson")
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("批量写 ES 失败: " + resp.body());
        }
        return chunks.size();
    }

    @Override
    public int deleteChunksByIds(String targetName, List<String> obsoleteChunkIds) throws Exception {
        if (obsoleteChunkIds == null || obsoleteChunkIds.isEmpty()) {
            return 0;
        }
        for (String id : obsoleteChunkIds) {
            HttpRequest req = buildReq(esBaseUrl + "/" + targetName + "/_doc/" + id)
                    .DELETE()
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        }
        return obsoleteChunkIds.size();
    }

    @Override
    public List<SearchResult> searchSimilar(String targetName, float[] queryVector, int topK, double minScore) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        String url = esBaseUrl + "/" + targetName + "/_search";
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("size", topK);

        Map<String, Object> scriptScore = new HashMap<>();
        Map<String, Object> script = new HashMap<>();
        script.put("source", "cosineSimilarity(params.query_vector, 'embedding') + 1.0");
        Map<String, Object> params = new HashMap<>();
        params.put("query_vector", queryVector);
        script.put("params", params);
        scriptScore.put("script", script);
        scriptScore.put("query", Map.of("match_all", Map.of()));
        queryMap.put("query", Map.of("script_score", scriptScore));

        HttpRequest req = buildReq(url)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(queryMap)))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            JSONObject root = JSON.parseObject(resp.body());
            JSONArray hits = root.getJSONObject("hits").getJSONArray("hits");
            if (hits != null) {
                int rank = 1;
                for (int i = 0; i < hits.size(); i++) {
                    JSONObject hit = hits.getJSONObject(i);
                    double esScore = hit.getDoubleValue("_score");
                    double score = (esScore - 1.0); // 还原 cosine (-1 to 1)
                    if (score >= minScore) {
                        JSONObject source = hit.getJSONObject("_source");
                        SearchResult sr = new SearchResult(
                                rank++,
                                score,
                                source.getString("chunk_id"),
                                source.getString("file_name"),
                                source.getString("content")
                        );
                        results.add(sr);
                    }
                }
            }
        }
        return results;
    }

    private HttpRequest.Builder buildReq(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        if (!apiKeyOrAuthHeader.isEmpty()) {
            builder.header("Authorization", apiKeyOrAuthHeader);
        }
        return builder;
    }

    @Override
    public String getDriverName() {
        return "Elasticsearch (" + esBaseUrl + ")";
    }
}
