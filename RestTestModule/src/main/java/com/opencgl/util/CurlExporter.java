package com.opencgl.util;

import com.opencgl.model.RestRequest;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

/**
 * cURL命令导出工具
 * 将REST请求转换为cURL命令
 */
public class CurlExporter {
    
    /**
     * 将请求转换为cURL命令
     */
    public static String export(RestRequest request) {
        StringBuilder curl = new StringBuilder("curl");
        
        // 方法
        if (!"GET".equalsIgnoreCase(request.getRequestMethod())) {
            curl.append(" -X ").append(request.getRequestMethod());
        }
        
        // URL
        curl.append(" '").append(escapeShell(request.getRequestUrl())).append("'");
        
        // Headers
        Map<String, String> headers = request.getRequestHeaderMap();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                curl.append(" \\\n  -H '")
                    .append(escapeShell(entry.getKey()))
                    .append(": ")
                    .append(escapeShell(entry.getValue()))
                    .append("'");
            }
        }
        
        // Content-Type (如果未在headers中指定)
        if (StringUtils.isNotBlank(request.getMediaType()) && 
            (headers == null || !headers.containsKey("Content-Type"))) {
            curl.append(" \\\n  -H 'Content-Type: ")
                .append(escapeShell(request.getMediaType()))
                .append("'");
        }
        
        // Body
        if (StringUtils.isNotBlank(request.getRequestMessage())) {
            curl.append(" \\\n  -d '")
                .append(escapeShell(request.getRequestMessage()))
                .append("'");
        }
        
        return curl.toString();
    }
    
    /**
     * 从基本参数生成cURL命令
     */
    public static String export(String method, String url, Map<String, String> headers, 
                                String contentType, String body) {
        StringBuilder curl = new StringBuilder("curl");
        
        // 方法
        if (!"GET".equalsIgnoreCase(method)) {
            curl.append(" -X ").append(method);
        }
        
        // URL
        curl.append(" '").append(escapeShell(url)).append("'");
        
        // Headers
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                curl.append(" \\\n  -H '")
                    .append(escapeShell(entry.getKey()))
                    .append(": ")
                    .append(escapeShell(entry.getValue()))
                    .append("'");
            }
        }
        
        // Content-Type
        if (StringUtils.isNotBlank(contentType) && 
            (headers == null || !headers.containsKey("Content-Type"))) {
            curl.append(" \\\n  -H 'Content-Type: ")
                .append(escapeShell(contentType))
                .append("'");
        }
        
        // Body
        if (StringUtils.isNotBlank(body)) {
            curl.append(" \\\n  -d '")
                .append(escapeShell(body))
                .append("'");
        }
        
        return curl.toString();
    }
    
    /**
     * 生成单行cURL命令（用于复制）
     */
    public static String exportOneline(String method, String url, Map<String, String> headers,
                                       String contentType, String body) {
        StringBuilder curl = new StringBuilder("curl");
        
        if (!"GET".equalsIgnoreCase(method)) {
            curl.append(" -X ").append(method);
        }
        
        curl.append(" '").append(escapeShell(url)).append("'");
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                curl.append(" -H '")
                    .append(escapeShell(entry.getKey()))
                    .append(": ")
                    .append(escapeShell(entry.getValue()))
                    .append("'");
            }
        }
        
        if (StringUtils.isNotBlank(contentType) && 
            (headers == null || !headers.containsKey("Content-Type"))) {
            curl.append(" -H 'Content-Type: ").append(escapeShell(contentType)).append("'");
        }
        
        if (StringUtils.isNotBlank(body)) {
            curl.append(" -d '").append(escapeShell(body)).append("'");
        }
        
        return curl.toString();
    }
    
    /**
     * Shell字符转义
     */
    private static String escapeShell(String input) {
        if (input == null) return "";
        // 转义单引号
        return input.replace("'", "'\\''");
    }
}
