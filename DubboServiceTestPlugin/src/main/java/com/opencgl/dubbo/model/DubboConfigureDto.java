package com.opencgl.dubbo.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

public class DubboConfigureDto {
    private String envInfo;
    private String interfaceInfo;
    private String methodInfo;
    private String requestType;

    public DubboConfigureDto() {}

    public DubboConfigureDto(String envInfo, String interfaceInfo, String methodInfo, String requestType) {
        this.envInfo = envInfo;
        this.interfaceInfo = interfaceInfo;
        this.methodInfo = methodInfo;
        this.requestType = requestType;
    }

    public String getEnvInfo() { return envInfo; }
    public void setEnvInfo(String envInfo) { this.envInfo = envInfo; }

    public String getInterfaceInfo() { return interfaceInfo; }
    public void setInterfaceInfo(String interfaceInfo) { this.interfaceInfo = interfaceInfo; }

    public String getMethodInfo() { return methodInfo; }
    public void setMethodInfo(String methodInfo) { this.methodInfo = methodInfo; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    @Override
    public String toString() {
        return "DubboConfigureDto(envInfo=" + this.getEnvInfo() + ", interfaceInfo=" + this.getInterfaceInfo() + ", methodInfo=" + this.getMethodInfo() + ", requestType=" + this.getRequestType() + ")";
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String envInfo;
        private String interfaceInfo;
        private String methodInfo;
        private String requestType;

        public Builder envInfo(String envInfo) { this.envInfo = envInfo; return this; }
        public Builder interfaceInfo(String interfaceInfo) { this.interfaceInfo = interfaceInfo; return this; }
        public Builder methodInfo(String methodInfo) { this.methodInfo = methodInfo; return this; }
        public Builder requestType(String requestType) { this.requestType = requestType; return this; }

        public DubboConfigureDto build() {
            return new DubboConfigureDto(envInfo, interfaceInfo, methodInfo, requestType);
        }
    }
}
