package com.opencgl.dubbossl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class DubboEnvConfig {
    private String envName;
    private String registryAddress;
    private String registryGroup;
    private String apiPackagePath;
    private String serviceData;

    public DubboEnvConfig() {}

    public DubboEnvConfig(String envName, String registryAddress, String registryGroup, String apiPackagePath, String serviceData) {
        this.envName = envName;
        this.registryAddress = registryAddress;
        this.registryGroup = registryGroup;
        this.apiPackagePath = apiPackagePath;
        this.serviceData = serviceData;
    }

    public String getEnvName() { return envName; }
    public void setEnvName(String envName) { this.envName = envName; }

    public String getRegistryAddress() { return registryAddress; }
    public void setRegistryAddress(String registryAddress) { this.registryAddress = registryAddress; }

    public String getRegistryGroup() { return registryGroup; }
    public void setRegistryGroup(String registryGroup) { this.registryGroup = registryGroup; }

    public String getApiPackagePath() { return apiPackagePath; }
    public void setApiPackagePath(String apiPackagePath) { this.apiPackagePath = apiPackagePath; }

    public String getServiceData() { return serviceData; }
    public void setServiceData(String serviceData) { this.serviceData = serviceData; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String envName;
        private String registryAddress;
        private String registryGroup;
        private String apiPackagePath;
        private String serviceData;

        public Builder envName(String envName) { this.envName = envName; return this; }
        public Builder registryAddress(String registryAddress) { this.registryAddress = registryAddress; return this; }
        public Builder registryGroup(String registryGroup) { this.registryGroup = registryGroup; return this; }
        public Builder apiPackagePath(String apiPackagePath) { this.apiPackagePath = apiPackagePath; return this; }
        public Builder serviceData(String serviceData) { this.serviceData = serviceData; return this; }

        public DubboEnvConfig build() {
            return new DubboEnvConfig(envName, registryAddress, registryGroup, apiPackagePath, serviceData);
        }
    }
}
