package com.opencgl.nacos.service;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Nacos 服务
 * 支持配置管理和服务发现
 *
 * @author OpenCGL
 */
public class NacosService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(NacosService.class);

    private ConfigService configService;
    private NamingService namingService;
    private String serverAddr;

    /**
     * 连接到 Nacos
     */
    public void connect(String serverAddr, String namespace) throws NacosException {
        close();
        this.serverAddr = serverAddr;
        
        Properties props = new Properties();
        props.put("serverAddr", serverAddr);
        if (namespace != null && !namespace.isEmpty()) {
            props.put("namespace", namespace);
        }
        
        configService = NacosFactory.createConfigService(props);
        namingService = NacosFactory.createNamingService(props);
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        try {
            if (namingService != null) {
                namingService.getServerStatus();
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Nacos 连接测试失败", e);
            return false;
        }
    }

    // ==================== 配置管理 ====================

    /**
     * 获取配置
     */
    public String getConfig(String dataId, String group) throws NacosException {
        if (configService == null) throw new IllegalStateException("未连接");
        return configService.getConfig(dataId, group, 5000);
    }

    /**
     * 发布配置
     */
    public boolean publishConfig(String dataId, String group, String content) throws NacosException {
        if (configService == null) throw new IllegalStateException("未连接");
        return configService.publishConfig(dataId, group, content);
    }

    /**
     * 删除配置
     */
    public boolean removeConfig(String dataId, String group) throws NacosException {
        if (configService == null) throw new IllegalStateException("未连接");
        return configService.removeConfig(dataId, group);
    }

    /**
     * 监听配置变化
     */
    public void addConfigListener(String dataId, String group, Consumer<String> callback) throws NacosException {
        if (configService == null) throw new IllegalStateException("未连接");
        configService.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                callback.accept(configInfo);
            }
        });
    }

    // ==================== 服务发现 ====================

    /**
     * 获取服务列表
     */
    public List<ServiceInfo> listServices(String groupName, int pageNo, int pageSize) throws NacosException {
        if (namingService == null) throw new IllegalStateException("未连接");
        var listView = namingService.getServicesOfServer(pageNo, pageSize, groupName);
        
        List<ServiceInfo> result = new ArrayList<>();
        for (String serviceName : listView.getData()) {
            ServiceInfo info = new ServiceInfo();
            info.setName(serviceName);
            info.setGroupName(groupName);
            result.add(info);
        }
        return result;
    }

    /**
     * 获取服务实例
     */
    public List<Instance> getInstances(String serviceName, String groupName) throws NacosException {
        if (namingService == null) throw new IllegalStateException("未连接");
        return namingService.getAllInstances(serviceName, groupName);
    }

    /**
     * 注册服务实例
     */
    public void registerInstance(String serviceName, String groupName, String ip, int port, 
                                 Map<String, String> metadata) throws NacosException {
        if (namingService == null) throw new IllegalStateException("未连接");
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setHealthy(true);
        instance.setWeight(1.0);
        if (metadata != null) {
            instance.setMetadata(metadata);
        }
        namingService.registerInstance(serviceName, groupName, instance);
    }

    /**
     * 注销服务实例
     */
    public void deregisterInstance(String serviceName, String groupName, String ip, int port) throws NacosException {
        if (namingService == null) throw new IllegalStateException("未连接");
        namingService.deregisterInstance(serviceName, groupName, ip, port);
    }

    /**
     * 获取服务状态
     */
    public String getServerStatus() {
        if (namingService == null) return "未连接";
        return namingService.getServerStatus();
    }

    @Override
    public void close() {
        ConfigService configToClose = configService;
        NamingService namingToClose = namingService;
        configService = null;
        namingService = null;

        if (configToClose != null) {
        try {
                configToClose.shutDown();
        } catch (Exception e) {
                logger.error("关闭 Nacos config service 失败", e);
            }
        }
        if (namingToClose != null) {
            try {
                namingToClose.shutDown();
            } catch (Exception e) {
                logger.error("关闭 Nacos naming service 失败", e);
            }
        }
    }
}
