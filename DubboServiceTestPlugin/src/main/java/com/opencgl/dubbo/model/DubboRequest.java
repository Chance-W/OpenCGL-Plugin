package com.opencgl.dubbo.model;

import java.util.Map;

import com.opencgl.base.hook.TlsConfig;
import com.opencgl.base.model.BaseRequest;

/**
 * @author Chance.W
 */
public class DubboRequest extends BaseRequest {
    private String dubboRegistryAddr;
    private String dubboRegistryGroup;
    private String dubboProvidersUrl;
    private Integer settingTimeout;
    private String interfaceName;
    private String method;
    private String reqType;
    private String reqJsonMessage;
    
    /**
     * TLS 配置 (可选)
     */
    private TlsConfig tlsConfig;
    
    /**
     * 请求次数 (默认为1)
     */
    private Integer requestCount;
    /**
     * 重试次数 (默认为0)
     */
    private Integer retries;

    private String version;
    private java.util.Map<String, String> attachments;
    /** Dubbo RPC 服务分组，用于 reference.setGroup()，与 ZK 命名空间分组独立 */
    private String dubboGroup;

    public DubboRequest() {}

    public DubboRequest(String dubboRegistryAddr, String dubboRegistryGroup, String dubboProvidersUrl, Integer settingTimeout, String interfaceName, String method, String reqType, String reqJsonMessage, TlsConfig tlsConfig, Integer requestCount, Integer retries, String version, java.util.Map<String, String> attachments, String dubboGroup) {
        this.dubboRegistryAddr = dubboRegistryAddr;
        this.dubboRegistryGroup = dubboRegistryGroup;
        this.dubboProvidersUrl = dubboProvidersUrl;
        this.settingTimeout = settingTimeout;
        this.interfaceName = interfaceName;
        this.method = method;
        this.reqType = reqType;
        this.reqJsonMessage = reqJsonMessage;
        this.tlsConfig = tlsConfig;
        this.requestCount = requestCount;
        this.retries = retries;
        this.version = version;
        this.attachments = attachments;
        this.dubboGroup = dubboGroup;
    }

    public String getDubboRegistryAddr() { return dubboRegistryAddr; }
    public void setDubboRegistryAddr(String dubboRegistryAddr) { this.dubboRegistryAddr = dubboRegistryAddr; }

    public String getDubboRegistryGroup() { return dubboRegistryGroup; }
    public void setDubboRegistryGroup(String dubboRegistryGroup) { this.dubboRegistryGroup = dubboRegistryGroup; }

    public String getDubboProvidersUrl() { return dubboProvidersUrl; }
    public void setDubboProvidersUrl(String dubboProvidersUrl) { this.dubboProvidersUrl = dubboProvidersUrl; }

    public Integer getSettingTimeout() { return settingTimeout; }
    public void setSettingTimeout(Integer settingTimeout) { this.settingTimeout = settingTimeout; }

    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getReqType() { return reqType; }
    public void setReqType(String reqType) { this.reqType = reqType; }

    public String getReqJsonMessage() { return reqJsonMessage; }
    public void setReqJsonMessage(String reqJsonMessage) { this.reqJsonMessage = reqJsonMessage; }

    public TlsConfig getTlsConfig() { return tlsConfig; }
    public void setTlsConfig(TlsConfig tlsConfig) { this.tlsConfig = tlsConfig; }

    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }

    public Integer getRetries() { return retries; }
    public void setRetries(Integer retries) { this.retries = retries; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Map<String, String> getAttachments() { return attachments; }
    public void setAttachments(Map<String, String> attachments) { this.attachments = attachments; }

    public String getDubboGroup() { return dubboGroup; }
    public void setDubboGroup(String dubboGroup) { this.dubboGroup = dubboGroup; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String dubboRegistryAddr;
        private String dubboRegistryGroup;
        private String dubboProvidersUrl;
        private Integer settingTimeout;
        private String interfaceName;
        private String method;
        private String reqType;
        private String reqJsonMessage;
        private TlsConfig tlsConfig;
        private Integer requestCount;
        private Integer retries;
        private String version;
        private java.util.Map<String, String> attachments;
        private String dubboGroup;

        public Builder dubboRegistryAddr(String dubboRegistryAddr) { this.dubboRegistryAddr = dubboRegistryAddr; return this; }
        public Builder dubboRegistryGroup(String dubboRegistryGroup) { this.dubboRegistryGroup = dubboRegistryGroup; return this; }
        public Builder dubboProvidersUrl(String dubboProvidersUrl) { this.dubboProvidersUrl = dubboProvidersUrl; return this; }
        public Builder settingTimeout(Integer settingTimeout) { this.settingTimeout = settingTimeout; return this; }
        public Builder interfaceName(String interfaceName) { this.interfaceName = interfaceName; return this; }
        public Builder method(String method) { this.method = method; return this; }
        public Builder reqType(String reqType) { this.reqType = reqType; return this; }
        public Builder reqJsonMessage(String reqJsonMessage) { this.reqJsonMessage = reqJsonMessage; return this; }
        public Builder tlsConfig(TlsConfig tlsConfig) { this.tlsConfig = tlsConfig; return this; }
        public Builder requestCount(Integer requestCount) { this.requestCount = requestCount; return this; }
        public Builder retries(Integer retries) { this.retries = retries; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder attachments(Map<String, String> attachments) { this.attachments = attachments; return this; }
        public Builder dubboGroup(String dubboGroup) { this.dubboGroup = dubboGroup; return this; }

        public DubboRequest build() {
            return new DubboRequest(dubboRegistryAddr, dubboRegistryGroup, dubboProvidersUrl, settingTimeout, interfaceName, method, reqType, reqJsonMessage, tlsConfig, requestCount, retries, version, attachments, dubboGroup);
        }
    }
}
