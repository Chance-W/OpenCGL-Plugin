package com.opencgl.aiqa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 文档库服务 - RAG (检索增强生成) 版本
 * 使用 Embedding 向量化文档，按语义相似度检索
 */
public class DocumentLibraryService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DocumentLibraryService.class);
    
    // 支持的文件扩展名
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".txt", ".md", ".java", ".json", ".xml", ".properties", 
        ".yaml", ".yml", ".html", ".css", ".js", ".ts", ".py",
        ".sql", ".sh", ".bat", ".csv", ".log"
    );
    
    // 最大单文件大小 (1MB)
    private static final long MAX_FILE_SIZE = 1024 * 1024;  // 1MB
    
    // 文档分块大小 (500 字符)
    private static final int CHUNK_SIZE = 500;
    
    // 分块重叠 (50 字符)
    private static final int CHUNK_OVERLAP = 50;
    
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    
    private String documentPath;
    private List<DocumentInfo> loadedDocuments = new ArrayList<>();
    private int chunkCount = 0;
    private boolean indexed = false;
    private final ExecutorService indexingExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-document-indexer");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> indexingFuture;
    private volatile boolean closed;
    
    public DocumentLibraryService() {
        this.embeddingService = new EmbeddingService();
        this.vectorStore = new VectorStore();
    }
    
    /**
     * 配置 Embedding 服务
     */
    public void configureEmbedding(String baseUrl, String apiKey) {
        embeddingService.configure(baseUrl, apiKey, null);
    }
    
    /**
     * 加载并索引文档 (同步)
     */
    public int loadDocuments(String path) {
        this.documentPath = path;
        this.loadedDocuments.clear();
        this.vectorStore.clear();
        this.chunkCount = 0;
        this.indexed = false;
        
        if (path == null || path.isEmpty()) {
            return 0;
        }
        
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            logger.warn("路径不存在或不是目录: {}", path);
            return 0;
        }
        
        List<ChunkInfo> chunks = new ArrayList<>();
        
        try {
            logger.info("开始扫描目录: {}", dirPath);
            try (var paths = Files.walk(dirPath, 3)) {
                paths
                .filter(Files::isRegularFile)
                .peek(p -> logger.debug("发现文件: {}", p.getFileName()))
                .filter(this::isSupportedFile)
                .peek(p -> logger.info("支持的文件: {}", p.getFileName()))
                .filter(p -> {
                    try {
                        long size = Files.size(p);
                        boolean ok = size <= MAX_FILE_SIZE;
                        if (!ok) logger.warn("文件过大跳过: {} ({}KB)", p.getFileName(), size/1024);
                        return ok;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(file -> {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        String relativePath = dirPath.relativize(file).toString();
                        
                        loadedDocuments.add(new DocumentInfo(relativePath, content.length()));
                        
                        // 分块
                        List<String> fileChunks = splitIntoChunks(content);
                        for (String chunk : fileChunks) {
                            chunks.add(new ChunkInfo(relativePath, chunk));
                        }
                        
                    } catch (IOException e) {
                        logger.warn("读取文件失败: {}", file, e);
                    }
                });
            }
        } catch (IOException e) {
            logger.error("遍历目录失败: {}", path, e);
        }
        
        this.chunkCount = chunks.size();
        logger.info("共加载 {} 个文档，{} 个分块", loadedDocuments.size(), chunkCount);
        
        // 异步索引向量
        indexChunksAsync(chunks);
        
        return loadedDocuments.size();
    }
    
    /**
     * 将文本分成小块
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            start = end - CHUNK_OVERLAP;
            if (start < 0) start = 0;
            if (end == text.length()) break;
        }
        
        return chunks;
    }
    
    /**
     * 异步索引分块
     */
    private void indexChunksAsync(List<ChunkInfo> chunks) {
        if (indexingFuture != null) indexingFuture.cancel(true);
        indexingFuture = indexingExecutor.submit(() -> {
            int indexed = 0;
            for (ChunkInfo chunk : chunks) {
                if (closed || Thread.currentThread().isInterrupted()) return;
                try {
                    double[] vector = embeddingService.embed(chunk.content);
                    vectorStore.add(chunk.source, chunk.content, vector);
                    indexed++;
                    
                    // 每 10 个打印进度
                    if (indexed % 10 == 0) {
                        logger.info("索引进度: {}/{}", indexed, chunks.size());
                    }
                } catch (IOException e) {
                    logger.warn("向量化失败: {}", e.getMessage());
                }
            }
            this.indexed = true;
            logger.info("索引完成: {} 个分块", vectorStore.size());
        });
    }
    
    /**
     * 搜索相关文档片段
     */
    public String searchRelevant(String query, int topK) {
        if (!indexed || vectorStore.size() == 0) {
            return "";
        }
        
        try {
            double[] queryVector = embeddingService.embed(query);
            List<VectorStore.SearchResult> results = vectorStore.search(queryVector, topK);
            
            if (results.isEmpty()) {
                return "";
            }
            
            StringBuilder sb = new StringBuilder();
            for (VectorStore.SearchResult r : results) {
                sb.append("\n--- ").append(r.source).append(" (相关度: ")
                  .append(String.format("%.2f", r.score)).append(") ---\n");
                sb.append(r.content).append("\n");
            }
            
            return sb.toString();
        } catch (IOException e) {
            logger.error("搜索失败: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * 检查是否有索引的文档
     */
    public boolean hasDocuments() {
        return indexed && vectorStore.size() > 0;
    }
    
    /**
     * 获取加载的文档数量
     */
    public int getDocumentCount() {
        return loadedDocuments.size();
    }
    
    /**
     * 获取分块数量
     */
    public int getChunkCount() {
        return chunkCount;
    }
    
    /**
     * 是否索引完成
     */
    public boolean isIndexed() {
        return indexed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (indexingFuture != null) indexingFuture.cancel(true);
        indexingExecutor.shutdownNow();
        embeddingService.close();
        vectorStore.clear();
        loadedDocuments.clear();
    }
    
    private boolean isSupportedFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    public static class DocumentInfo {
        private final String path;
        private final int size;
        
        public DocumentInfo(String path, int size) {
            this.path = path;
            this.size = size;
        }
        
        public String getPath() { return path; }
        public int getSize() { return size; }
    }
    
    private static class ChunkInfo {
        final String source;
        final String content;
        
        ChunkInfo(String source, String content) {
            this.source = source;
            this.content = content;
        }
    }
}
