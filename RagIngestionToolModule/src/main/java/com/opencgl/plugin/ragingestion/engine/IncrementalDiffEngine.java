package com.opencgl.plugin.ragingestion.engine;

import com.opencgl.plugin.ragingestion.model.DocumentChunk;
import com.opencgl.plugin.ragingestion.storage.VectorStorageDriver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 增量对齐引擎。
 * 比较新切分出来的 Chunk 清单与库中已有的 (chunkId, contentHash) 清单，
 * 将其准确划分为：UNCHANGED（免算跳过）、NEW/MODIFIED（需计算并写入）和 OBSOLETE（需物理清理）。
 */
public class IncrementalDiffEngine {

    public static class DiffResult {
        private final List<DocumentChunk> pendingChunks = new ArrayList<>();
        private final List<DocumentChunk> unchangedChunks = new ArrayList<>();
        private final List<String> obsoleteChunkIds = new ArrayList<>();

        public List<DocumentChunk> getPendingChunks() {
            return pendingChunks;
        }

        public List<DocumentChunk> getUnchangedChunks() {
            return unchangedChunks;
        }

        public List<String> getObsoleteChunkIds() {
            return obsoleteChunkIds;
        }
    }

    public static DiffResult compare(List<DocumentChunk> newChunks, String fileId,
                                     String targetName, VectorStorageDriver driver) throws Exception {
        DiffResult result = new DiffResult();
        Map<String, String> existingHashes = driver.fetchExistingHashes(targetName, fileId);

        Set<String> newIds = new HashSet<>();
        for (DocumentChunk chunk : newChunks) {
            newIds.add(chunk.getChunkId());
            String existingHash = existingHashes.get(chunk.getChunkId());
            if (existingHash != null && existingHash.equals(chunk.getContentHash())) {
                chunk.setDiffStatus("UNCHANGED");
                chunk.setProcessStatus("SKIPPED");
                result.getUnchangedChunks().add(chunk);
            } else {
                chunk.setDiffStatus(existingHash == null ? "NEW" : "MODIFIED");
                chunk.setProcessStatus("PENDING");
                result.getPendingChunks().add(chunk);
            }
        }

        for (String oldId : existingHashes.keySet()) {
            if (!newIds.contains(oldId)) {
                result.getObsoleteChunkIds().add(oldId);
            }
        }

        return result;
    }
}
