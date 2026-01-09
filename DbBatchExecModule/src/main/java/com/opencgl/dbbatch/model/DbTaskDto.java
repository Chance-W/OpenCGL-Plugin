package com.opencgl.dbbatch.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data; // Keep for reference, but methods are manual
import lombok.EqualsAndHashCode;

/**
 * 数据库执行任务 DTO
 */
@Data
public class DbTaskDto {
    
    @JSONField(name = "PARENT_ID")
    private String parentId;
    
    @JSONField(name = "IS_GROUP")
    private boolean isGroup; 
    
    @JSONField(name = "DB_TYPE")
    private String dbType;
    
    @JSONField(name = "HOST")
    private String host;
    
    @JSONField(name = "PORT")
    private String port;
    
    @JSONField(name = "USERNAME")
    private String username;
    
    @JSONField(name = "PASSWORD")
    private String password;
    
    @JSONField(name = "DATABASE_NAME")
    private String database;
    
    @JSONField(name = "SQL_CONTENT")
    private String sqlContent;
    
    @JSONField(name = "REPLACE_CONFIG")
    private String replaceConfig;
    
    @JSONField(name = "DESCRIPTION")
    private String description;
    
    @JSONField(name = "ID")
    private String id;
    
    @JSONField(name = "NAME")
    private String name;
    
    // 瞬态字段 (不存库)
    private transient boolean isSuccess;
    private transient String lastResult;

    @Override
    public String toString() {
        return name;
    }

    // Manual Getters and Setters
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getSqlContent() { return sqlContent; }
    public void setSqlContent(String sqlContent) { this.sqlContent = sqlContent; }

    public String getReplaceConfig() { return replaceConfig; }
    public void setReplaceConfig(String replaceConfig) { this.replaceConfig = replaceConfig; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public boolean isSuccess() { return isSuccess; }
    public void setSuccess(boolean success) { isSuccess = success; }
    
    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }
}
