package com.opencgl.nginx.service;

import com.opencgl.nginx.model.NginxConfig;
import com.opencgl.nginx.model.NginxConfig.*;

/**
 * Nginx 配置生成服务
 */
public class NginxConfigGenerator {
    
    /**
     * 生成 nginx.conf 内容
     */
    public String generate(NginxConfig config) {
        StringBuilder sb = new StringBuilder();
        
        // Upstream 配置
        if (config.isUpstreamEnabled() && !config.getUpstreamServers().isEmpty()) {
            sb.append(generateUpstream(config));
            sb.append("\n");
        }
        
        // Server 块
        sb.append("server {\n");
        
        // 监听端口
        if (config.isSslEnabled()) {
            sb.append("    listen ").append(config.getListenPort()).append(" ssl;\n");
        } else {
            sb.append("    listen ").append(config.getListenPort()).append(";\n");
        }
        
        // 服务器名
        sb.append("    server_name ").append(config.getServerName()).append(";\n");
        sb.append("\n");
        
        // SSL 配置
        if (config.isSslEnabled()) {
            sb.append(generateSslConfig(config));
            sb.append("\n");
        }
        
        // 反向代理或静态文件
        if (config.isProxyEnabled()) {
            sb.append(generateProxyConfig(config));
        } else {
            sb.append(generateStaticConfig(config));
        }
        
        sb.append("}\n");
        
        return sb.toString();
    }
    
    private String generateUpstream(NginxConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("upstream ").append(config.getUpstreamName()).append(" {\n");
        
        // 负载均衡策略
        String directive = config.getLoadBalanceType().getDirective();
        if (!directive.isEmpty()) {
            sb.append("    ").append(directive).append("\n");
        }
        
        // 服务器列表
        for (UpstreamServer server : config.getUpstreamServers()) {
            sb.append("    server ").append(server.address());
            if (server.weight() > 1) {
                sb.append(" weight=").append(server.weight());
            }
            sb.append(";\n");
        }
        
        sb.append("}\n");
        return sb.toString();
    }
    
    private String generateSslConfig(NginxConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("    # SSL 配置\n");
        sb.append("    ssl_certificate ").append(config.getSslCertificate()).append(";\n");
        sb.append("    ssl_certificate_key ").append(config.getSslCertificateKey()).append(";\n");
        sb.append("    ssl_protocols TLSv1.2 TLSv1.3;\n");
        sb.append("    ssl_ciphers HIGH:!aNULL:!MD5;\n");
        sb.append("    ssl_prefer_server_ciphers on;\n");
        return sb.toString();
    }
    
    private String generateProxyConfig(NginxConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("    # 反向代理配置\n");
        sb.append("    location ").append(config.getProxyLocation()).append(" {\n");
        
        String target = config.isUpstreamEnabled() 
            ? "http://" + config.getUpstreamName()
            : config.getProxyPass();
            
        sb.append("        proxy_pass ").append(target).append(";\n");
        sb.append("        proxy_set_header Host $host;\n");
        sb.append("        proxy_set_header X-Real-IP $remote_addr;\n");
        sb.append("        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n");
        sb.append("        proxy_set_header X-Forwarded-Proto $scheme;\n");
        sb.append("\n");
        sb.append("        # WebSocket 支持\n");
        sb.append("        proxy_http_version 1.1;\n");
        sb.append("        proxy_set_header Upgrade $http_upgrade;\n");
        sb.append("        proxy_set_header Connection \"upgrade\";\n");
        sb.append("    }\n");
        return sb.toString();
    }
    
    private String generateStaticConfig(NginxConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("    # 静态文件配置\n");
        sb.append("    root ").append(config.getRoot()).append(";\n");
        sb.append("    index ").append(config.getIndex()).append(";\n");
        sb.append("\n");
        sb.append("    location / {\n");
        sb.append("        try_files $uri $uri/ =404;\n");
        sb.append("    }\n");
        sb.append("\n");
        sb.append("    # 静态资源缓存\n");
        sb.append("    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {\n");
        sb.append("        expires 30d;\n");
        sb.append("        add_header Cache-Control \"public, no-transform\";\n");
        sb.append("    }\n");
        return sb.toString();
    }
}
