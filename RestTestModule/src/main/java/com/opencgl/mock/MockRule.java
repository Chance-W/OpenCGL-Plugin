package com.opencgl.mock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock 规则
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockRule {
    
    private String name;
    private String method;  // GET, POST, PUT, DELETE, * (任意)
    private String path;    // 路径或正则
    private boolean regex;  // 是否正则匹配
    private boolean enabled = true;
    
    private int statusCode = 200;
    private String contentType = "application/json";
    private String responseBody = "{}";
    private Map<String, String> responseHeaders = new HashMap<>();
    private long delayMs = 0;  // 响应延迟（毫秒）
    
    /**
     * 快速创建简单规则
     */
    public static MockRule simple(String name, String method, String path, String responseBody) {
        return MockRule.builder()
            .name(name)
            .method(method)
            .path(path)
            .regex(false)
            .enabled(true)
            .statusCode(200)
            .contentType("application/json")
            .responseBody(responseBody)
            .build();
    }
    
    /**
     * 创建错误响应规则
     */
    public static MockRule error(String name, String method, String path, int statusCode, String message) {
        return MockRule.builder()
            .name(name)
            .method(method)
            .path(path)
            .regex(false)
            .enabled(true)
            .statusCode(statusCode)
            .contentType("application/json")
            .responseBody("{\"error\": \"" + message + "\"}")
            .build();
    }
}
