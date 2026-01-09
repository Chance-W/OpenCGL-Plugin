package com.opencgl.dubbo.mock.service;

import com.opencgl.dubbo.mock.model.ProviderNodeModel;
import com.opencgl.dubbo.mock.model.RegistryNodeModel;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DubboMockService {
    private static final Logger log = LoggerFactory.getLogger(DubboMockService.class);
    
    // key: registryId + providerId
    private static final Map<String, ServiceConfig<org.apache.dubbo.rpc.service.GenericService>> runningServices = new ConcurrentHashMap<>();

    private RegistryNodeModel currentRegistryNode;

    // Phase 21: Engine 模式下，保存所有已暴露的 ServerConfig ，以便批量注销
    private final Map<String, ServiceConfig<org.apache.dubbo.rpc.service.GenericService>> activeServices = new ConcurrentHashMap<>();

    public static void syncRunningState(java.util.List<RegistryNodeModel> registries) {
        for (RegistryNodeModel r : registries) {
            for (ProviderNodeModel p : r.getProviders()) {
                String key = r.getId() + "_" + p.getId();
                p.setRunning(runningServices.containsKey(key));
            }
        }
    }

    public RegistryNodeModel getCurrentRegistryNode() {
        return currentRegistryNode;
    }

    /**
     * Engine 模式专用启动入口：接收完整的 registry 配置，一次性循环抛出其下属所有的 Provider
     */
    public void startProvider(RegistryNodeModel registry) throws Exception {
        this.currentRegistryNode = registry;
        log.info("开始暴露 Registry: {}", registry.getName());
        
        if (registry.getProviders() == null || registry.getProviders().isEmpty()) {
            log.warn("该 Registry 下没有配置任何 Provider，跳过启动");
            return;
        }

        // 统一提取基础配置
        ApplicationConfig application = new ApplicationConfig();
        application.setName(registry.getApplicationName() != null && !registry.getApplicationName().isEmpty() ? registry.getApplicationName() : "dubbo-mock-provider");
        
        RegistryConfig registryConfig = new RegistryConfig();
        // 必须使用由 registryType 拼接的整 URL（如 zookeeper://127.0.0.1:2181），否则 Dubbo 会默认它是 dubbo:// 注册中心！
        registryConfig.setAddress(registry.getRegistryUrl());
        registryConfig.setClient("mockcurator"); // 使用自定义兼容旧 ZK 的 Curator 客户端
        if (registry.isNoRegistry()) {
            registryConfig.setRegister(false);
        }
        if (registry.getZkGroup() != null && !registry.getZkGroup().trim().isEmpty()) {
            registryConfig.setGroup(registry.getZkGroup().trim());
        }

        
        // Phase 20: 禁用 Provider 向 ZK 写入元数据，以兼容不支持 Metadata 的旧版 Zookeeper
        Map<String, String> parameters = new HashMap<>();
        parameters.put("metadata-type", "local"); // 阻止向远端 registry 写 metadata
        parameters.put("zookeeper.create.mode", "persistent");
        // Phase 22: 最关键防御：Dubbo 3 默认开启双注册（应用级+接口级），应用级服务发现向老ZK写时会触发 CreateMode.CONTAINER 导致 Unimplemented!
        // 作为 Mock 引擎，仅需传统的 interface (接口级) 注册即可让全网泛化调用。彻底关闭应用级发现机制规避老 ZK 崩溃。
        registryConfig.setRegisterMode("interface");
        registryConfig.setParameters(parameters);
        
        // 彻底切断向 ZK 的元数据上报和配置拉取
        registryConfig.setUseAsMetadataCenter(false);
        registryConfig.setUseAsConfigCenter(false);
        
        // 使用自定义 mockcurator SPI，强制以 PERSISTENT 节点替代 CONTAINER 节点，兼容 ZK 3.4.x
        registryConfig.setClient("mockcurator");

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName(registry.getProtocol() != null && !registry.getProtocol().isEmpty() ? registry.getProtocol() : "dubbo");
        protocol.setPort(registry.getPort());
        
        // 批量暴露
        for (ProviderNodeModel provider : registry.getProviders()) {
            try {
                ServiceConfig<org.apache.dubbo.rpc.service.GenericService> service = new ServiceConfig<>();
                service.setApplication(application);
                service.setRegistry(registryConfig);
                service.setProtocol(protocol);
                // Phase 20: 禁用 InJvm 本地短路暴露，防止同进程内的 Dubbo 测试工具直接走本地代理导致版本错乱
                service.setScope("remote");
                service.setInterface(provider.getInterfaceName());
                
                // version 和 group
                if (provider.getVersion() != null && !provider.getVersion().trim().isEmpty()) {
                    service.setVersion(provider.getVersion().trim());
                }
                if (provider.getGroup() != null && !provider.getGroup().trim().isEmpty()) {
                    service.setGroup(provider.getGroup().trim());
                }

                // 泛化调用配置
                service.setGeneric("true");
                service.setRef(new GenericMockServiceImpl(provider));

                // 导出服务
                service.export();
                activeServices.put(provider.getId(), service);
                log.info("Mock Provider 导出成功: {} (Version: {}, Group: {})", provider.getInterfaceName(), provider.getVersion(), provider.getGroup());

                // Phase 22: 主动补齐 ZK 下面的 routers 和 configurators 节点。
                // 原因是 Dubbo 3.x Consumer 在订阅时，如果发现这些节点不存在，会使用默认的 zk 客户端（含 CONTAINER 特性）去强行创建，
                // 此时在旧版 ZK 上就会崩溃报错 KeeperErrorCode = Unimplemented。
                // 由于我们这里 mock 提供者不会去创建 routers，所以我们帮它创建好 PERSISTENT 节点，断绝 Consumer 的念想。
                org.apache.curator.framework.CuratorFramework curator = null;
                try {
                    curator = org.apache.curator.framework.CuratorFrameworkFactory.newClient(
                            registry.getRegistryUrl().replace("zookeeper://", "").replace("mockzookeeper://", "").split("\\?")[0],
                            new org.apache.curator.retry.ExponentialBackoffRetry(1000, 3)
                    );
                    curator.start();
                    curator.blockUntilConnected();
                    
                    String basePath = "/dubbo/" + provider.getInterfaceName();
                    String[] subNodes = {"/routers", "/configurators", "/providers", "/consumers"};
                    for (String sub : subNodes) {
                        try {
                            curator.create().creatingParentsIfNeeded().withMode(org.apache.zookeeper.CreateMode.PERSISTENT).forPath(basePath + sub);
                        } catch (org.apache.zookeeper.KeeperException.NodeExistsException ignored) {
                        } catch (Exception ex) {
                            log.warn("Mock 预创建节点失败: " + basePath + sub, ex);
                        }
                    }
                } catch (Exception ex) {
                    log.error("Mock 预创建环境节点时发生错误", ex);
                } finally {
                    if (curator != null) {
                        try { curator.close(); } catch (RuntimeException closeError) { log.warn("关闭 Curator 失败", closeError); }
                    }
                }

                if (provider.getLogListener() != null) {
                    provider.getLogListener().accept("Mock Provider 导出成功: " + provider.getInterfaceName());
                }
            } catch (Exception e) {
                 log.error("暴露 Provider 失败: " + provider.getInterfaceName(), e);
                 if (provider.getLogListener() != null) {
                     provider.getLogListener().accept("Mock Provider 导出失败: " + e.getMessage());
                 }
                 throw e;
            }
        }
    }

    /**
     * 兼容旧版的单 Provider 启动模式，仍保留但重定向至批量接口
     */
    public void startProvider(RegistryNodeModel registry, ProviderNodeModel provider) throws Exception {
       if (provider != null && (registry.getProviders() == null || !registry.getProviders().contains(provider))) {
           if (registry.getProviders() == null) {
               registry.setProviders(new java.util.ArrayList<>());
           }
           registry.getProviders().add(provider);
       }
       startProvider(registry);
    }

    /**
     * 停用所有服务
     */
    public void stopProvider(RegistryNodeModel registry) {
        log.info("开始取消暴露 Registry: {}", registry.getName());
        for (Map.Entry<String, ServiceConfig<org.apache.dubbo.rpc.service.GenericService>> entry : activeServices.entrySet()) {
            try {
                ServiceConfig<org.apache.dubbo.rpc.service.GenericService> service = entry.getValue();
                service.unexport();
                log.info("取消暴露 Provider: {}", service.getInterface());
            } catch (Exception e) {
                log.error("取消暴漏 Provider 失败: " + entry.getKey(), e);
            }
        }
        activeServices.clear();

        try {
            DubboBootstrap.getInstance().destroy();
            log.info("DubboBootstrap 实例且资源已被销毁清理。");
        } catch (Exception e) {
            log.warn("销毁 DubboBootstrap 实例时遇到问题: {}", e.getMessage());
        }
    }

    public void close() {
        for (Map.Entry<String, ServiceConfig<org.apache.dubbo.rpc.service.GenericService>> entry : new java.util.ArrayList<>(runningServices.entrySet())) {
            try { entry.getValue().unexport(); } catch (RuntimeException e) { log.error("取消暴露 Provider 失败: " + entry.getKey(), e); }
        }
        runningServices.clear();
        for (Map.Entry<String, ServiceConfig<org.apache.dubbo.rpc.service.GenericService>> entry : new java.util.ArrayList<>(activeServices.entrySet())) {
            try { entry.getValue().unexport(); } catch (RuntimeException e) { log.error("取消暴露 Provider 失败: " + entry.getKey(), e); }
        }
        activeServices.clear();
        try { DubboBootstrap.getInstance().destroy(); } catch (RuntimeException e) { log.warn("销毁 DubboBootstrap 失败", e); }
    }
}
