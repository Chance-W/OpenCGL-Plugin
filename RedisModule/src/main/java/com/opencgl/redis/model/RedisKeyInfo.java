package com.opencgl.redis.model;

import com.opencgl.redis.i18n.I18N;

/**
 * Redis Key 信息
 */
public class RedisKeyInfo {

    /**
     * Key 类型枚举
     */
    public enum KeyType {
        STRING, HASH, LIST, SET, ZSET, STREAM, NONE
    }

    private String key;
    private KeyType type;
    private long ttl; // -1 永不过期, -2 不存在
    private long size; // 元素数量或字符串长度

    public RedisKeyInfo() {
    }

    public RedisKeyInfo(String key, KeyType type, long ttl, long size) {
        this.key = key;
        this.type = type;
        this.ttl = ttl;
        this.size = size;
    }

    // Getters and Setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public KeyType getType() {
        return type;
    }

    public void setType(KeyType type) {
        this.type = type;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    /**
     * 获取类型显示名
     */
    public String getTypeDisplay() {
        return type != null ? type.name().toLowerCase() : "unknown";
    }

    /**
     * 获取 TTL 显示
     */
    public String getTtlDisplay() {
        if (ttl == -1) {
            return I18N.get("label.ttl.never_expire");
        } else if (ttl == -2) {
            return I18N.get("label.ttl.n_a");
        } else if (ttl < 60) {
            return ttl + "s";
        } else if (ttl < 3600) {
            return (ttl / 60) + "m " + (ttl % 60) + "s";
        } else if (ttl < 86400) {
            return (ttl / 3600) + "h " + ((ttl % 3600) / 60) + "m";
        } else {
            return (ttl / 86400) + "d " + ((ttl % 86400) / 3600) + "h";
        }
    }

    /**
     * 从类型字符串解析
     */
    public static KeyType parseType(String typeStr) {
        if (typeStr == null)
            return KeyType.NONE;
        switch (typeStr.toLowerCase()) {
            case "string":
                return KeyType.STRING;
            case "hash":
                return KeyType.HASH;
            case "list":
                return KeyType.LIST;
            case "set":
                return KeyType.SET;
            case "zset":
                return KeyType.ZSET;
            case "stream":
                return KeyType.STREAM;
            default:
                return KeyType.NONE;
        }
    }
}
