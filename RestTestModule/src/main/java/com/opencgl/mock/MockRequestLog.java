package com.opencgl.mock;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Mock 请求日志
 */
@Data
public class MockRequestLog {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    private LocalDateTime timestamp;
    private String method;
    private String path;
    private String query;
    private String body;
    private boolean matched;
    private String matchedRule;
    
    public MockRequestLog(String method, String path, String query, String body) {
        this.timestamp = LocalDateTime.now();
        this.method = method;
        this.path = path;
        this.query = query;
        this.body = body;
    }
    
    public String getDisplayTime() {
        return timestamp.format(FORMATTER);
    }
    
    public String getDisplayPath() {
        if (query != null && !query.isEmpty()) {
            return path + "?" + query;
        }
        return path;
    }
}
