package com.opencgl.rsa.service;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.*;
import java.security.spec.RSAKeyGenParameterSpec;
import javax.crypto.Cipher;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * RSA密钥服务类
 * 负责生成、保存RSA密钥对
 * 
 * @author Chance.W
 * @date 2025-12-31
 */
public class RsaKeyService {

    private static final Logger logger = LoggerFactory.getLogger(RsaKeyService.class);
    
    static {
        // 添加BouncyCastle提供者
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * 生成RSA密钥对
     * 
     * @param keySize 密钥长度（位）
     * @param publicExponent 公钥指数（默认65537）
     * @return 包含公钥和私钥PEM字符串的Map
     */
    public Map<String, String> generateKeyPair(int keySize, int publicExponent) throws Exception {
        logger.info("开始生成RSA密钥对，密钥长度：{} 位", keySize);
        
        // 创建密钥对生成器
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        
        // 设置密钥生成参数
        RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(
            keySize, 
            java.math.BigInteger.valueOf(publicExponent)
        );
        keyPairGenerator.initialize(spec, new SecureRandom());
        
        // 生成密钥对
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        
        // 转换为PEM格式
        String publicKeyPem = convertPublicKeyToPEM(publicKey);
        String privateKeyPem = convertPrivateKeyToPEM(privateKey);
        
        Map<String, String> result = new HashMap<>();
        result.put("publicKey", publicKeyPem);
        result.put("privateKey", privateKeyPem);
        
        logger.info("RSA密钥对生成成功");
        return result;
    }

    /**
     * 将公钥转换为PEM格式字符串
     */
    private String convertPublicKeyToPEM(PublicKey publicKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(publicKey);
        }
        return stringWriter.toString();
    }

