package com.opencgl.dubbo.mock.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 注册中心节点模型（树的顶层节点）
 * 一个注册中心可以包含多个 Provider (接口Mock)
 */
public class RegistryNodeModel {

    /** 唯一标识符，用于持久化和查询 */
    private String id;

    /** 注册中心显示名称，如 "本地ZK" */
    private String name;

    /** 注册中心类型: zookeeper / nacos */
    private String registryType;

    /** 注册中心地址，如 "127.0.0.1:2181" */
    private String registryAddress;

    /** ZK 命名空间分组，对应 registryConfig.setGroup() */
    private String zkGroup;

    /** Dubbo 暴露协议类型，如 "dubbo" */
    private String protocol;

    /** Dubbo 暴露端口，如 20880 */
    private int port;

    /** 用于 Dubbo ApplicationConfig 的应用名称，如 "dubbo-mock-provider" */
    private String applicationName;

    /** 此注册中心下的所有 Provider Mock 节点 */
    private List<ProviderNodeModel> providers;

    /** 是否启用此注册中心（是否在启动时自动连接）*/
    private boolean enabled;

    /**
     * Provider 暴露时绑定的本机 IP 地址。
     * 若为空则由 Dubbo 自动选取；若指定则使用该 IP 注册到注册中心，
     * 在多网卡或 Docker/VPN 环境下非常有用。
     */
    private String providerHost;

    /**
     * 是否使用无注册中心模式（Dubbo 直连）。
     * 为 true 时将 RegistryConfig 地址设为 "N/A"，Dubbo 仅在本地暴露服务，
     * 不向任何注册中心注册，消费方可通过 dubbo://ip:port/接口 直连调用。
     */
    private boolean noRegistry;

    public RegistryNodeModel() {
        this.id = UUID.randomUUID().toString();
        this.registryType = "zookeeper";
        this.registryAddress = "127.0.0.1:2181";
        this.zkGroup = "";
        this.protocol = "dubbo";
        this.port = 20880;
        this.applicationName = "dubbo-mock-provider";
        this.providers = new ArrayList<>();
        this.enabled = true;
        this.providerHost = "";
        this.noRegistry = false;
    }

    /**
     * 获取注册中心完整 URL，如 "zookeeper://127.0.0.1:2181"。
     * 若 noRegistry 为 true，则返回 "N/A" 让 Dubbo 跳过注册。
     */
    public String getRegistryUrl() {
        if (noRegistry) {
            return "N/A";
        }
        return registryType + "://" + registryAddress;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegistryType() { return registryType; }
    public void setRegistryType(String registryType) { this.registryType = registryType; }
    public String getRegistryAddress() { return registryAddress; }
    public void setRegistryAddress(String registryAddress) { this.registryAddress = registryAddress; }
    public String getZkGroup() { return zkGroup; }
    public void setZkGroup(String zkGroup) { this.zkGroup = zkGroup; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public List<ProviderNodeModel> getProviders() { return providers; }
    public void setProviders(List<ProviderNodeModel> providers) { this.providers = providers; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProviderHost() { return providerHost; }
    public void setProviderHost(String providerHost) { this.providerHost = providerHost; }
    public boolean isNoRegistry() { return noRegistry; }
    public void setNoRegistry(boolean noRegistry) { this.noRegistry = noRegistry; }
}
