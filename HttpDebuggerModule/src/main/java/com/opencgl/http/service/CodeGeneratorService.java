package com.opencgl.http.service;

import com.opencgl.http.model.HttpRequestModel;
import com.opencgl.http.model.ProxyConfig;

import java.util.Map;

/**
 * 代码生成服务
 * 支持生成 cURL, Java HttpClient, Python Requests 代码片段
 */
public class CodeGeneratorService {

    /**
     * 生成 cURL 命令
     */
    public String generateCurl(HttpRequestModel request, ProxyConfig proxy) {
        StringBuilder sb = new StringBuilder("curl");
        sb.append(" -X ").append(request.getMethod());
        sb.append(" '").append(request.getUrl()).append("'");
        
        // Proxy
        if (proxy != null && proxy.isEnabled()) {
            sb.append(" \\\n  -x '").append(proxy.getHost()).append(":").append(proxy.getPort()).append("'");
        }
        
        // Headers
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            sb.append(" \\\n  -H '").append(entry.getKey()).append(": ").append(entry.getValue()).append("'");
        }
        
        // Body
        if (request.getBody() != null && !request.getBody().isEmpty()) {
            // Escape single quotes for shell
            String body = request.getBody().replace("'", "'\\''");
            sb.append(" \\\n  -d '").append(body).append("'");
        }
        
        return sb.toString();
    }

    /**
     * 生成 Java 11 HttpClient 代码
     */
    public String generateJava(HttpRequestModel request, ProxyConfig proxy) {
        StringBuilder sb = new StringBuilder();
        sb.append("import java.net.URI;\n");
        sb.append("import java.net.http.HttpClient;\n");
        sb.append("import java.net.http.HttpRequest;\n");
        sb.append("import java.net.http.HttpResponse;\n");
        
        if (proxy != null && proxy.isEnabled()) {
            sb.append("import java.net.InetSocketAddress;\n");
            sb.append("import java.net.ProxySelector;\n");
        }
        sb.append("\n");
        
        if (proxy != null && proxy.isEnabled()) {
            sb.append("HttpClient client = HttpClient.newBuilder()\n");
            sb.append("    .proxy(ProxySelector.of(new InetSocketAddress(\"").append(proxy.getHost()).append("\", ").append(proxy.getPort()).append(")))\n");
            sb.append("    .build();\n");
        } else {
            sb.append("HttpClient client = HttpClient.newHttpClient();\n");
        }
        
        sb.append("HttpRequest request = HttpRequest.newBuilder()\n");
        sb.append("    .uri(URI.create(\"").append(request.getUrl()).append("\"))\n");
        
        // Headers
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            sb.append("    .header(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\")\n");
        }
        
        // Method & Body
        String method = request.getMethod();
        if (request.getBody() != null && !request.getBody().isEmpty()) {
            // Very basic escaping for Java string
            String body = request.getBody().replace("\"", "\\\"").replace("\n", "\\n");
            sb.append("    .method(\"").append(method).append("\", HttpRequest.BodyPublishers.ofString(\"").append(body).append("\"))\n");
        } else {
             if ("POST".equals(method) || "PUT".equals(method)) {
                  sb.append("    .method(\"").append(method).append("\", HttpRequest.BodyPublishers.noBody())\n");
             } else {
                  sb.append("    .").append(method.toLowerCase()).append("()\n");
             }
        }
        
        sb.append("    .build();\n\n");
        sb.append("HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("System.out.println(response.body());");
        
        return sb.toString();
    }

    /**
     * 生成 Python requests 代码
     */
    public String generatePython(HttpRequestModel request, ProxyConfig proxy) {
        StringBuilder sb = new StringBuilder("import requests\n\n");
        sb.append("url = \"").append(request.getUrl()).append("\"\n\n");
        
        // Headers
        if (!request.getHeaders().isEmpty()) {
            sb.append("headers = {\n");
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                sb.append("    \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\",\n");
            }
            sb.append("}\n\n");
        } else {
            sb.append("headers = {}\n\n");
        }
        
        // Body (Simple string payload)
        if (request.getBody() != null && !request.getBody().isEmpty()) {
            String body = request.getBody().replace("\"", "\\\"").replace("\n", "\\n"); // Basic escape
            sb.append("payload = \"").append(body).append("\"\n\n");
        } else {
             sb.append("payload = {}\n\n");
        }
        
        // Proxy
        if (proxy != null && proxy.isEnabled()) {
            sb.append("proxies = {\n");
            String proxyUrl = "http://" + proxy.getHost() + ":" + proxy.getPort();
            sb.append("    'http': '").append(proxyUrl).append("',\n");
            sb.append("    'https': '").append(proxyUrl).append("'\n");
            sb.append("}\n\n");
            sb.append("response = requests.request(\"").append(request.getMethod()).append("\", url, headers=headers, data=payload, proxies=proxies)\n\n");
        } else {
            sb.append("response = requests.request(\"").append(request.getMethod()).append("\", url, headers=headers, data=payload)\n\n");
        }
        
        sb.append("print(response.text)");
        
        return sb.toString();
    }
}
