package com.opencgl.http.model;

import com.opencgl.base.model.BaseDataDto;

import java.sql.Timestamp;

/**
 * HTTP调试器树节点数据模型
 * 
 * @author Chance.W
 */
public class HttpTreeItem extends BaseDataDto {

    public String getNodeType() { return nodeType; }
    public String getIconName() { return iconName; }
    public String getDescription() { return description; }
    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public String getHeaders() { return headers; }
    public String getParams() { return params; }
    public String getBody() { return body; }
    public String getBodyType() { return bodyType; }
    public String getAuthConfig() { return authConfig; }
    public String getHookScript() { return hookScript; }
    public Integer getTimeout() { return timeout; }
    public String getLastResponse() { return lastResponse; }
    public Integer getLastStatusCode() { return lastStatusCode; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    
    // 节点类型常量
    public static final String TYPE_FOLDER = "FOLDER";
    public static final String TYPE_REQUEST = "REQUEST";

    // HTTP方法常量
    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_DELETE = "DELETE";
    public static final String METHOD_PATCH = "PATCH";
    public static final String METHOD_HEAD = "HEAD";
    public static final String METHOD_OPTIONS = "OPTIONS";

    // Getters and Setters
    private String nodeType;
    private String iconName;
    private String description;
    
    // 请求配置字段（仅REQUEST类型使用）
    private String method;
    private String url;
    private String headers;      // JSON格式存储
    private String params;       // JSON格式存储（query params）
    private String body;
    private String bodyType;     // JSON/FORM/RAW/XML
    private String authConfig;   // 认证配置 JSON
    private String hookScript;   // Hook脚本名称
    private Integer timeout;     // 超时时间（毫秒）
    private Boolean sslVerification; // SSL验证
    private Boolean followRedirects; // 跟随重定向
    
    // mTLS
    private String clientCertPath;
    private String clientCertPass;
    private String serverCertPath; // Custom CA
    private String serverCertPass;
    
    // 响应缓存（可选，不持久化到数据库）
    private transient String lastResponse;
    private transient Integer lastStatusCode;
    
    // 审计字段
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    /**
     * 获取节点图标
     */
    public String getNodeIcon() {
        if (iconName != null) return iconName;
        
        if (nodeType == null) return "📁";
        
        switch (nodeType) {
            case TYPE_FOLDER: return "📁";
            case TYPE_REQUEST: return getMethodIcon();
            default: return "📁";
        }
    }
    
    /**
     * 根据HTTP方法获取图标
     */
    private String getMethodIcon() {
        if (method == null) return "📄";
        
        switch (method) {
            case METHOD_GET: return "🟢";
            case METHOD_POST: return "🟡";
            case METHOD_PUT: return "🔵";
            case METHOD_DELETE: return "🔴";
            case METHOD_PATCH: return "🟣";
            default: return "⚪";
        }
    }
    
    /**
     * 判断是否是请求节点
     */
    public boolean isRequest() {
        return TYPE_REQUEST.equals(nodeType);
    }
    
    /**
     * 判断是否是文件夹
     */
    public boolean isFolder() {
        return TYPE_FOLDER.equals(nodeType);
    }

    @com.alibaba.fastjson.annotation.JSONField(name = "node_type")
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    @com.alibaba.fastjson.annotation.JSONField(name = "icon_name")
    public void setIconName(String iconName) { this.iconName = iconName; }

    public void setDescription(String description) { this.description = description; }

    public void setMethod(String method) { this.method = method; }

    public void setUrl(String url) { this.url = url; }

    public void setHeaders(String headers) { this.headers = headers; }

    public void setParams(String params) { this.params = params; }

    public void setBody(String body) { this.body = body; }

    @com.alibaba.fastjson.annotation.JSONField(name = "body_type")
    public void setBodyType(String bodyType) { this.bodyType = bodyType; }

    @com.alibaba.fastjson.annotation.JSONField(name = "auth_config")
    public void setAuthConfig(String authConfig) { this.authConfig = authConfig; }

    @com.alibaba.fastjson.annotation.JSONField(name = "hook_script")
    public void setHookScript(String hookScript) { this.hookScript = hookScript; }

    public void setTimeout(Integer timeout) { this.timeout = timeout; }

    public Boolean getSslVerification() { return sslVerification; }
    public void setSslVerification(Boolean sslVerification) { this.sslVerification = sslVerification; }

    public Boolean getFollowRedirects() { return followRedirects; }
    public void setFollowRedirects(Boolean followRedirects) { this.followRedirects = followRedirects; }

    public String getClientCertPath() { return clientCertPath; }
    public void setClientCertPath(String clientCertPath) { this.clientCertPath = clientCertPath; }

    public String getClientCertPass() { return clientCertPass; }
    public void setClientCertPass(String clientCertPass) { this.clientCertPass = clientCertPass; }

    public String getServerCertPath() { return serverCertPath; }
    public void setServerCertPath(String serverCertPath) { this.serverCertPath = serverCertPath; }

    public String getServerCertPass() { return serverCertPass; }
    public void setServerCertPass(String serverCertPass) { this.serverCertPass = serverCertPass; }

    public void setLastResponse(String lastResponse) { this.lastResponse = lastResponse; }

    public void setLastStatusCode(Integer lastStatusCode) { this.lastStatusCode = lastStatusCode; }

    @com.alibaba.fastjson.annotation.JSONField(name = "created_at")
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @com.alibaba.fastjson.annotation.JSONField(name = "updated_at")
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
