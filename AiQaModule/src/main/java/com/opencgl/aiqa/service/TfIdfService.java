package com.opencgl.aiqa.service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TF-IDF 文本相似度服务 - 本地检索，无需外部 API
 */
public class TfIdfService {
    
    private List<DocumentChunk> chunks = new ArrayList<>();
    private Map<String, Double> idfScores = new HashMap<>();
    private int totalDocs = 0;
    
    /**
     * 索引文档块
     */
    public void indexChunks(List<DocumentChunk> documentChunks) {
        this.chunks = new ArrayList<>(documentChunks);
        this.totalDocs = chunks.size();
        
        // 计算 IDF
        Map<String, Integer> docFrequency = new HashMap<>();
        for (DocumentChunk chunk : chunks) {
            Set<String> terms = tokenize(chunk.content);
            for (String term : terms) {
                docFrequency.merge(term, 1, Integer::sum);
            }
        }
        
        // IDF = log(N / df)
        idfScores.clear();
        for (Map.Entry<String, Integer> entry : docFrequency.entrySet()) {
            double idf = Math.log((double) totalDocs / entry.getValue());
            idfScores.put(entry.getKey(), idf);
        }
    }
    
    /**
     * 搜索相关文档块
     */
    public List<DocumentChunk> search(String query, int topK) {
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> queryTerms = tokenize(query);
        double[] queryVector = computeTfIdfVector(queryTerms);
        
        // 计算相似度
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Set<String> chunkTerms = tokenize(chunk.content);
            double[] chunkVector = computeTfIdfVector(chunkTerms);
            double similarity = cosineSimilarity(queryVector, chunkVector);
            scoredChunks.add(new ScoredChunk(chunk, similarity));
        }
        
        // 排序取 TopK
        return scoredChunks.stream()
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topK)
            .filter(s -> s.score > 0.01) // 过滤掉无关内容
            .map(s -> s.chunk)
            .collect(Collectors.toList());
    }
    
    /**
     * 分词 (简单按空格和标点分割)
     */
    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase()
                .replaceAll("[\\p{Punct}]", " ")
                .split("\\s+"))
            .filter(s -> s.length() > 1)
            .collect(Collectors.toSet());
    }
    
    /**
     * 计算 TF-IDF 向量
     */
    private double[] computeTfIdfVector(Set<String> terms) {
        List<String> allTerms = new ArrayList<>(idfScores.keySet());
        double[] vector = new double[allTerms.size()];
        
        Map<String, Long> termCounts = terms.stream()
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        
        for (int i = 0; i < allTerms.size(); i++) {
            String term = allTerms.get(i);
            if (termCounts.containsKey(term)) {
                double tf = termCounts.get(term);
                double idf = idfScores.getOrDefault(term, 0.0);
                vector[i] = tf * idf;
            }
        }
        return vector;
    }
    
    /**
     * 余弦相似度
     */
    private double cosineSimilarity(double[] a, double[] b) {
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * 文档块
     */
    public static class DocumentChunk {
        public final String source;
        public final String content;
        
        public DocumentChunk(String source, String content) {
            this.source = source;
            this.content = content;
        }
    }
    
    private static class ScoredChunk {
        final DocumentChunk chunk;
        final double score;
        
        ScoredChunk(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
