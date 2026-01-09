package com.opencgl.http.model;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

/**
 * HTTP请求模型
 */
@Getter
public class HttpRequestModel {
    private String method = "GET";
    private String url = "";
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> params = new HashMap<>();
    private String body = "";
    private String bodyType = "JSON"; // JSON, FORM, XML, RAW

    // mTLS
    private String clientCertPath;
    private String clientCertPass;
    private String serverCertPath;
    private String serverCertPass;

    public void setMethod(String method) {
        this.method = method;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setBodyType(String bodyType) {
        this.bodyType = bodyType;
    }
    private int timeout = 30; // seconds
    private boolean sslVerification = true;
    private boolean followRedirects = true;

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setSslVerification(boolean sslVerification) {
        this.sslVerification = sslVerification;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public int getTimeout() { return timeout; }
    public boolean isFollowRedirects() { return followRedirects; }
    public boolean isSslVerification() { return sslVerification; }
    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getParams() { return params; }
    public String getBody() { return body; }
    public String getBodyType() { return bodyType; }
    public String getClientCertPath() { return clientCertPath; }
    public String getClientCertPass() { return clientCertPass; }
    public String getServerCertPath() { return serverCertPath; }
    public String getServerCertPass() { return serverCertPass; }

    public void setClientCertPath(String clientCertPath) { this.clientCertPath = clientCertPath; }

    public void setClientCertPass(String clientCertPass) { this.clientCertPass = clientCertPass; }

    public void setServerCertPath(String serverCertPath) { this.serverCertPath = serverCertPath; }

    public void setServerCertPass(String serverCertPass) { this.serverCertPass = serverCertPass; }
}
