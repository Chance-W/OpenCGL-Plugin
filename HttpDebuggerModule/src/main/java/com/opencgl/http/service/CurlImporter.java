package com.opencgl.http.service;

import com.opencgl.http.model.HttpRequestModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * cURL 命令解析器
 */
public class CurlImporter {
    private static final Logger logger = LoggerFactory.getLogger(CurlImporter.class);
    
    public static HttpRequestModel parse(String curlCommand) {
        if (curlCommand == null || curlCommand.trim().isEmpty()) {
            throw new IllegalArgumentException("cURL command cannot be empty");
        }
        
        HttpRequestModel request = new HttpRequestModel();
        request.setMethod("GET");
        
        try {
            String cmd = curlCommand.trim();
            if (cmd.toLowerCase().startsWith("curl ")) {
                cmd = cmd.substring(5).trim();
            }
            
            String url = extractUrl(cmd);
            if (url != null) {
                request.setUrl(url);
            }
            
            String method = extractMethod(cmd);
            if (method != null) {
                request.setMethod(method.toUpperCase());
            }
            
            Map<String, String> headers = extractHeaders(cmd);
            request.setHeaders(headers);
            
            String body = extractBody(cmd);
            if (body != null) {
                request.setBody(body);
                if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                    request.setBodyType("JSON");
                } else if (body.contains("=") && body.contains("&")) {
                    request.setBodyType("FORM");
                } else {
                    request.setBodyType("RAW");
                }
            }
            
            logger.info("Parsed cURL: {} {}", request.getMethod(), request.getUrl());
            
        } catch (Exception e) {
            logger.error("Failed to parse cURL", e);
            throw new RuntimeException("Invalid cURL: " + e.getMessage());
        }
        
        return request;
    }
    
    private static String extractUrl(String cmd) {
        Pattern urlPattern = Pattern.compile("--url\\s+['\"]?([^'\"\\s]+)['\"]?");
        Matcher matcher = urlPattern.matcher(cmd);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        Pattern bareUrlPattern = Pattern.compile("(https?://[^\\s'\"]+)");
        matcher = bareUrlPattern.matcher(cmd);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    private static String extractMethod(String cmd) {
        Pattern methodPattern = Pattern.compile("(?:-X|--request)\\s+['\"]?([A-Z]+)['\"]?");
        Matcher matcher = methodPattern.matcher(cmd);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private static Map<String, String> extractHeaders(String cmd) {
        Map<String, String> headers = new HashMap<>();
        Pattern headerPattern = Pattern.compile("(?:-H|--header)\\s+['\"]([^'\"]+)['\"]");
        Matcher matcher = headerPattern.matcher(cmd);
        
        while (matcher.find()) {
            String header = matcher.group(1);
            int colonIndex = header.indexOf(':');
            if (colonIndex > 0) {
                String key = header.substring(0, colonIndex).trim();
                String value = header.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
        
        return headers;
    }
    
    private static String extractBody(String cmd) {
        // Single-quoted: allow any character including " and newlines until closing '
        // Double-quoted: allow any character including ' and newlines until closing "
        // DOTALL so newlines inside body are captured
        Pattern[] patterns = {
            Pattern.compile("(?:-d|--data|--data-raw|--data-binary)\\s+'((?:[^'\\\\]|\\\\.)*)'", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("(?:-d|--data|--data-raw|--data-binary)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("(?:-d|--data|--data-raw|--data-binary)\\s+([^\\s-]+)", Pattern.CASE_INSENSITIVE)
        };
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(cmd);
            if (matcher.find()) {
                String body = matcher.group(1);
                body = body.replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
                if (body.contains("%")) {
                    try {
                        body = URLDecoder.decode(body, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}
                }
                return body;
            }
        }
        
        return null;
    }
}
