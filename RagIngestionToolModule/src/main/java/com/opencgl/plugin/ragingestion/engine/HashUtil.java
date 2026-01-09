package com.opencgl.plugin.ragingestion.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * SHA-256 哈希计算工具类，用于为切片和文本生成确定的指纹。
 */
public class HashUtil {

    public static String sha256(String text) {
        if (text == null) {
            text = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败: " + e.getMessage(), e);
        }
    }
}
