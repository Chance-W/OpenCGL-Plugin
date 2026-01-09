package com.opencgl.redis.model;


import com.opencgl.base.model.BaseDataDto;
import lombok.*;

/**
 * Redis 连接配置 DTO
 * @author Chance.W
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class RedisWidgetDto extends BaseDataDto {
    
    // 基础信息
    private String connectionName;
    
    // 连接类型
    private RedisConnectionType connectionType = RedisConnectionType.STANDALONE;
    
    // 单机配置
    private String host = "localhost";
    private Integer port = 6379;
    private String password;
    private Integer database = 0;
    
    // 集群配置
    private String clusterNodes; // 逗号分隔: node1:6379,node2:6379
    
    // 哨兵配置
    private String sentinelMaster;
    private String sentinelNodes; // 逗号分隔
    
    // 连接状态
    private transient boolean connected = false;
    
    // 废弃兼容
    @Deprecated
    private String redisIpAddress;
    
    // ===== 手动添加 Getter/Setter (备用) =====
    
    public String getConnectionName() { return connectionName; }
    public void setConnectionName(String connectionName) { this.connectionName = connectionName; }
    
    public RedisConnectionType getConnectionType() { return connectionType; }
    public void setConnectionType(RedisConnectionType connectionType) { this.connectionType = connectionType; }
    
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public Integer getDatabase() { return database; }
    public void setDatabase(Integer database) { this.database = database; }
    
    public String getClusterNodes() { return clusterNodes; }
    public void setClusterNodes(String clusterNodes) { this.clusterNodes = clusterNodes; }
    
    public String getSentinelMaster() { return sentinelMaster; }
    public void setSentinelMaster(String sentinelMaster) { this.sentinelMaster = sentinelMaster; }
    
    public String getSentinelNodes() { return sentinelNodes; }
    public void setSentinelNodes(String sentinelNodes) { this.sentinelNodes = sentinelNodes; }
    
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    
    @Deprecated
    public String getRedisIpAddress() { return redisIpAddress; }
    @Deprecated
    public void setRedisIpAddress(String redisIpAddress) { this.redisIpAddress = redisIpAddress; }
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        if (connectionName != null && !connectionName.isEmpty()) {
            return connectionName;
        }
        if (connectionType == null) {
            return host + ":" + port;
        }
        switch (connectionType) {
            case STANDALONE:
                return host + ":" + port;
            case CLUSTER:
                return "Cluster: " + (clusterNodes != null ? clusterNodes.split(",")[0] : "");
            case SENTINEL:
                return "Sentinel: " + sentinelMaster;
            default:
                return "Redis Connection";
        }
    }
    
    /**
     * 获取连接地址（用于显示）
     */
    public String getConnectionAddress() {
        if (connectionType == null) {
            return host + ":" + port;
        }
        switch (connectionType) {
            case STANDALONE:
                return host + ":" + port;
            case CLUSTER:
                return clusterNodes;
            case SENTINEL:
                return sentinelNodes;
            default:
                return "";
        }
    }
    
    /**
     * 重写 getName，让 TreeCellFactory 使用 displayName 显示
     */
    @Override
    public String getName() {
        String superName = super.getName();
        if (superName != null && !superName.isEmpty()) {
            return superName;
        }
        return getDisplayName();
    }
}
