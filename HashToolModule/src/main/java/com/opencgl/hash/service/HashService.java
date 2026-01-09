package com.opencgl.hash.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

/**
 * 哈希计算服务
 * 支持多种哈希算法
 *
 * @author OpenCGL
 */
public class HashService {

    public enum Algorithm {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        SHA512("SHA-512"),
        CRC32("CRC32");

        private final String name;

        Algorithm(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 计算文本哈希
     */
    public String hashText(String text, Algorithm algorithm) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        if (algorithm == Algorithm.CRC32) {
            return calculateCRC32(text.getBytes());
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm.getName());
            byte[] digest = md.digest(text.getBytes());
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("算法不支持: " + algorithm.getName(), e);
        }
    }

    /**
     * 计算文件哈希
     */
    public String hashFile(String filePath, Algorithm algorithm) throws IOException {
        if (algorithm == Algorithm.CRC32) {
            return calculateFileCRC32(filePath);
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm.getName());
            try (InputStream is = new FileInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("算法不支持: " + algorithm.getName(), e);
        }
    }

    /**
     * 计算 CRC32
     */
    private String calculateCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return Long.toHexString(crc32.getValue()).toUpperCase();
    }

    /**
     * 计算文件 CRC32
     */
    private String calculateFileCRC32(String filePath) throws IOException {
        CRC32 crc32 = new CRC32();
        try (InputStream is = new FileInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                crc32.update(buffer, 0, read);
            }
        }
        return Long.toHexString(crc32.getValue()).toUpperCase();
    }

    /**
     * 字节数组转16进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString().toUpperCase();
    }

    /**
     * 验证哈希值是否匹配
     */
    public boolean verify(String text, String expectedHash, Algorithm algorithm) {
        String actualHash = hashText(text, algorithm);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * 验证文件哈希值
     */
    public boolean verifyFile(String filePath, String expectedHash, Algorithm algorithm) throws IOException {
        String actualHash = hashFile(filePath, algorithm);
        return actualHash.equalsIgnoreCase(expectedHash);
    }
}
