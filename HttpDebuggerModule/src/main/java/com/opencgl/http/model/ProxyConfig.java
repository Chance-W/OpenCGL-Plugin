package com.opencgl.http.model;

/**
 * 代理配置模型
 */
public class ProxyConfig {
    private boolean enabled;
    private String host;
    private int port;
    private String type; // HTTP or SOCKS, default HTTP

    public ProxyConfig() {
        this.enabled = false;
        this.host = "127.0.0.1";
        this.port = 8888;
        this.type = "HTTP";
    }

    public ProxyConfig(boolean enabled, String host, int port) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.type = "HTTP";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
