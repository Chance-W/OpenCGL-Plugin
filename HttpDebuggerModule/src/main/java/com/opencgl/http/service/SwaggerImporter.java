package com.opencgl.http.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.opencgl.http.model.HttpTreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Swagger/OpenAPI 导入器
 */
public class SwaggerImporter {
    private static final Logger logger = LoggerFactory.getLogger(SwaggerImporter.class);
    
    public static List<HttpTreeItem> importFromFile(File file, Long parentId) throws Exception {
        String content = new String(Files.readAllBytes(file.toPath()));
        return importFromJson(content, parentId);
    }
    
    public static List<HttpTreeItem> importFromJson(String jsonContent, Long parentId) {
        List<HttpTreeItem> items = new ArrayList<>();
        
        try {
            JSONObject spec = JSON.parseObject(jsonContent);
            boolean isOpenAPI3 = spec.containsKey("openapi");
            String baseUrl = extractBaseUrl(spec, isOpenAPI3);
            
            JSONObject paths = spec.getJSONObject("paths");
            if (paths == null) {
                logger.warn("No paths in Swagger");
                return items;
            }
            
            for (String path : paths.keySet()) {
                JSONObject pathItem = paths.getJSONObject(path);
                
                for (String method : pathItem.keySet()) {
                    if (isHttpMethod(method)) {
                        JSONObject operation = pathItem.getJSONObject(method);
                        HttpTreeItem item = createRequestItem(path, method.toUpperCase(), 
                            operation, baseUrl, parentId);
                        items.add(item);
                    }
                }
            }
            
            logger.info("Imported {} requests from Swagger", items.size());
            
        } catch (Exception e) {
            logger.error("Failed to parse Swagger", e);
            throw new RuntimeException("Invalid Swagger: " + e.getMessage());
        }
        
        return items;
    }
    
    private static String extractBaseUrl(JSONObject spec, boolean isOpenAPI3) {
        if (isOpenAPI3) {
            JSONArray servers = spec.getJSONArray("servers");
            if (servers != null && servers.size() > 0) {
                return servers.getJSONObject(0).getString("url");
            }
        } else {
            String scheme = "http";
            JSONArray schemes = spec.getJSONArray("schemes");
            if (schemes != null && schemes.size() > 0) {
                scheme = schemes.getString(0);
            }
            
            String host = spec.getString("host");
            String basePath = spec.getString("basePath");
            
            if (host != null) {
                StringBuilder url = new StringBuilder(scheme).append("://").append(host);
                if (basePath != null && !basePath.equals("/")) {
                    url.append(basePath);
                }
                return url.toString();
            }
        }
        
        return "http://localhost";
    }
    
    private static HttpTreeItem createRequestItem(String path, String method, 
                                                   JSONObject operation, String baseUrl, Long parentId) {
        HttpTreeItem item = new HttpTreeItem();
        item.setNodeType(HttpTreeItem.TYPE_REQUEST);
        item.setParentId(parentId);
        item.setIsLeaf(true);
        
        String name = operation.getString("summary");
        if (name == null || name.isEmpty()) {
            name = operation.getString("operationId");
        }
        if (name == null || name.isEmpty()) {
            name = method + " " + path;
        }
        item.setName(name);
        
        item.setMethod(method);
        item.setUrl(baseUrl + path);
        
        JSONArray parameters = operation.getJSONArray("parameters");
        if (parameters != null) {
            JSONObject paramsJson = new JSONObject();
            JSONObject headersJson = new JSONObject();
            
            for (int i = 0; i < parameters.size(); i++) {
                JSONObject param = parameters.getJSONObject(i);
                String in = param.getString("in");
                String paramName = param.getString("name");
                String defaultValue = param.getString("default");
                
                if ("query".equals(in)) {
                    paramsJson.put(paramName, defaultValue != null ? defaultValue : "");
                } else if ("header".equals(in)) {
                    headersJson.put(paramName, defaultValue != null ? defaultValue : "");
                }
            }
            
            if (!paramsJson.isEmpty()) {
                item.setParams(paramsJson.toJSONString());
            }
            if (!headersJson.isEmpty()) {
                item.setHeaders(headersJson.toJSONString());
            }
        }
        
        JSONObject requestBody = operation.getJSONObject("requestBody");
        if (requestBody != null) {
            JSONObject content = requestBody.getJSONObject("content");
            if (content != null && content.containsKey("application/json")) {
                item.setBodyType("JSON");
                JSONObject schema = content.getJSONObject("application/json").getJSONObject("schema");
                if (schema != null) {
                    item.setBody(generateExample(schema));
                }
            }
        }
        
        return item;
    }
    
    private static String generateExample(JSONObject schema) {
        JSONObject example = schema.getJSONObject("example");
        if (example != null) {
            return example.toJSONString();
        }
        
        JSONObject properties = schema.getJSONObject("properties");
        if (properties != null) {
            JSONObject exampleObj = new JSONObject();
            for (String key : properties.keySet()) {
                JSONObject prop = properties.getJSONObject(key);
                Object defaultValue = prop.get("default");
                if (defaultValue != null) {
                    exampleObj.put(key, defaultValue);
                } else {
                    exampleObj.put(key, getDefaultForType(prop.getString("type")));
                }
            }
            return exampleObj.toJSONString();
        }
        
        return "{}";
    }
    
    private static Object getDefaultForType(String type) {
        if (type == null) return "";
        switch (type) {
            case "string": return "";
            case "integer": return 0;
            case "number": return 0.0;
            case "boolean": return false;
            case "array": return new JSONArray();
            case "object": return new JSONObject();
            default: return "";
        }
    }
    
    private static boolean isHttpMethod(String method) {
        String m = method.toLowerCase();
        return m.equals("get") || m.equals("post") || m.equals("put") || 
               m.equals("delete") || m.equals("patch") || m.equals("options") || 
               m.equals("head");
    }
}
