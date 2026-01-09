package com.opencgl.aiqa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量存储 - 内存存储文档向量，支持相似度搜索
 */
public class VectorStore {
    private static final Logger logger = LoggerFactory.getLogger(VectorStore.class);
    
    private final List<VectorEntry> entries = new ArrayList<>();
    
    /**
     * 添加向量
     */
    public void add(String source, String content, double[] vector) {
        entries.add(new VectorEntry(source, content, vector));
    }
    
    /**
     * 清空存储
     */
    public void clear() {
        entries.clear();
    }
    
    /**
     * 获取条目数量
     */
    public int size() {
        return entries.size();
    }
    
    /**
     * 搜索相似文档
     */
    public List<SearchResult> search(double[] queryVector, int topK) {
        if (entries.isEmpty() || queryVector == null) {
            return Collections.emptyList();
        }
        
        return entries.stream()
            .map(entry -> {
                double similarity = EmbeddingService.cosineSimilarity(queryVector, entry.vector);
                return new SearchResult(entry.source, entry.content, similarity);
            })
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topK)
            .filter(r -> r.score > 0.3) // 过滤低相关性
            .collect(Collectors.toList());
    }
    
    /**
     * 向量条目
     */
    private static class VectorEntry {
        final String source;
        final String content;
        final double[] vector;
        
        VectorEntry(String source, String content, double[] vector) {
            this.source = source;
            this.content = content;
            this.vector = vector;
        }
    }
    
    /**
     * 搜索结果
     */
    public static class SearchResult {
        public final String source;
        public final String content;
        public final double score;
        
        public SearchResult(String source, String content, double score) {
            this.source = source;
            this.content = content;
            this.score = score;
        }
    }
}
