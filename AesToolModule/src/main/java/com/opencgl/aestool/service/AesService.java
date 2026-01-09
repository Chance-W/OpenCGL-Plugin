package com.opencgl.aestool.service;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.util.Base64;

/**
 * AES加解密服务
 */
public class AesService {

    private static final Logger logger = LoggerFactory.getLogger(AesService.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * AES加密
     */
    public String encrypt(String plainText, String key, String iv) throws Exception {
        byte[] keyBytes = key.getBytes("UTF-8");

        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES密钥长度必须是16、24或32字节");
        }

        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher;

        if (iv != null && !iv.trim().isEmpty()) {
            byte[] ivBytes = iv.getBytes("UTF-8");
            if (ivBytes.length != 16) {
                throw new IllegalArgumentException("IV向量长度必须是16字节");
            }
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        } else {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        }

        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * AES解密
     */
    public String decrypt(String cipherText, String key, String iv) throws Exception {
        byte[] keyBytes = key.getBytes("UTF-8");

        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES密钥长度必须是16、24或32字节");
        }

        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher;

        if (iv != null && !iv.trim().isEmpty()) {
            byte[] ivBytes = iv.getBytes("UTF-8");
            if (ivBytes.length != 16) {
                throw new IllegalArgumentException("IV向量长度必须是16字节");
            }
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        } else {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding", "BC");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
        }

        byte[] cipherBytes = Base64.getDecoder().decode(cipherText);
        byte[] decryptedBytes = cipher.doFinal(cipherBytes);
        return new String(decryptedBytes, "UTF-8");
    }
}
