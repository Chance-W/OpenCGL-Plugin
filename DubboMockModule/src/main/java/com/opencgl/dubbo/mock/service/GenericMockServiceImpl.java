package com.opencgl.dubbo.mock.service;

import com.alibaba.fastjson.JSON;
import com.opencgl.dubbo.mock.model.MethodMockConfig;
import com.opencgl.dubbo.mock.model.ProviderNodeModel;
import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericMockServiceImpl implements GenericService {

    private static final Logger log = LoggerFactory.getLogger(GenericMockServiceImpl.class);
    private final ProviderNodeModel providerConfig;

    public GenericMockServiceImpl(ProviderNodeModel providerConfig) {
        this.providerConfig = providerConfig;
    }

    @Override
    public Object $invoke(String method, String[] parameterTypes, Object[] args) throws GenericException {
        // 安全处理参数避免空指针
        parameterTypes = parameterTypes == null ? new String[0] : parameterTypes;
        args = args == null ? new Object[0] : args;
        String msg = String.format("Mock收到调用 | 接口: %s, 方法: %s", providerConfig.getInterfaceName(), method);
        log.info(msg);
        if (providerConfig.getLogListener() != null) {
            providerConfig.getLogListener().accept(msg);
        }
        
        MethodMockConfig matchedRule = null;
        if (providerConfig.getMethods() != null) {
            for (MethodMockConfig rule : providerConfig.getMethods()) {
                if (!rule.isEnabled()) continue;
                if (rule.getMethodName() != null && rule.getMethodName().equals(method)) {
                    matchedRule = rule;
                    break;
                } else if ("*".equals(rule.getMethodName()) || "".equals(rule.getMethodName())) {
                    matchedRule = rule;
                }
            }
        }

        if (matchedRule != null) {
            String matchMsg = String.format("匹配到规则 | 方法: %s, 延迟: %dms", method, matchedRule.getDelayMs());
            log.info(matchMsg);
            if (providerConfig.getLogListener() != null) {
                providerConfig.getLogListener().accept(matchMsg);
            }
            if (matchedRule.getDelayMs() > 0) {
                try {
                    Thread.sleep(matchedRule.getDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                // Dubbo 泛化调用通常期望 Map/List 这种基础对象作为反序列化来源
                return JSON.parse(matchedRule.getResponseJson());
            } catch (Exception e) {
                String errMsg = String.format("Mock 返回体 JSON 解析失败 | 接口: %s, 方法: %s, 错误: %s", providerConfig.getInterfaceName(), method, e.getMessage());
                log.error(errMsg, e);
                if (providerConfig.getLogListener() != null) {
                    providerConfig.getLogListener().accept(errMsg);
                }
                return "Mock JSON Parse Error: " + e.getMessage();
            }
        }
        
        String warnMsg = String.format("未找到该方法对应的 Mock 规则 [%s], 默认返回 null。", method);
        log.warn(warnMsg);
        if (providerConfig.getLogListener() != null) {
            providerConfig.getLogListener().accept(warnMsg);
        }
        return null; // Return null if no matching rule is found
    }
}
