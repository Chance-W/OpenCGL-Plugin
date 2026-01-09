package com.opencgl.jwt.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JWT 服务
 * 支持 JWT 解码、生成、验证
 *
 * @author OpenCGL
 */
public class JWTService {

    /**
     * 解码 JWT（不验证签名）
     */
    public JWTDecodeResult decode(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token 不能为空");
        }

        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("无效的 JWT 格式");
        }

        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String signature = parts.length > 2 ? parts[2] : "";

            return new JWTDecodeResult(headerJson, payloadJson, signature, null);
        } catch (Exception e) {
            throw new IllegalArgumentException("JWT 解码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证 JWT 签名
     */
    public JWTDecodeResult verifyAndDecode(String token, String secret, String algorithm) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            
            Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);

            Claims claims = jws.getPayload();
            JwsHeader header = jws.getHeader();

            String headerJson = formatJson(header);
            String payloadJson = formatJson(claims);

            // 检查过期
            Date expiration = claims.getExpiration();
            String status = null;
            if (expiration != null) {
                if (expiration.before(new Date())) {
                    status = "EXPIRED";
                } else {
                    status = "VALID";
                }
            } else {
                status = "VALID (无过期时间)";
            }

            return new JWTDecodeResult(headerJson, payloadJson, "", status);
        } catch (ExpiredJwtException e) {
            JWTDecodeResult result = decode(token);
            return new JWTDecodeResult(result.header(), result.payload(), result.signature(), "EXPIRED");
        } catch (JwtException e) {
            JWTDecodeResult result = decode(token);
            return new JWTDecodeResult(result.header(), result.payload(), result.signature(), "INVALID: " + e.getMessage());
        }
    }

    /**
     * 生成 JWT
     */
    public String generate(Map<String, Object> claims, String secret, String algorithm, long expireMinutes) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        JwtBuilder builder = Jwts.builder()
            .claims(claims)
            .issuedAt(new Date());

        if (expireMinutes > 0) {
            builder.expiration(new Date(System.currentTimeMillis() + expireMinutes * 60 * 1000));
        }

        return builder.signWith(key).compact();
    }

    /**
     * 格式化 JSON 输出
     */
    private String formatJson(Object obj) {
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{\n");
            Map<?, ?> map = (Map<?, ?>) obj;
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append("  \"").append(entry.getKey()).append("\": ");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else if (value instanceof Date) {
                    sb.append(((Date) value).getTime() / 1000);
                } else {
                    sb.append(value);
                }
                if (++i < map.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
        return obj.toString();
    }

    public record JWTDecodeResult(String header, String payload, String signature, String status) {}
}
