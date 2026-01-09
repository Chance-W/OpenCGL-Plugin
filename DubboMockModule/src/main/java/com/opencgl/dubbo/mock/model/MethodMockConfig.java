package com.opencgl.dubbo.mock.model;

import java.util.UUID;

/**
 * 方法级 Mock 规则配置模型（树的三级叶子节点）
 */
public class MethodMockConfig {

    private String id;

    /** 方法名，例如 "getUserInfo"，为空或者为 * 则可代表 default fallback 匹配所有 */
    private String methodName;

    /** 当匹配到该方法调用时，固定返回的 JSON 响应体报文 */
    private String responseJson;

    /** 是否启用此条规则 */
    private boolean enabled;

    /** 模拟响应延迟耗时 (毫秒) */
    private int delayMs;

    public MethodMockConfig() {
        this.id = UUID.randomUUID().toString();
        this.methodName = "";
        this.responseJson = "{\n  \"message\": \"success\"\n}";
        this.enabled = true;
        this.delayMs = 0;
    }

    public String getDisplayName() {
        if (methodName == null || methodName.trim().isEmpty()) {
            return "* (All Methods)";
        }
        return methodName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getDelayMs() { return delayMs; }
    public void setDelayMs(int delayMs) { this.delayMs = delayMs; }
}
