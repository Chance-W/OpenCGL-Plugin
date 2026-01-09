package com.opencgl.redis.model;

import com.opencgl.redis.i18n.I18N;

/**
 * Redis 连接类型枚举
 */
public enum RedisConnectionType {

    STANDALONE("STANDALONE", "localhost:6379"),
    CLUSTER("CLUSTER", "node1:6379,node2:6379,node3:6379"),
    SENTINEL("SENTINEL", "sentinel1:26379,sentinel2:26379");

    private final String displayName;
    private final String placeholder;

    RedisConnectionType(String displayName, String placeholder) {
        this.displayName = displayName;
        this.placeholder = placeholder;
    }

    public String getDisplayName() {
        return I18N.get("type." + name().toLowerCase());
    }

    public String getPlaceholder() {
        return placeholder;
    }
}
