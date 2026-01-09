package com.opencgl.plugin.ragingestion.storage;

import com.alibaba.fastjson2.JSON;
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

/**
 * PostgreSQL PGVector 存储驱动。
 * 支持 vector 类型创建、增量更新及原生的余弦相似度计算操作符 <=>。
 */
public class PgVectorDriver implements VectorStorageDriver {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PgVectorDriver(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    private Connection getConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    @Override
    public void ensureTableOrIndexExists(String targetName, int dimension) throws Exception {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            String sql = "CREATE TABLE IF NOT EXISTS " + targetName + " ("
                    + "chunk_id VARCHAR(128) PRIMARY KEY, "
                    + "file_id VARCHAR(512), "
                    + "file_name VARCHAR(256), "
                    + "chunk_index INT, "
                    + "content TEXT, "
                    + "content_hash VARCHAR(128), "
                    + "char_length INT, "
                    + "embedding vector(" + dimension + ")"
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
        String sql = "INSERT INTO " + targetName + " "
                + "(chunk_id, file_id, file_name, chunk_index, content, content_hash, char_length, embedding) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector) "
                + "ON CONFLICT (chunk_id) DO UPDATE SET "
                + "content = EXCLUDED.content, content_hash = EXCLUDED.content_hash, "
                + "char_length = EXCLUDED.char_length, embedding = EXCLUDED.embedding";

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
        String vecJson = JSON.toJSONString(queryVector);
        String sql = "SELECT chunk_id, file_name, content, 1 - (embedding <=> ?::vector) AS score "
                + "FROM " + targetName + " "
                + "WHERE 1 - (embedding <=> ?::vector) >= ? "
                + "ORDER BY score DESC LIMIT ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vecJson);
            ps.setString(2, vecJson);
            ps.setDouble(3, minScore);
            ps.setInt(4, topK);

            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    SearchResult sr = new SearchResult(
                            rank++,
                            rs.getDouble("score"),
                            rs.getString("chunk_id"),
                            rs.getString("file_name"),
                            rs.getString("content")
                    );
                    results.add(sr);
                }
            }
        }
        return results;
    }

    @Override
    public String getDriverName() {
        return "PostgreSQL (PGVector)";
    }
}
