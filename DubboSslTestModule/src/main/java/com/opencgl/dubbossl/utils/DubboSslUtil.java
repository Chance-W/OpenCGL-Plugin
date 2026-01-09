package com.opencgl.dubbossl.utils;

import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.opencgl.dubbossl.model.DubboSslRequest;
import com.opencgl.dubbossl.model.DubboSslResponse;

/**
 * Dubbo SSL 工具类
 * 使用独立的 ApplicationName 与普通 Dubbo 隔离
 *
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DubboSslUtil {
    private static final Logger log = LoggerFactory.getLogger(DubboSslUtil.class);
    private static final java.util.Set<ReferenceConfig<GenericService>> activeReferences = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    private static final String APP_NAME = "OpenCGLService-SSL";
    
    private DubboSslRequest dubboRequest;
    private ApplicationConfig applicationConfig = null;
    private RegistryConfig registryConfig = null;

    public DubboSslUtil(DubboSslRequest dubboRequest) {
        this.dubboRequest = dubboRequest;
        init();
    }

    /**
     * 发送单个请求（向后兼容）
     * @return DubboSslResponse
     * @throws GenericException
     */
    public DubboSslResponse sendMessage() throws GenericException {
        return sendSingleRequest();
    }
    
    /**
     * 批量发送请求（复用连接）
     * @param requests 请求列表
     * @return DubboSslResponse 包含所有请求的响应
     * @throws GenericException
     */
    public DubboSslResponse sendBatch(java.util.List<DubboSslRequest> requests) throws GenericException {
        return sendBatch(requests, null);
    }

    /**
     * 批量发送请求（复用连接，支持实时回调）
     * @param requests 请求列表
     * @param callback 每个请求完成后的回调（可选），用于实时显示
     * @return DubboSslResponse 包含所有请求的响应
     * @throws GenericException
     */
    public DubboSslResponse sendBatch(java.util.List<DubboSslRequest> requests,
                                       java.util.function.Consumer<java.util.Map<String, Object>> callback) throws GenericException {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("请求列表不能为空");
        }
        
        // 批量发送（包括单个请求，保证回调被触发）
        ReferenceConfig<GenericService> reference = null;
        GenericService genericService;
        java.util.List<Object> results = new java.util.ArrayList<>();
        
        try {
            // 只初始化一次连接（使用第一个请求的配置）
            DubboSslRequest firstRequest = requests.get(0);
            
            try {
                reference = generateGenericServiceReferenceConfig(firstRequest);
                genericService = reference.get();
            } catch (Exception connectionError) {
                // 连接初始化失败，为所有请求生成错误结果
                log.error("连接初始化失败", connectionError);
                for (int i = 0; i < requests.size(); i++) {
                    java.util.Map<String, Object> errorResult = new java.util.LinkedHashMap<>();
                    errorResult.put("requestIndex", i + 1);
                    errorResult.put("totalRequests", requests.size());
                    errorResult.put("error", "连接失败: " + connectionError.getMessage());
                    errorResult.put("errorClass", connectionError.getClass().getName());
                    results.add(errorResult);
                    
                    // 实时回调错误结果
                    if (callback != null) {
                        callback.accept(errorResult);
                    }
                }
                // 返回包含所有错误的响应
                return DubboSslResponse.builder().object(results).build();
            }
            
            // 循环发送多次请求，复用同一个连接
            for (int i = 0; i < requests.size(); i++) {
                DubboSslRequest req = requests.get(i);
                try {
                    Object result = invokeGenericService(genericService, req.getMethod(), req.getReqType(), req.getReqJsonMessage());
                    
                    // 构建带序号的结果
                    java.util.Map<String, Object> indexedResult = new java.util.LinkedHashMap<>();
                    indexedResult.put("requestIndex", i + 1);
                    indexedResult.put("totalRequests", requests.size());
                    indexedResult.put("result", result);
                    results.add(indexedResult);
                    
                    // 实时回调
                    if (callback != null) {
                        callback.accept(indexedResult);
                    }
                    
                } catch (Exception e) {
                    // 单次请求失败，记录错误但继续执行
                    java.util.Map<String, Object> errorResult = new java.util.LinkedHashMap<>();
                    errorResult.put("requestIndex", i + 1);
                    errorResult.put("totalRequests", requests.size());
                    errorResult.put("error", e.getMessage());
                    errorResult.put("errorClass", e.getClass().getName());
                    results.add(errorResult);
                    
                    // 实时回调错误结果
                    if (callback != null) {
                        callback.accept(errorResult);
                    }
                    
                    log.error("请求 " + (i + 1) + " / " + requests.size() + " 失败: " + e.getMessage(), e);
                }
            }
        } finally {
            // 所有请求完成后才销毁连接
            if (reference != null) {
                release(reference);
            }
            // 请求结束后清除 SSL 系统属性
            clearSslProperties();
        }
        
        return DubboSslResponse.builder().object(results).build();
    }
    
    /**
     * 发送单次请求
     */
    private DubboSslResponse sendSingleRequest() throws GenericException {
        ReferenceConfig<GenericService> reference = null;
        GenericService genericService;
        Object o;
        try {
            reference = generateGenericServiceReferenceConfig(dubboRequest);
            genericService = reference.get();
            o = invokeGenericService(genericService, dubboRequest.getMethod(), dubboRequest.getReqType(), dubboRequest.getReqJsonMessage());
        } finally {
            if (reference != null) {
                release(reference);
            }
            // 请求结束后清除 SSL 系统属性
            clearSslProperties();
        }
        return DubboSslResponse.builder().object(o).build();
    }

    private void clearSslProperties() {
        System.clearProperty("dubbo.ssl.client-key-cert-chain-path");
        System.clearProperty("dubbo.ssl.client-private-key-path");
        System.clearProperty("dubbo.ssl.client-key-password");
        System.clearProperty("dubbo.ssl.client-trust-cert-collection-path");
        log.debug("已清除 Dubbo SSL 系统属性");
    }

    private ReferenceConfig<GenericService> generateGenericServiceReferenceConfig(DubboSslRequest request) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        activeReferences.add(reference);
        if (Thread.currentThread().isInterrupted()) {
            release(reference);
            throw new IllegalStateException("Dubbo SSL task was cancelled while creating a reference");
        }
        if (!StringUtils.isEmpty(request.getDubboProvidersUrl())) {
            reference.setUrl(request.getDubboProvidersUrl());
        }
        reference.setRegistry(registryConfig);
        reference.setTimeout(1000000000);
        reference.setRetries(-1);
        reference.setApplication(applicationConfig);
        reference.setProtocol("dubbo");
        reference.setGeneric(true);
        reference.setInterface(request.getInterfaceName());
        return reference;
    }

    private static void release(ReferenceConfig<GenericService> reference) {
        activeReferences.remove(reference);
        try { reference.destroy(); } catch (RuntimeException e) { log.warn("销毁 Dubbo SSL ReferenceConfig 失败", e); }
    }

    public static void shutdownAll() {
        for (ReferenceConfig<GenericService> reference : new java.util.ArrayList<>(activeReferences)) release(reference);
        clearSslPropertiesStatic();
    }

    private static void clearSslPropertiesStatic() {
        System.clearProperty("dubbo.ssl.client-key-cert-chain-path");
        System.clearProperty("dubbo.ssl.client-private-key-path");
        System.clearProperty("dubbo.ssl.client-key-password");
        System.clearProperty("dubbo.ssl.client-trust-cert-collection-path");
    }

    private void init() {
        applicationConfig = new ApplicationConfig();
        applicationConfig.setName(APP_NAME);
        applicationConfig.setQosEnable(false);
        
        // 配置 SSL (从界面配置的路径)
        configureSsl();

        registryConfig = new RegistryConfig();
        registryConfig.setAddress("zookeeper://" + dubboRequest.getDubboRegistryAddr());
        registryConfig.setGroup(dubboRequest.getDubboRegistryGroup());
        registryConfig.setTimeout(10000);
    }
    
    private void configureSsl() {
        // 使用界面配置的证书路径
        if (!StringUtils.isEmpty(dubboRequest.getClientCertPath())) {
            System.setProperty("dubbo.ssl.client-key-cert-chain-path", dubboRequest.getClientCertPath());
            log.info("设置客户端证书链: {}", dubboRequest.getClientCertPath());
        }
        if (!StringUtils.isEmpty(dubboRequest.getClientKeyPath())) {
            System.setProperty("dubbo.ssl.client-private-key-path", dubboRequest.getClientKeyPath());
            log.info("设置客户端私钥: {}", dubboRequest.getClientKeyPath());
        }
        if (!StringUtils.isEmpty(dubboRequest.getClientKeyPassword())) {
            System.setProperty("dubbo.ssl.client-key-password", dubboRequest.getClientKeyPassword());
        }
        if (!StringUtils.isEmpty(dubboRequest.getCaCertPath())) {
            System.setProperty("dubbo.ssl.client-trust-cert-collection-path", dubboRequest.getCaCertPath());
            log.info("设置信任CA证书: {}", dubboRequest.getCaCertPath());
        }
        log.info("已设置 Dubbo SSL 系统属性 (PEM格式)");
    }

    /**
     * 统一泛化服务调用与入参解析支持基础类型及多参数
     */
    public static Object invokeGenericService(GenericService genericService, String method, String reqType, String reqJsonMessage) {
        if (StringUtils.isEmpty(reqType) || StringUtils.isEmpty(reqJsonMessage)) {
            return genericService.$invoke(method, new String[]{}, new Object[]{});
        }
        String[] types = reqType.split(",");
        Object parsedContent;
        try {
            parsedContent = JSON.parse(reqJsonMessage);
        } catch (Exception e) {
            if (types.length == 1 && ("java.lang.String".equals(types[0].trim()) || "String".equalsIgnoreCase(types[0].trim()))) {
                parsedContent = reqJsonMessage;
            } else {
                throw e;
            }
        }

        Object[] args;
        if (types.length > 1) {
            if (parsedContent instanceof com.alibaba.fastjson.JSONArray) {
                args = ((com.alibaba.fastjson.JSONArray) parsedContent).toArray();
                if (args.length != types.length) {
                    throw new IllegalArgumentException("多参数接口调用时，传入数组参数个数(" + args.length + ")与方法入参个数(" + types.length + ")不匹配！");
                }
            } else {
                throw new IllegalArgumentException("当方法具有多个参数(" + types.length + "个)时，入参须为各对应参数对象组成的 JSON 数组格式形如 [arg1, arg2, ...]");
            }
        } else {
            args = new Object[]{parsedContent};
        }
        return genericService.$invoke(method, types, args);
    }
}
