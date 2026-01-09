package com.opencgl.dubbossl.model;

import com.opencgl.base.model.BaseDataDto;

/**
 * Dubbo SSL 树节点模型
 * 
 * @author Chance.W
 */
public class DubboSslTreeItem extends BaseDataDto {
    private String envName;
    private String interfaceInfo;
    private String methodInfo;
    private Boolean isSelected;
    private String requestType;
    private String inputText;
    private Integer requestCount;
    
    public DubboSslTreeItem() {}
    
    public DubboSslTreeItem(String envName, String interfaceInfo, String methodInfo, 
                            Boolean isSelected, String requestType, String inputText) {
        this.envName = envName;
        this.interfaceInfo = interfaceInfo;
        this.methodInfo = methodInfo;
        this.isSelected = isSelected;
        this.requestType = requestType;
        this.inputText = inputText;
    }
    
    // Getters and Setters
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
    
    private String hookScript;
    public String getHookScript() { return hookScript; }
    public void setHookScript(String hookScript) { this.hookScript = hookScript; }
    
    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }
    
    @Override
    public String toString() {
        if (methodInfo != null && !methodInfo.isEmpty()) {
            return methodInfo;
        }
        if (interfaceInfo != null && !interfaceInfo.isEmpty()) {
            String[] parts = interfaceInfo.split("\\.");
            return parts.length > 0 ? parts[parts.length - 1] : interfaceInfo;
        }
        if (envName != null && !envName.isEmpty()) {
            return envName;
        }
        return getName() != null ? getName() : "未命名";
    }
}