    /**
     * 将私钥转换为PEM格式字符串
     */
    private String convertPrivateKeyToPEM(PrivateKey privateKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(privateKey);
        }
        return stringWriter.toString();
    }

    /**
     * 保存密钥到文件
     * 
     * @param keyContent PEM格式的密钥内容
     * @param filePath 保存路径
     */
    public void saveKeyToFile(String keyContent, String filePath) throws IOException {
        logger.info("保存密钥到文件：{}", filePath);
        try (FileWriter fileWriter = new FileWriter(filePath);
             BufferedWriter writer = new BufferedWriter(fileWriter)) {
            writer.write(keyContent);
        }
        logger.info("密钥保存成功");
    }

    /**
     * 从PEM字符串加载公钥（支持PEM格式或纯Base64）
     */
    public PublicKey loadPublicKeyFromPEM(String pemString) throws Exception {
        pemString = normalizePemString(pemString, "PUBLIC");
        
        try (StringReader stringReader = new StringReader(pemString);
             PEMParser pemParser = new PEMParser(stringReader)) {
            
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            
            if (object instanceof SubjectPublicKeyInfo) {
                return converter.getPublicKey((SubjectPublicKeyInfo) object);
            }
            
            throw new IllegalArgumentException("无法解析公钥，请检查格式是否正确");
        }
    }

    /**
     * 从PEM字符串加载私钥（支持PEM格式或纯Base64）
     */
    public PrivateKey loadPrivateKeyFromPEM(String pemString) throws Exception {
        String input = pemString.trim();
        String base64Content;
        
        // 提取Base64内容
        if (input.contains("-----BEGIN")) {
            // 从PEM格式中提取Base64
            base64Content = input
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        } else {
            // 已经是纯Base64
            base64Content = input.replaceAll("\\s+", "");
        }
        
        // 解码Base64
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Content);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的Base64编码: " + e.getMessage());
        }
        
        // 尝试解析私钥
        return parsePrivateKeyBytes(keyBytes);
    }
    
    /**
     * 从字节数组解析私钥（支持PKCS#1和PKCS#8格式）
     */
    private PrivateKey parsePrivateKeyBytes(byte[] keyBytes) throws Exception {
        // 尝试1: PKCS#1格式（RSA PRIVATE KEY）
        try {
            org.bouncycastle.asn1.pkcs.RSAPrivateKey rsaPrivateKey = 
                org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(keyBytes);
            java.security.spec.RSAPrivateCrtKeySpec keySpec = new java.security.spec.RSAPrivateCrtKeySpec(
                rsaPrivateKey.getModulus(),
                rsaPrivateKey.getPublicExponent(),
                rsaPrivateKey.getPrivateExponent(),
                rsaPrivateKey.getPrime1(),
                rsaPrivateKey.getPrime2(),
                rsaPrivateKey.getExponent1(),
                rsaPrivateKey.getExponent2(),
                rsaPrivateKey.getCoefficient()
            );
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
            logger.debug("使用PKCS#1格式解析成功");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            logger.debug("PKCS#1格式解析失败: {}", e.getMessage());
        }
        
        // 尝试2: PKCS#8格式（PRIVATE KEY）
        try {
            java.security.spec.PKCS8EncodedKeySpec keySpec = 
                new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
            logger.debug("使用PKCS#8格式解析成功");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            logger.debug("PKCS#8格式解析失败: {}", e.getMessage());
        }
        
        throw new IllegalArgumentException("无法解析私钥。请确保私钥格式正确（PKCS#1或PKCS#8）。");
    }
    
    /**
     * 用PEM头尾包装Base64字符串
     */
    private String wrapBase64WithPemHeader(String base64, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        sb.append("-----END ").append(type).append("-----");
        return sb.toString();
    }
    
    /**
     * 规范化PEM字符串（支持纯Base64输入）
     */
    private String normalizePemString(String input, String keyType) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("密钥内容不能为空");
        }
        
        input = input.trim();
        
        // 如果已经是PEM格式，直接返回
        if (input.contains("-----BEGIN")) {
            return input;
        }
        
        // 纯Base64，添加PEM头尾（公钥用PUBLIC KEY格式）
        String base64 = input.replaceAll("\\s+", "");
        if ("PUBLIC".equals(keyType)) {
            return wrapBase64WithPemHeader(base64, "PUBLIC KEY");
        } else {
            // 私钥先尝试PKCS#8格式
            return wrapBase64WithPemHeader(base64, "PRIVATE KEY");
        }
    }

    /**
     * 使用公钥加密数据（RSA-OAEP）
     * 
     * @param plainText 明文
     * @param publicKeyPem 公钥PEM字符串
     * @return Base64编码的密文
     */
    public String encrypt(String plainText, String publicKeyPem) throws Exception {
        PublicKey publicKey = loadPublicKeyFromPEM(publicKeyPem);
        
        // 使用RSA/ECB/OAEPWithSHA-256AndMGF1Padding
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * 使用私钥解密数据（RSA-OAEP）
     * 
     * @param cipherText Base64编码的密文
     * @param privateKeyPem 私钥PEM字符串
     * @return 明文
     */
    public String decrypt(String cipherText, String privateKeyPem) throws Exception {
        PrivateKey privateKey = loadPrivateKeyFromPEM(privateKeyPem);
        
        // 使用RSA/ECB/OAEPWithSHA-256AndMGF1Padding
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "BC");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        
        byte[] cipherBytes = Base64.getDecoder().decode(cipherText);
        byte[] decryptedBytes = cipher.doFinal(cipherBytes);
        return new String(decryptedBytes, "UTF-8");
    }

    /**
     * 使用私钥对数据签名（SHA256withRSA）
     * 
     * @param data 要签名的数据
     * @param privateKeyPem 私钥PEM字符串
     * @return Base64编码的签名
     */
    public String sign(String data, String privateKeyPem) throws Exception {
        PrivateKey privateKey = loadPrivateKeyFromPEM(privateKeyPem);
        
        // 使用SHA256withRSA签名算法
        Signature signature = Signature.getInstance("SHA256withRSA", "BC");
        signature.initSign(privateKey);
        signature.update(data.getBytes("UTF-8"));
        
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    /**
     * 使用公钥验证签名（SHA256withRSA）
     * 
     * @param data 原始数据
     * @param signatureBase64 Base64编码的签名
     * @param publicKeyPem 公钥PEM字符串
     * @return 验证结果（true表示签名有效）
     */
    public boolean verify(String data, String signatureBase64, String publicKeyPem) throws Exception {
        PublicKey publicKey = loadPublicKeyFromPEM(publicKeyPem);
        
        // 使用SHA256withRSA验证算法
        Signature signature = Signature.getInstance("SHA256withRSA", "BC");
        signature.initVerify(publicKey);
        signature.update(data.getBytes("UTF-8"));
        
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        return signature.verify(signatureBytes);
    }
    
    /**
     * AES加密
     * 
     * @param plainText 明文
     * @param key 密钥（16/24/32字节）
     * @param iv IV向量（16字节，可为null则使用ECB模式）
     * @return Base64编码的密文
     */
    public String aesEncrypt(String plainText, String key, String iv) throws Exception {
        byte[] keyBytes = key.getBytes("UTF-8");
        
        // 检查密钥长度
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES密钥长度必须是16、24或32字节");
        }
        
        javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        javax.crypto.Cipher cipher;
        
        if (iv != null && !iv.trim().isEmpty()) {
            // CBC模式
            byte[] ivBytes = iv.getBytes("UTF-8");
            if (ivBytes.length != 16) {
                throw new IllegalArgumentException("IV向量长度必须是16字节");
            }
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(ivBytes);
            cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        } else {
            // ECB模式
            cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding", "BC");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
        }
        
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    /**
     * AES解密
     * 
     * @param cipherText Base64编码的密文
     * @param key 密钥（16/24/32字节）
     * @param iv IV向量（16字节，可为null则使用ECB模式）
     * @return 明文
     */
    public String aesDecrypt(String cipherText, String key, String iv) throws Exception {
        byte[] keyBytes = key.getBytes("UTF-8");
        
        // 检查密钥长度
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES密钥长度必须是16、24或32字节");
        }
        
        javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        javax.crypto.Cipher cipher;
        
        if (iv != null && !iv.trim().isEmpty()) {
            // CBC模式
            byte[] ivBytes = iv.getBytes("UTF-8");
            if (ivBytes.length != 16) {
                throw new IllegalArgumentException("IV向量长度必须是16字节");
            }
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(ivBytes);
            cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec);
        } else {
            // ECB模式
            cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding", "BC");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey);
        }
        
        byte[] cipherBytes = Base64.getDecoder().decode(cipherText);
        byte[] decryptedBytes = cipher.doFinal(cipherBytes);
        return new String(decryptedBytes, "UTF-8");
    }
}
