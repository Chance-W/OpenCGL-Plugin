package com.opencgl.plugin.ragingestion.storage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.opencgl.plugin.ragingestion.model.DocumentChunk;
import com.opencgl.plugin.ragingestion.model.SearchResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * SQLite 本地零依赖向量存储驱动。
 * 兼容纯本地文件存储 (.db)，自动建表，并将向量持久化为 JSON Float 列表，
 * 同时在内存结合归一化余弦相似度快速给万级以下切片提供 Top K 检索。
 */
public class SqliteVectorDriver implements VectorStorageDriver {

    private final String dbPath;

    public SqliteVectorDriver(String dbPath) {
        this.dbPath = dbPath;
    }

    private Connection getConnection() throws Exception {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    @Override
    public void ensureTableOrIndexExists(String targetName, int dimension) throws Exception {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS " + targetName + " ("
                    + "chunk_id TEXT PRIMARY KEY, "
                    + "file_id TEXT, "
                    + "file_name TEXT, "
                    + "chunk_index INTEGER, "
                    + "content TEXT, "
                    + "content_hash TEXT, "
                    + "char_length INTEGER, "
                    + "embedding_json TEXT"
                    + ")";
            stmt.execute(sql);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + targetName + "_file_id ON " + targetName + "(file_id)");
        }
    }

    @Override
    public Map<String, String> fetchExistingHashes(String targetName, String fileId) throws Exception {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT chunk_id, content_hash FROM " + targetName + " WHERE file_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("chunk_id"), rs.getString("content_hash"));
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
        String sql = "INSERT OR REPLACE INTO " + targetName + " "
                + "(chunk_id, file_id, file_name, chunk_index, content, content_hash, char_length, embedding_json) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (DocumentChunk chunk : chunks) {
                    ps.setString(1, chunk.getChunkId());
                    ps.setString(2, chunk.getFileId());
                    ps.setString(3, chunk.getFileName());
                    ps.setInt(4, chunk.getChunkIndex());
                    ps.setString(5, chunk.getContent());
                    ps.setString(6, chunk.getContentHash());
                    ps.setInt(7, chunk.getCharLength());
                    ps.setString(8, JSON.toJSONString(chunk.getEmbedding()));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }
        }
        return chunks.size();
    }

    @Override
    public int deleteChunksByIds(String targetName, List<String> obsoleteChunkIds) throws Exception {
        if (obsoleteChunkIds == null || obsoleteChunkIds.isEmpty()) {
            return 0;
        }
        String sql = "DELETE FROM " + targetName + " WHERE chunk_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String id : obsoleteChunkIds) {
                ps.setString(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return obsoleteChunkIds.size();
    }

    @Override
    public List<SearchResult> searchSimilar(String targetName, float[] queryVector, int topK, double minScore) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        if (queryVector == null || queryVector.length == 0) {
            return results;
        }

        String sql = "SELECT chunk_id, file_name, content, embedding_json FROM " + targetName;
        PriorityQueue<SearchResult> topQueue = new PriorityQueue<>((a, b) -> Double.compare(a.getScore(), b.getScore()));

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String chunkId = rs.getString("chunk_id");
                String fileName = rs.getString("file_name");
                String content = rs.getString("content");
                String embJson = rs.getString("embedding_json");
                if (embJson == null || embJson.isEmpty()) {
                    continue;
                }
                JSONArray arr = JSON.parseArray(embJson);
                float[] targetVec = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    targetVec[i] = arr.getFloatValue(i);
                }

                double score = cosineSimilarity(queryVector, targetVec);
                if (score >= minScore) {
                    SearchResult sr = new SearchResult(0, score, chunkId, fileName, content);
                    topQueue.offer(sr);
                    if (topQueue.size() > topK) {
                        topQueue.poll();
                    }
                }
            }
        }

        List<SearchResult> sorted = new ArrayList<>();
        while (!topQueue.isEmpty()) {
            sorted.add(0, topQueue.poll());
        }
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setRank(i + 1);
        }
        return sorted;
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        int len = Math.min(v1.length, v2.length);
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        for (int i = 0; i < len; i++) {
            dot += v1[i] * v2[i];
            n1 += v1[i] * v1[i];
            n2 += v2[i] * v2[i];
        }
        if (n1 <= 1e-9 || n2 <= 1e-9) {
            return 0.0;
        }
        return dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }

    @Override
    public List<DocumentChunk> listStoredChunks(String targetName, String keywordFilter, int maxCount) throws Exception {
        List<DocumentChunk> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT chunk_id, file_id, file_name, chunk_index, content, content_hash FROM ")
                .append(targetName);
        if (keywordFilter != null && !keywordFilter.trim().isEmpty()) {
            sql.append(" WHERE content LIKE ?");
        }
        sql.append(" ORDER BY file_id, chunk_index LIMIT ").append(Math.max(1, maxCount));

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (keywordFilter != null && !keywordFilter.trim().isEmpty()) {
                ps.setString(1, "%" + keywordFilter.trim() + "%");
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cContent = rs.getString("content");
                    DocumentChunk chunk = new DocumentChunk(
                            rs.getString("chunk_id"),
                            rs.getString("file_name"),
                            "已在库记录",
                            rs.getInt("chunk_index"),
                            cContent,
                            rs.getString("content_hash")
                    );
                    if (cContent != null && cContent.startsWith("Q: ") && cContent.contains("\nA: ")) {
                        int idx = cContent.indexOf("\nA: ");
                        chunk.setQaQuestion(cContent.substring(3, idx));
                        chunk.setQaAnswer(cContent.substring(idx + 4));
                    } else {
                        chunk.setQaQuestion("");
                        chunk.setQaAnswer(cContent != null ? cContent : "");
                    }
                    result.add(chunk);
                }
            }
        }
        return result;
    }

    @Override
    public String getDriverName() {
        return "SQLite 本地离线驱动 (" + dbPath + ")";
    }
}
