package com.opencgl.plugin.ragingestion.storage;

import com.opencgl.plugin.ragingestion.model.DocumentChunk;
import com.opencgl.plugin.ragingestion.model.SearchResult;

import java.util.List;
import java.util.Map;

/**
 * 向量存储驱动 SPI 接口，统一屏蔽底层不同数据库（SQLite / PostgreSQL / Elasticsearch 等）的差异。
 */
public interface VectorStorageDriver {

    /**
     * 1. 检测并确保目标表或索引存在，若不存在则自动生成建表 DDL 并执行
     */
    void ensureTableOrIndexExists(String targetName, int dimension) throws Exception;

    /**
     * 2. 查询特定文档现存的所有切片哈希列表，用于增量对账过滤
     *
     * @param targetName 表名/索引名
     * @param fileId     文档路径或标识
     * @return Map<chunkId, contentHash>
     */
    Map<String, String> fetchExistingHashes(String targetName, String fileId) throws Exception;

    /**
     * 3. 批量 Upsert 写入切片数据与对应向量
     */
    int upsertChunks(String targetName, List<DocumentChunk> chunks) throws Exception;

    /**
     * 4. 物理清理已过期的旧切片 ID 列表
     */
    int deleteChunksByIds(String targetName, List<String> obsoleteChunkIds) throws Exception;

    /**
     * 5. 在线查询相似切片 Top K
     */
    List<SearchResult> searchSimilar(String targetName, float[] queryVector, int topK, double minScore) throws Exception;

    /**
     * 6. 查询已存入库的历史记录列表（支持关键词过滤），用于在线回写和 CRUD 管理
     */
    default List<DocumentChunk> listStoredChunks(String targetName, String keywordFilter, int maxCount) throws Exception {
        return List.of();
    }

    /**
     * 7. 单条更新已在库中的切片纪录及向量
     */
    default boolean updateChunk(String targetName, DocumentChunk updatedChunk) throws Exception {
        return upsertChunks(targetName, List.of(updatedChunk)) > 0;
    }

    /**
     * 8. 单条删除已在库中的切片纪录
     */
    default boolean deleteChunkById(String targetName, String chunkId) throws Exception {
        return deleteChunksByIds(targetName, List.of(chunkId)) > 0;
    }

    /**
     * 驱动名称
     */
    String getDriverName();
}
