package com.opencgl.checksum.service;

import com.opencgl.checksum.i18n.I18N;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 文件哈希计算服务
 */
public class ChecksumService {
    private static final Logger logger = LoggerFactory.getLogger(ChecksumService.class);
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-file-checksum");
        thread.setDaemon(true);
        return thread;
    });

    public enum HashAlgorithm {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256");

        private final String algorithm;

        HashAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public String getAlgorithm() {
            return algorithm;
        }
    }

    /**
     * 计算文件哈希值
     */
    public CompletableFuture<FileHashResult> calculateHash(File file, HashAlgorithm algorithm) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MessageDigest digest = MessageDigest.getInstance(algorithm.getAlgorithm());
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        digest.update(buffer, 0, bytesRead);
                    }
                }

                byte[] hashBytes = digest.digest();
                String hash = bytesToHex(hashBytes);

                return new FileHashResult(
                        file.getName(),
                        file.getAbsolutePath(),
                        file.length(),
                        algorithm.name(),
                        hash,
                        null);
            } catch (Exception e) {
                logger.error(I18N.get("msg.calc_error", file.getName()), e);
                return new FileHashResult(
                        file.getName(),
                        file.getAbsolutePath(),
                        file.length(),
                        algorithm.name(),
                        "",
                        e.getMessage());
            }
        }, executor);
    }

    /**
     * 批量计算文件哈希
     */
    public void calculateBatch(File[] files, HashAlgorithm algorithm,
            Consumer<FileHashResult> resultCallback,
            Runnable onComplete) {
        CompletableFuture[] futures = new CompletableFuture[files.length];

        for (int i = 0; i < files.length; i++) {
            futures[i] = calculateHash(files[i], algorithm)
                    .thenAccept(resultCallback);
        }

        CompletableFuture.allOf(futures).thenRun(onComplete);
    }

    /**
     * 比较两个文件的哈希值
     */
    public boolean compareHashes(String hash1, String hash2) {
        if (hash1 == null || hash2 == null)
            return false;
        return hash1.equalsIgnoreCase(hash2);
    }

    /**
     * 验证文件哈希值
     */
    public CompletableFuture<Boolean> verifyHash(File file, HashAlgorithm algorithm, String expectedHash) {
        return calculateHash(file, algorithm)
                .thenApply(result -> compareHashes(result.hash(), expectedHash));
    }

    public CompletableFuture<Void> runAsync(Runnable action) {
        return CompletableFuture.runAsync(action, executor);
    }

    public void dispose() {
        executor.shutdownNow();
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public record FileHashResult(String fileName, String filePath, long fileSize,
            String algorithm, String hash, String error) {
        public boolean hasError() {
            return error != null && !error.isEmpty();
        }

        public String getFormattedSize() {
            return formatFileSize(fileSize);
        }
    }
}
