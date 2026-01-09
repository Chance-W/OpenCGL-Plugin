package com.opencgl.plugin.ragingestion.storage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.opencgl.plugin.ragingestion.model.DocumentChunk;
import com.opencgl.plugin.ragingestion.model.SearchResult;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * FAISS 本地高速相似度计算与索引快照驱动。
 * 支持在 JVM 内存中进行超高效浮点归一化相似度计算，并将索引和元数据同步持久化到 .faissindex 文件。
 */
public class FaissLocalVectorDriver implements VectorStorageDriver {

    private final String faissIndexPath;
    private final Map<String, DocumentChunk> memoryStore = new LinkedHashMap<>();
    private final Map<String, float[]> memoryVectors = new LinkedHashMap<>();

    public FaissLocalVectorDriver(String faissIndexPath) {
        this.faissIndexPath = faissIndexPath;
        loadIndexFromFile();
    }

    private synchronized void loadIndexFromFile() {
        try {
            File f = new File(faissIndexPath);
            if (!f.exists()) {
                return;
            }
            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            if (content == null || content.trim().isEmpty()) {
                return;
            }
            JSONObject root = JSON.parseObject(content);
            JSONArray chunksArr = root.getJSONArray("chunks");
            if (chunksArr != null) {
                for (int i = 0; i < chunksArr.size(); i++) {
                    JSONObject obj = chunksArr.getJSONObject(i);
                    String id = obj.getString("chunkId");
                    DocumentChunk chunk = new DocumentChunk(
                            id,
                            obj.getString("fileName"),
                            obj.getString("ruleMatched"),
                            obj.getIntValue("chunkIndex"),
                            obj.getString("content"),
                            obj.getString("contentHash")
                    );
                    memoryStore.put(id, chunk);
                    JSONArray vecArr = obj.getJSONArray("embedding");
                    if (vecArr != null) {
                        float[] vec = new float[vecArr.size()];
                        for (int j = 0; j < vecArr.size(); j++) {
                            vec[j] = vecArr.getFloatValue(j);
                        }
                        memoryVectors.put(id, vec);
                        chunk.setEmbedding(vec);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[FaissLocalVectorDriver] 加载索引文件失败: " + e.getMessage());
        }
    }

    private synchronized void saveIndexToFile() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (DocumentChunk c : memoryStore.values()) {
                JSONObject obj = new JSONObject();
                obj.put("chunkId", c.getChunkId());
                obj.put("fileName", c.getFileName());
                obj.put("ruleMatched", c.getRuleMatched());
                obj.put("chunkIndex", c.getChunkIndex());
                obj.put("content", c.getContent());
                obj.put("contentHash", c.getContentHash());
                float[] vec = memoryVectors.get(c.getChunkId());
                if (vec != null) {
                    arr.add(obj);
                    JSONArray vecArr = new JSONArray();
                    for (float f : vec) {
                        vecArr.add(f);
                    }
                    obj.put("embedding", vecArr);
                }
            }
            root.put("chunks", arr);
            File f = new File(faissIndexPath);
            if (f.getParentFile() != null && !f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            Files.writeString(f.toPath(), JSON.toJSONString(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[FaissLocalVectorDriver] 保存索引文件失败: " + e.getMessage());
        }
    }

    @Override
    public void ensureTableOrIndexExists(String targetName, int dimension) throws Exception {
        // Local FAISS uses file persistence
    }

    @Override
    public Map<String, String> fetchExistingHashes(String targetName, String fileId) throws Exception {
        Map<String, String> hashes = new HashMap<>();
        for (DocumentChunk c : memoryStore.values()) {
            hashes.put(c.getChunkId(), c.getContentHash());
        }
        return hashes;
    }

    @Override
    public synchronized int upsertChunks(String targetName, List<DocumentChunk> chunks) throws Exception {
        int count = 0;
        for (DocumentChunk c : chunks) {
            memoryStore.put(c.getChunkId(), c);
            if (c.getEmbedding() != null) {
                memoryVectors.put(c.getChunkId(), c.getEmbedding());
            }
            count++;
        }
        saveIndexToFile();
        return count;
    }

    @Override
    public synchronized int deleteChunksByIds(String targetName, List<String> obsoleteChunkIds) throws Exception {
        int count = 0;
        for (String id : obsoleteChunkIds) {
            if (memoryStore.remove(id) != null) {
                memoryVectors.remove(id);
                count++;
            }
        }
        if (count > 0) {
            saveIndexToFile();
        }
        return count;
    }

    @Override
    public List<SearchResult> searchSimilar(String targetName, float[] queryVector, int topK, double minScore) throws Exception {
        PriorityQueue<SearchResult> queue = new PriorityQueue<>(Comparator.comparingDouble(SearchResult::getScore));
        for (Map.Entry<String, float[]> entry : memoryVectors.entrySet()) {
            double score = cosineSimilarity(queryVector, entry.getValue());
            if (score >= minScore) {
                DocumentChunk c = memoryStore.get(entry.getKey());
                if (c != null) {
                    queue.offer(new SearchResult(0, score, c.getChunkId(), c.getFileName(), c.getContent()));
                }
            }
        }
        List<SearchResult> list = new ArrayList<>();
        while (!queue.isEmpty()) {
            list.add(0, queue.poll());
        }
        if (list.size() > topK) {
            list = list.subList(0, topK);
        }
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        return list;
    }

    @Override
    public List<DocumentChunk> listStoredChunks(String targetName, String keywordFilter, int maxCount) throws Exception {
        List<DocumentChunk> result = new ArrayList<>();
        for (DocumentChunk c : memoryStore.values()) {
            if (keywordFilter == null || keywordFilter.trim().isEmpty() ||
                    (c.getContent() != null && c.getContent().contains(keywordFilter.trim()))) {
                result.add(c);
                if (result.size() >= maxCount) {
                    break;
                }
            }
        }
        return result;
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        int len = Math.min(v1.length, v2.length);
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        for (int i = 0; i < len; i++) {
            dot += v1[i] * v2[i];
            n1 += v1[i] * v1[i];
            n2 += v2[i] * v2[i];
        }
        if (n1 <= 1e-9 || n2 <= 1e-9) return 0.0;
        return dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }

    @Override
    public String getDriverName() {
        return "FAISS 极速本地相似度引擎 (" + faissIndexPath + ")";
    }
}
