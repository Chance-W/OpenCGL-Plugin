package com.opencgl.http.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

/**
 * HTTP响应模型
 */
@Getter
public class HttpResponseModel {
    private int statusCode;
    private String statusMessage;
    private Map<String, String> headers = new HashMap<>();
    private String body;
    private long responseTime; // 毫秒
    private long contentLength;
    private String error;

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getStatusCode() { return statusCode; }
    public String getStatusMessage() { return statusMessage; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public long getResponseTime() { return responseTime; }
    public long getContentLength() { return contentLength; }
    public String getError() { return error; }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
