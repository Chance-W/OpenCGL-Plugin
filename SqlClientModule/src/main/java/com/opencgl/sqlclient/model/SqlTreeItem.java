package com.opencgl.sqlclient.model;

import com.alibaba.fastjson.annotation.JSONField;
import com.opencgl.base.model.BaseDataDto;

import java.sql.Timestamp;

/**
 * SQL客户端树节点数据模型
 * 
 * @author Chance.W
 */
public class SqlTreeItem extends BaseDataDto {
    
    private String nodeType;
    private String iconName;
    
    // 连接配置字段（仅CONNECTION类型使用）
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;  // 加密存储
    private String dbType;    // MySQL/PostgreSQL/Oracle/SQLite
    
    // 数据库对象信息（TABLE/VIEW/FUNCTION等类型使用）
    private String schemaName;
    private String tableName;
    private String objectType;
    private String objectDdl;
    
    // 审计字段
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    /**
     * 获取节点类型对应的图标
     */
    public String getNodeIcon() {
        if (iconName != null) return iconName;
        
        if (nodeType == null) return "📁";
        
        try {
            NodeType type = NodeType.valueOf(nodeType);
            switch (type) {
                case CONNECTION_GROUP: return "📁";
                case CONNECTION: return "🔌";
                case SCHEMA: return "🗂️";
                case TABLE_GROUP: return "📊";
                case TABLE: return "📋";
                case VIEW_GROUP: return "👁️";
                case VIEW: return "👁️";
                case FUNCTION_GROUP: return "⚙️";
                case FUNCTION: return "⚙️";
                case PROCEDURE_GROUP: return "📝";
                case PROCEDURE: return "📝";
                default: return "📁";
            }
        } catch (IllegalArgumentException e) {
            return "📁";
        }
    }
    
    /**
     * 判断是否是连接节点
     */
    public boolean isConnection() {
        return NodeType.CONNECTION.name().equals(nodeType);
    }
    
    /**
     * 判断是否是数据库对象
     */
    public boolean isDatabaseObject() {
        return NodeType.TABLE.name().equals(nodeType) || 
               NodeType.VIEW.name().equals(nodeType) || 
               NodeType.FUNCTION.name().equals(nodeType) || 
               NodeType.PROCEDURE.name().equals(nodeType);
    }
    
    // Getters and Setters
    public String getNodeType() { return nodeType; }
    @JSONField(name = "node_type")
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public void setNodeTypeEnum(NodeType type) { this.nodeType = type.name(); }
    public NodeType getNodeTypeEnum() { 
        if (nodeType == null) return null;
        try {
            return NodeType.valueOf(nodeType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    public String getIconName() { return iconName; }
    @JSONField(name = "icon_name")
    public void setIconName(String iconName) { this.iconName = iconName; }
    
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    
    public String getDatabaseName() { return databaseName; }
    @JSONField(name = "database_name")
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getDbType() { return dbType; }
    @JSONField(name = "db_type")
    public void setDbType(String dbType) { this.dbType = dbType; }
    
    public String getSchemaName() { return schemaName; }
    @JSONField(name = "schema_name")
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    
    public String getTableName() { return tableName; }
    @JSONField(name = "table_name")
    public void setTableName(String tableName) { this.tableName = tableName; }
    
    public String getObjectType() { return objectType; }
    @JSONField(name = "object_type")
    public void setObjectType(String objectType) { this.objectType = objectType; }
    
    public String getObjectDdl() { return objectDdl; }
    @JSONField(name = "object_ddl")
    public void setObjectDdl(String objectDdl) { this.objectDdl = objectDdl; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    @JSONField(name = "created_at")
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    @JSONField(name = "updated_at")
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    
    @Override
    public Long getParentId() { return super.getParentId(); }
    @Override
    @JSONField(name = "parent_id")
    public void setParentId(Long parentId) { super.setParentId(parentId); }
    
    @Override
    public Boolean getIsLeaf() { return super.getIsLeaf(); }
    @Override
    @JSONField(name = "is_leaf")
    public void setIsLeaf(Boolean isLeaf) { super.setIsLeaf(isLeaf); }
    
    @Override
    public Integer getSortOrder() { return super.getSortOrder(); }
    @Override
    @JSONField(name = "sort_order")
    public void setSortOrder(Integer sortOrder) { super.setSortOrder(sortOrder); }
}
