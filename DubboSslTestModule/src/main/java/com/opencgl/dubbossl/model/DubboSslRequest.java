package com.opencgl.dubbossl.model;

/**
 * Dubbo SSL 请求模型
 *
 * @author Chance.W
 */
public class DubboSslRequest {
    private String dubboRegistryAddr;
    private String dubboRegistryGroup;
    private String dubboProvidersUrl;
    private String interfaceName;
    private String method;
    private String reqType;
    private String reqJsonMessage;
    
    // TLS 配置 (界面配置，非 Hook)
    private String clientCertPath;
    private String clientKeyPath;
    private String clientKeyPassword;
    private String caCertPath;
    private Integer requestCount;
    
    private DubboSslRequest(Builder builder) {
        this.dubboRegistryAddr = builder.dubboRegistryAddr;
        this.dubboRegistryGroup = builder.dubboRegistryGroup;
        this.dubboProvidersUrl = builder.dubboProvidersUrl;
        this.interfaceName = builder.interfaceName;
        this.method = builder.method;
        this.reqType = builder.reqType;
        this.reqJsonMessage = builder.reqJsonMessage;
        this.clientCertPath = builder.clientCertPath;
        this.clientKeyPath = builder.clientKeyPath;
        this.clientKeyPassword = builder.clientKeyPassword;
        this.caCertPath = builder.caCertPath;
        this.requestCount = builder.requestCount;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getDubboRegistryAddr() { return dubboRegistryAddr; }
    public String getDubboRegistryGroup() { return dubboRegistryGroup; }
    public String getDubboProvidersUrl() { return dubboProvidersUrl; }
    public String getInterfaceName() { return interfaceName; }
    public String getMethod() { return method; }
    public String getReqType() { return reqType; }
    public String getReqJsonMessage() { return reqJsonMessage; }
    public String getClientCertPath() { return clientCertPath; }
    public String getClientKeyPath() { return clientKeyPath; }
    public String getClientKeyPassword() { return clientKeyPassword; }
    public String getCaCertPath() { return caCertPath; }
    public Integer getRequestCount() { return requestCount; }
    
    public static class Builder {
        private String dubboRegistryAddr;
        private String dubboRegistryGroup;
        private String dubboProvidersUrl;
        private String interfaceName;
        private String method;
        private String reqType;
        private String reqJsonMessage;
        private String clientCertPath;
        private String clientKeyPath;
        private String clientKeyPassword;
        private String caCertPath;
        private Integer requestCount;
        
        public Builder dubboRegistryAddr(String val) { dubboRegistryAddr = val; return this; }
        public Builder dubboRegistryGroup(String val) { dubboRegistryGroup = val; return this; }
        public Builder dubboProvidersUrl(String val) { dubboProvidersUrl = val; return this; }
        public Builder interfaceName(String val) { interfaceName = val; return this; }
        public Builder method(String val) { method = val; return this; }
        public Builder reqType(String val) { reqType = val; return this; }
        public Builder reqJsonMessage(String val) { reqJsonMessage = val; return this; }
        public Builder clientCertPath(String val) { clientCertPath = val; return this; }
        public Builder clientKeyPath(String val) { clientKeyPath = val; return this; }
        public Builder clientKeyPassword(String val) { clientKeyPassword = val; return this; }
        public Builder caCertPath(String val) { caCertPath = val; return this; }
        public Builder requestCount(Integer val) { requestCount = val; return this; }
        
        public DubboSslRequest build() {
            return new DubboSslRequest(this);
        }
    }
}
