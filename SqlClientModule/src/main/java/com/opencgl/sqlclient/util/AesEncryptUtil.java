package com.opencgl.sqlclient.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES加密工具类
 * 用于数据库密码的加密和解密
 * 
 * @author Chance.W
 */
public class AesEncryptUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(AesEncryptUtil.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    // 密钥（实际应用中应该从配置文件或环境变量读取）
    private static final String SECRET_KEY = "OpenCGL2024SecretKeyForEncryption";
    
    /**
     * 加密
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        try {
            SecretKeySpec keySpec = new SecretKeySpec(getKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
            
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            return plainText; // 加密失败时返回原文
        }
    }
    
    /**
     * 解密
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            SecretKeySpec keySpec = new SecretKeySpec(getKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            return encryptedText; // 解密失败时返回原文
        }
    }
    
    /**
     * 获取密钥字节数组
     */
    private static byte[] getKey() {
        // 使用固定长度的密钥（32字节 = 256位）
        byte[] key = new byte[32];
        byte[] secretBytes = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        
        System.arraycopy(secretBytes, 0, key, 0, Math.min(secretBytes.length, key.length));
        return key;
    }
}
