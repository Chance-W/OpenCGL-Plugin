package com.opencgl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 请求历史记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestHistory {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private LocalDateTime timestamp;
    private String method;
    private String url;
    private Integer statusCode;
    private Long responseTimeMs;
    private String requestBody;
    private String responseBody;
    private String headers;
    
    /**
     * 获取显示标题
     */
    public String getDisplayTitle() {
        String time = timestamp != null ? timestamp.format(FORMATTER) : "";
        String shortUrl = url;
        if (url != null && url.length() > 50) {
            shortUrl = url.substring(0, 50) + "...";
        }
        return String.format("[%s] %s %s", time, method, shortUrl);
    }
    
    /**
     * 获取状态显示
     */
    public String getStatusDisplay() {
        if (statusCode == null) {
            return "Error";
        }
        return statusCode + " (" + responseTimeMs + "ms)";
    }
    
    /**
     * 是否成功请求
     */
    public boolean isSuccess() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}
