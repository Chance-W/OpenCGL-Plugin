package com.opencgl.dubbo.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DubboEnvConfig {
    private String envName;
    private String registryAddress;
    /** ZK 命名空间分组，用于 registryConfig.setGroup()，对应新字段 ZK_GROUP */
    private String zkGroup;
    /** 兼容旧数据保留，新代码优先使用 zkGroup */
    private String registryGroup;
    private String apiPackagePath;
    private String serviceData;

    public DubboEnvConfig() {}

    public DubboEnvConfig(String envName, String registryAddress, String zkGroup, String registryGroup, String apiPackagePath, String serviceData) {
        this.envName = envName;
        this.registryAddress = registryAddress;
        this.zkGroup = zkGroup;
        this.registryGroup = registryGroup;
        this.apiPackagePath = apiPackagePath;
        this.serviceData = serviceData;
    }

    public String getEnvName() { return envName; }
    public void setEnvName(String envName) { this.envName = envName; }

    public String getRegistryAddress() { return registryAddress; }
    public void setRegistryAddress(String registryAddress) { this.registryAddress = registryAddress; }

    public String getZkGroup() { return zkGroup != null ? zkGroup : registryGroup; }
    public void setZkGroup(String zkGroup) { this.zkGroup = zkGroup; }

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
        private String zkGroup;
        private String registryGroup;
        private String apiPackagePath;
        private String serviceData;

        public Builder envName(String envName) { this.envName = envName; return this; }
        public Builder registryAddress(String registryAddress) { this.registryAddress = registryAddress; return this; }
        public Builder zkGroup(String zkGroup) { this.zkGroup = zkGroup; return this; }
        public Builder registryGroup(String registryGroup) { this.registryGroup = registryGroup; return this; }
        public Builder apiPackagePath(String apiPackagePath) { this.apiPackagePath = apiPackagePath; return this; }
        public Builder serviceData(String serviceData) { this.serviceData = serviceData; return this; }

        public DubboEnvConfig build() {
            return new DubboEnvConfig(envName, registryAddress, zkGroup, registryGroup, apiPackagePath, serviceData);
        }
    }
}
