package com.opencgl.dubbo.model;

import com.opencgl.base.model.HistoryItem;

import java.util.Date;

public class DubboHistoryItem extends HistoryItem {
    private String interfaceName;
    private String methodName;
    private String paramType;
    private String address;
    private String version;
    private String requestType;
    private String group;  // ZK 分组（历史字段，保留）
    private String dubboGroup; // Dubbo RPC 服务分组
    private Integer timeout;
    private Integer retries;
    private String hookScript;
    private String attachments;

    // Phase 23: TLS support for History
    private Boolean tlsEnable;
    private String clientCertPath;
    private String clientKeyPath;
    private String clientKeyPassword;
    private String caCertPath;

    private String envName;
    private String inputText;

    public DubboHistoryItem(String id, Date timestamp, String status, String summary,
                            String interfaceName, String methodName, String address, String envName, String inputText,
                            String group, String dubboGroup, String version, String requestType, Integer timeout, Integer retries, String hookScript, String attachments,
                            Boolean tlsEnable, String clientCertPath, String clientKeyPath, String clientKeyPassword, String caCertPath) {
        super(id, timestamp, status, summary);
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.address = address;
        this.envName = envName;
        this.inputText = inputText;
        this.group = group;
        this.dubboGroup = dubboGroup;
        this.version = version;
        this.requestType = requestType;
        this.timeout = timeout;
        this.retries = retries;
        this.hookScript = hookScript;
        this.attachments = attachments;

        this.tlsEnable = tlsEnable;
        this.clientCertPath = clientCertPath;
        this.clientKeyPath = clientKeyPath;
        this.clientKeyPassword = clientKeyPassword;
        this.caCertPath = caCertPath;
    }

    public DubboHistoryItem() {
    } // No-args constructor for JSON/Reflection

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getEnvName() {
        return envName;
    }

    public void setEnvName(String envName) {
        this.envName = envName;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    private String outputText;

    public String getOutputText() {
        return outputText;
    }

    public void setOutputText(String outputText) {
        this.outputText = outputText;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDubboGroup() {
        return dubboGroup;
    }

    public void setDubboGroup(String dubboGroup) {
        this.dubboGroup = dubboGroup;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public String getHookScript() {
        return hookScript;
    }

    public void setHookScript(String hookScript) {
        this.hookScript = hookScript;
    }

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }

    public Boolean getTlsEnable() {
        return tlsEnable;
    }

    public void setTlsEnable(Boolean tlsEnable) {
        this.tlsEnable = tlsEnable;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public void setClientCertPath(String clientCertPath) {
        this.clientCertPath = clientCertPath;
    }

    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public void setClientKeyPath(String clientKeyPath) {
        this.clientKeyPath = clientKeyPath;
    }

    public String getClientKeyPassword() {
        return clientKeyPassword;
    }

    public void setClientKeyPassword(String clientKeyPassword) {
        this.clientKeyPassword = clientKeyPassword;
    }

    public String getCaCertPath() {
        return caCertPath;
    }

    public void setCaCertPath(String caCertPath) {
        this.caCertPath = caCertPath;
    }
}
