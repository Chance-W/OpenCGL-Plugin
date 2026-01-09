package com.opencgl.dubbo.model;

import com.opencgl.base.model.BaseDataDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/20 14:33
 * @since v9.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DubboTreeItem extends BaseDataDto {
    private String envName;
    private String interfaceInfo;
    private String methodInfo;
    private Boolean isSelected;
    private String requestType;
    private String inputText;
    private String hookScript;
    private Integer requestCount;

    private String version;
    private String attachments;

    public String getEnvName() { return envName; }
    public void setEnvName(String envName) { this.envName = envName; }
    
    public String getInterfaceInfo() { return interfaceInfo; }
    public void setInterfaceInfo(String interfaceInfo) { this.interfaceInfo = interfaceInfo; }
    
    public String getMethodInfo() { return methodInfo; }
    public void setMethodInfo(String methodInfo) { this.methodInfo = methodInfo; }
    
    public Boolean getIsSelected() { return isSelected; }
    public void setIsSelected(Boolean isSelected) { this.isSelected = isSelected; }
    
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    
    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }
    
    public String getHookScript() { return hookScript; }
    public void setHookScript(String hookScript) { this.hookScript = hookScript; }
    
    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getAttachments() { return attachments; }
    public void setAttachments(String attachments) { this.attachments = attachments; }

    private Integer timeout;
    private Integer retries;
    private String group;
    /** Dubbo RPC 服务分组，用于 reference.setGroup()，对应 DUBBO_GROUP 列 */
    private String dubboGroup;
    // providerUrl stored as 'address' in History, let's call it providerUrl here for clarity or match history?
    // History uses 'address' but DubboTreeItem is specific. Let's use 'providerUrl'.
    private String providerUrl; 

    // Phase 17: TLS
    private Boolean tlsEnable;
    private Boolean mutualAuth;
    private String clientCertPath;
    private String clientKeyPath;
    private String clientKeyPassword;
    private String caCertPath;

    public Boolean getTlsEnable() { return tlsEnable; }
    public void setTlsEnable(Boolean tlsEnable) { this.tlsEnable = tlsEnable; }

    public Boolean getMutualAuth() { return mutualAuth; }
    public void setMutualAuth(Boolean mutualAuth) { this.mutualAuth = mutualAuth; }

    public String getClientCertPath() { return clientCertPath; }
    public void setClientCertPath(String clientCertPath) { this.clientCertPath = clientCertPath; }

    public String getClientKeyPath() { return clientKeyPath; }
    public void setClientKeyPath(String clientKeyPath) { this.clientKeyPath = clientKeyPath; }

    public String getClientKeyPassword() { return clientKeyPassword; }
    public void setClientKeyPassword(String clientKeyPassword) { this.clientKeyPassword = clientKeyPassword; }

    public String getCaCertPath() { return caCertPath; }
    public void setCaCertPath(String caCertPath) { this.caCertPath = caCertPath; }

    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }

    public Integer getRetries() { return retries; }
    public void setRetries(Integer retries) { this.retries = retries; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getDubboGroup() { return dubboGroup; }
    public void setDubboGroup(String dubboGroup) { this.dubboGroup = dubboGroup; }

    public String getProviderUrl() { return providerUrl; }
    public void setProviderUrl(String providerUrl) { this.providerUrl = providerUrl; }
}
