package com.opencgl.nginx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Nginx 服务器配置模型
 */
public class NginxConfig {
    
    // 基础配置
    private String serverName = "localhost";
    private int listenPort = 80;
    private String root = "/var/www/html";
    private String index = "index.html index.htm";
    
    // SSL 配置
    private boolean sslEnabled = false;
    private String sslCertificate = "";
    private String sslCertificateKey = "";
    
    // 反向代理
    private boolean proxyEnabled = false;
    private String proxyLocation = "/";
    private String proxyPass = "http://localhost:8080";
    
    // 负载均衡
    private boolean upstreamEnabled = false;
    private String upstreamName = "backend";
    private LoadBalanceType loadBalanceType = LoadBalanceType.ROUND_ROBIN;
    private List<UpstreamServer> upstreamServers = new ArrayList<>();
    
    // 枚举
    public enum LoadBalanceType {
        ROUND_ROBIN("轮询", ""),
        WEIGHT("权重", ""),
        IP_HASH("IP Hash", "ip_hash;"),
        LEAST_CONN("最少连接", "least_conn;");
        
        private final String displayName;
        private final String directive;
        
        LoadBalanceType(String displayName, String directive) {
            this.displayName = displayName;
            this.directive = directive;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDirective() { return directive; }
    }
    
    public record UpstreamServer(String address, int weight) {}
    
    // Getters and Setters
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    
    public int getListenPort() { return listenPort; }
    public void setListenPort(int listenPort) { this.listenPort = listenPort; }
    
    public String getRoot() { return root; }
    public void setRoot(String root) { this.root = root; }
    
    public String getIndex() { return index; }
    public void setIndex(String index) { this.index = index; }
    
    public boolean isSslEnabled() { return sslEnabled; }
    public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }
    
    public String getSslCertificate() { return sslCertificate; }
    public void setSslCertificate(String sslCertificate) { this.sslCertificate = sslCertificate; }
    
    public String getSslCertificateKey() { return sslCertificateKey; }
    public void setSslCertificateKey(String sslCertificateKey) { this.sslCertificateKey = sslCertificateKey; }
    
    public boolean isProxyEnabled() { return proxyEnabled; }
    public void setProxyEnabled(boolean proxyEnabled) { this.proxyEnabled = proxyEnabled; }
    
    public String getProxyLocation() { return proxyLocation; }
    public void setProxyLocation(String proxyLocation) { this.proxyLocation = proxyLocation; }
    
    public String getProxyPass() { return proxyPass; }
    public void setProxyPass(String proxyPass) { this.proxyPass = proxyPass; }
    
    public boolean isUpstreamEnabled() { return upstreamEnabled; }
    public void setUpstreamEnabled(boolean upstreamEnabled) { this.upstreamEnabled = upstreamEnabled; }
    
    public String getUpstreamName() { return upstreamName; }
    public void setUpstreamName(String upstreamName) { this.upstreamName = upstreamName; }
    
    public LoadBalanceType getLoadBalanceType() { return loadBalanceType; }
    public void setLoadBalanceType(LoadBalanceType loadBalanceType) { this.loadBalanceType = loadBalanceType; }
    
    public List<UpstreamServer> getUpstreamServers() { return upstreamServers; }
    public void setUpstreamServers(List<UpstreamServer> upstreamServers) { this.upstreamServers = upstreamServers; }
}
