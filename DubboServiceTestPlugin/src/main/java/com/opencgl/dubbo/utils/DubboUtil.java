package com.opencgl.dubbo.utils;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.SslConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.opencgl.base.hook.TlsConfig;
import com.opencgl.dubbo.model.DubboRequest;
import com.opencgl.dubbo.model.DubboResponse;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DubboUtil {
    private static final Logger log = LoggerFactory.getLogger(DubboUtil.class);
    private static final java.util.Set<ReferenceConfig<GenericService>> activeReferences = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<FrameworkModel> activeFrameworkModels = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private DubboRequest dubboRequest;

    private ApplicationConfig applicationConfig = null;

    private RegistryConfig registryConfig = null;

    private SslConfig sslConfig = null;

    public DubboUtil(DubboRequest dubboRequest) {
        this.dubboRequest = dubboRequest;
        init();
    }


    /**
     * 发送单个请求（向后兼容）
     *
     * @return DubboResponse
     * @throws GenericException
     */
    public DubboResponse sendMessage() throws GenericException {
        return sendSingleRequest();
    }

    /**
     * 批量发送请求（复用连接）
     *
     * @param requests 请求列表
     * @return DubboResponse 包含所有请求的响应
     * @throws GenericException
     */
    public DubboResponse sendBatch(java.util.List<DubboRequest> requests) throws GenericException {
        return sendBatch(requests, null);
    }

    /**
     * 批量发送请求（复用连接，支持实时回调）
     *
     * @param requests 请求列表
     * @param callback 每个请求完成后的回调（可选），用于实时显示
     * @return DubboResponse 包含所有请求的响应
     * @throws GenericException
     */
    public DubboResponse sendBatch(List<DubboRequest> requests,
                                   Consumer<Map<String, Object>> callback) throws GenericException {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("请求列表不能为空");
        }

        DubboRequest firstRequest = requests.getFirst();
        // Phase 18: Removed legacy TLS lock/config usage

        // 批量发送（包括单个请求，保证回调被触发）
        ReferenceConfig<GenericService> reference = null;
        GenericService genericService;
        List<Object> results = new ArrayList<>();

        try {
            // 只初始化一次连接（使用第一个请求的配置）
            try {
                reference = generateGenericServiceReferenceConfig(firstRequest);
                genericService = reference.get();
            }
            catch (Exception connectionError) {
                // 连接初始化失败，为所有请求生成错误结果
                log.error("连接初始化失败", connectionError);
                for (int i = 0; i < requests.size(); i++) {
                    Map<String, Object> errorResult = new java.util.LinkedHashMap<>();
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
                return DubboResponse.builder().object(results).build();
            }

            // 循环发送多次请求，复用同一个连接
            for (int i = 0; i < requests.size(); i++) {
                DubboRequest req = requests.get(i);
                try {
                    setAttachments(req);
                    Object result = invokeGenericService(genericService, req.getMethod(), req.getReqType(), req.getReqJsonMessage());

                    // 构建带序号的结果
                    Map<String, Object> indexedResult = new LinkedHashMap<>();
                    indexedResult.put("requestIndex", i + 1);
                    indexedResult.put("totalRequests", requests.size());
                    indexedResult.put("result", result);
                    results.add(indexedResult);

                    // 实时回调
                    if (callback != null) {
                        callback.accept(indexedResult);
                    }

                }
                catch (Exception e) {
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
        }
        finally {
            // 所有请求完成后才销毁连接
            if (reference != null) {
                release(reference);
            }
            // Phase 19: Destroy isolated model
            this.destroy();
        }

        return DubboResponse.builder().object(results).build();
    }

    /**
     * 流式发送批量请求 (Lazy Generation)
     * 避免一次性生成所有请求导致 OOM
     *
     * @param totalCount       总请求数
     * @param requestGenerator 请求生成器 (Index -> DubboRequest)
     * @param onProgress       进度回调 (Result -> Void)
     */
    public void sendStream(int totalCount, java.util.function.Function<Integer, DubboRequest> requestGenerator, java.util.function.Consumer<Map<String, Object>> onProgress) {
        // 1. 预先初始化 Reference (使用第一个请求的配置，假设所有请求连接配置相同)
        // 我们生成第0个请求来建立连接配置
        DubboRequest firstRequest = requestGenerator.apply(0);
        if (firstRequest == null) return; // Should not happen

        // Phase 18: Removed legacy TLS lock/config usage

        ReferenceConfig<GenericService> reference = null;
        try {
            reference = generateGenericServiceReferenceConfig(firstRequest);
            GenericService genericService = reference.get();

            // 2. 循环执行
            for (int i = 0; i < totalCount; i++) {
                DubboRequest req;
                // 复用第0个请求对象，或者是重新生成
                if (i == 0) {
                    req = firstRequest;
                } else {
                    req = requestGenerator.apply(i);
                }

                if (req == null) continue;

                try {
                    long startTime = System.currentTimeMillis();
                    setAttachments(req);
                    Object result = invokeGenericService(genericService, req.getMethod(), req.getReqType(), req.getReqJsonMessage());
                    long endTime = System.currentTimeMillis();

                    // 构建结果
                    Map<String, Object> indexedResult = new java.util.LinkedHashMap<>();
                    indexedResult.put("requestIndex", i + 1);
                    indexedResult.put("totalRequests", totalCount);
                    indexedResult.put("result", result);
                    indexedResult.put("elapsedTime", endTime - startTime);
                    
                    // 回调
                    if (onProgress != null) {
                        onProgress.accept(indexedResult);
                    }
                } catch (Exception e) {
                    java.util.Map<String, Object> errorResult = new java.util.LinkedHashMap<>();
                    errorResult.put("requestIndex", i + 1);
                    errorResult.put("totalRequests", totalCount);
                    errorResult.put("error", e.getMessage());
                    errorResult.put("errorClass", e.getClass().getName());
                    
                    if (onProgress != null) {
                        onProgress.accept(errorResult);
                    }
                    log.error("请求 " + (i + 1) + " / " + totalCount + " 失败: " + e.getMessage(), e);
                }
            }
        } finally {
            if (reference != null) {
                release(reference);
            }
            // Phase 19: Destroy isolated model
            this.destroy();
        }
    }

    /**
     * 发送单次请求
     */
    private DubboResponse sendSingleRequest() throws GenericException {
        // Phase 18: Removed legacy TLS lock/config usage
        
        ReferenceConfig<GenericService> reference = null;
        GenericService genericService;
        Object o;
        try {
            reference = generateGenericServiceReferenceConfig(dubboRequest);
            genericService = reference.get();
            setAttachments(dubboRequest);
            o = invokeGenericService(genericService, dubboRequest.getMethod(), dubboRequest.getReqType(), dubboRequest.getReqJsonMessage());
        }
        finally {
            // 清理 Reference，但不销毁 ScopeModel（会导致 DubboProtocol destroyed）
            if (reference != null) {
                release(reference);
            }
            // Phase 19: Destroy isolated model
            this.destroy();
        }
        return DubboResponse.builder().object(o).build();
    }

    private ReferenceConfig<GenericService> generateGenericServiceReferenceConfig(DubboRequest request) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>(moduleModel);
        activeReferences.add(reference);
        abortIfCancelled(reference);
        // Provider URL 与注册中心严格互斥：有直连地址时禁止当前引用回退到注册中心。
        DubboReferenceConfigurer.configure(reference, registryConfig, request.getDubboProvidersUrl());
        if (request.getSettingTimeout() != null) {
            reference.setTimeout(request.getSettingTimeout());
        } else {
            reference.setTimeout(10000);
        }
        reference.setRetries(request.getRetries() != null ? request.getRetries() : 0);
        reference.setApplication(applicationConfig);
        reference.setProtocol("dubbo");
        reference.setGeneric(true);
        reference.setCheck(false); // Phase 19: Disable startup check
        if (!StringUtils.isEmpty(request.getVersion())) {
            reference.setVersion(request.getVersion());
        } else {
          //  reference.setVersion("0.0.0");
        }
        // Dubbo RPC 服务分组（与 ZK 命名空间分组独立），留空则不设
        if (!StringUtils.isEmpty(request.getDubboGroup())) {
            reference.setGroup(request.getDubboGroup());
        }
        reference.setInterface(request.getInterfaceName());
        return reference;
    }

    public ReferenceConfig<GenericService> getReference(Class<?> interfaceClass) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>(moduleModel);
        activeReferences.add(reference);
        abortIfCancelled(reference);
        if (!StringUtils.isEmpty(dubboRequest.getDubboProvidersUrl())) {
            if (dubboRequest.getSettingTimeout() != null) {
                reference.setTimeout(dubboRequest.getSettingTimeout());
            } else {
                reference.setTimeout(10000); // Default 10s
            }
            reference.setRetries(dubboRequest.getRetries() != null ? dubboRequest.getRetries() : 0);
        }
        // 与通用泛化调用保持一致：直连和注册中心发现只能二选一。
        DubboReferenceConfigurer.configure(reference, registryConfig, dubboRequest.getDubboProvidersUrl());
        reference.setApplication(applicationConfig);
        reference.setProtocol("dubbo");
        reference.setInterface(interfaceClass);
        reference.setCheck(false); // Phase 19: Disable startup check to allow real connection errors to surface
        return reference;
    }

    private FrameworkModel frameworkModel;
    private ApplicationModel applicationModel;
    private ModuleModel moduleModel;

    private void init() {
        // Phase 19: FrameworkModel Isolation
        this.frameworkModel = new FrameworkModel();
        activeFrameworkModels.add(frameworkModel);
        if (Thread.currentThread().isInterrupted()) {
            destroy();
            throw new IllegalStateException("Dubbo task was cancelled during initialization");
        }
        this.applicationModel = frameworkModel.newApplication();
        this.moduleModel = applicationModel.newModule();

        applicationConfig = new ApplicationConfig();
        applicationConfig.setName("OpenCGLService");
        applicationConfig.setQosEnable(false);
        // Phase 20: 禁用 Dubbo 3 默认的应用级注册与元数据上报，避免旧版 ZK 报错
        applicationConfig.setRegisterMode("interface");
        
        // 显式指定内部 local 元数据类型，并在参数里禁用 metadata-report 支持，防止消费端向远端 ZK 空写
        Map<String, String> appParams = new java.util.HashMap<>();
        appParams.put("metadata-type", "local");
        appParams.put("metadata-report", "false");
        applicationConfig.setParameters(appParams);
        // Bind config to the specific module/application
        applicationModel.getApplicationConfigManager().setApplication(applicationConfig);

        // Phase 18/19: SslConfig in isolated model
        if (dubboRequest.getTlsConfig() != null && dubboRequest.getTlsConfig().isEnabled()) {
            TlsConfig tls = dubboRequest.getTlsConfig();
            this.sslConfig = new SslConfig();
            if (StringUtils.isNotEmpty(tls.getClientCertPath())) {
               this.sslConfig.setClientKeyCertChainPath(tls.getClientCertPath());
            }
            if (StringUtils.isNotEmpty(tls.getClientKeyPath())) {
               this.sslConfig.setClientPrivateKeyPath(tls.getClientKeyPath());
            }
            if (StringUtils.isNotEmpty(tls.getClientKeyPassword())) {
               this.sslConfig.setClientKeyPassword(tls.getClientKeyPassword());
            }
            if (StringUtils.isNotEmpty(tls.getCaCertPath())) {
               this.sslConfig.setClientTrustCertCollectionPath(tls.getCaCertPath());
            }
            
            // Set SSL config on the ApplicationModel's config manager
            applicationModel.getApplicationConfigManager().setSsl(sslConfig);
            log.info("已配置 SslConfig (mTLS enabled) for Isolated ApplicationModel");
        }

        registryConfig = new RegistryConfig();
        registryConfig.setAddress("zookeeper://" + dubboRequest.getDubboRegistryAddr());
        if (dubboRequest.getDubboRegistryGroup() != null && !dubboRequest.getDubboRegistryGroup().trim().isEmpty()) {
            registryConfig.setGroup(dubboRequest.getDubboRegistryGroup().trim());
        }
        // Phase 20: 消费端测试工具不需要把自身注册到 ZK 上脏数据，避免部分 ZK 元数据写入抛错
        registryConfig.setRegister(false);
        if (dubboRequest.getSettingTimeout() != null) {
            registryConfig.setTimeout(dubboRequest.getSettingTimeout());
        } else {
            registryConfig.setTimeout(10000);
        }
        
        // 确保 ZK 元数据报告客户端也使用 persistent 节点且禁用写入
        Map<String, String> regParams = new java.util.HashMap<>();
        regParams.put("metadata-type", "local");
        regParams.put("zookeeper.create.mode", "persistent");
        registryConfig.setParameters(regParams);
        
        // 彻底切断向 ZK 的元数据上报和配置拉取（这两者可能会使用默认 curator 从而避开 mockcurator SPI）
        registryConfig.setUseAsMetadataCenter(false);
        registryConfig.setUseAsConfigCenter(false);
        
        
        // Register RegistryConfig to the isolated application model
        // Note: ModuleConfigManager.addRegistry was not found, so we add to ApplicationConfigManager
        applicationModel.getApplicationConfigManager().addRegistry(registryConfig);
        
        // Start the application model to initialize registry and protocols
        applicationModel.getDeployer().start();
    }

    private void setAttachments(DubboRequest request) {
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            RpcContext.getContext().setAttachments(request.getAttachments());
        }
    }
    
    public void destroy() {
        FrameworkModel modelToDestroy = frameworkModel;
        frameworkModel = null;
        if (modelToDestroy != null) {
            activeFrameworkModels.remove(modelToDestroy);
            try { modelToDestroy.destroy(); } catch (RuntimeException e) { log.warn("销毁 Dubbo FrameworkModel 失败", e); }
        }
    }

    private static void release(ReferenceConfig<GenericService> reference) {
        activeReferences.remove(reference);
        try { reference.destroy(); } catch (RuntimeException e) { log.warn("销毁 Dubbo ReferenceConfig 失败", e); }
    }

    private static void abortIfCancelled(ReferenceConfig<GenericService> reference) {
        if (Thread.currentThread().isInterrupted()) {
            release(reference);
            throw new IllegalStateException("Dubbo task was cancelled while creating a reference");
        }
    }

    public static void shutdownAll() {
        for (ReferenceConfig<GenericService> reference : new java.util.ArrayList<>(activeReferences)) release(reference);
        for (FrameworkModel model : new java.util.ArrayList<>(activeFrameworkModels)) {
            activeFrameworkModels.remove(model);
            try { model.destroy(); } catch (RuntimeException e) { log.warn("销毁 Dubbo FrameworkModel 失败", e); }
        }
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
            // 如果单入参且参数类型为 String，兼容用户未加引号直接输入文本的情形
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
