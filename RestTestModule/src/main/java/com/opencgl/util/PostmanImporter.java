package com.opencgl.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.opencgl.model.RestWidgetDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Postman Collection 导入工具
 * 支持 Postman Collection v2.1 格式
 */
public class PostmanImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(PostmanImporter.class);
    
    /**
     * 从文件导入 Postman Collection
     */
    public static List<RestWidgetDto> importFromFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        return importFromJson(content);
    }
    
    /**
     * 从 JSON 字符串导入
     */
    public static List<RestWidgetDto> importFromJson(String json) {
        List<RestWidgetDto> requests = new ArrayList<>();
        
        try {
            JSONObject collection = JSONObject.parseObject(json);
            
            // Postman Collection v2.1 格式
            if (collection.containsKey("info") && collection.containsKey("item")) {
                JSONArray items = collection.getJSONArray("item");
                parseItems(items, requests, null);
            }
            // 简单的请求数组
            else if (collection.containsKey("requests")) {
                JSONArray requestArray = collection.getJSONArray("requests");
                for (int i = 0; i < requestArray.size(); i++) {
                    RestWidgetDto dto = parseRequest(requestArray.getJSONObject(i));
                    if (dto != null) {
                        requests.add(dto);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to parse Postman collection", e);
        }
        
        return requests;
    }
    
    /**
     * 递归解析 items（支持文件夹）
     */
    private static void parseItems(JSONArray items, List<RestWidgetDto> requests, String folderName) {
        if (items == null) return;
        
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            
            // 文件夹
            if (item.containsKey("item")) {
                String name = item.getString("name");
                parseItems(item.getJSONArray("item"), requests, name);
            }
            // 请求
            else if (item.containsKey("request")) {
                RestWidgetDto dto = parseRequestItem(item, folderName);
                if (dto != null) {
                    requests.add(dto);
                }
            }
        }
    }
    
    /**
     * 解析 Postman 请求 item
     */
    private static RestWidgetDto parseRequestItem(JSONObject item, String folderName) {
        try {
            RestWidgetDto dto = new RestWidgetDto();
            
            String name = item.getString("name");
            dto.setName(folderName != null ? folderName + "/" + name : name);
            dto.setIsLeaf(true);
            
            JSONObject request = item.getJSONObject("request");
            
            // 方法
            String method = request.getString("method");
            dto.setRequestMethod(method != null ? method : "GET");
            
            // URL
            Object urlObj = request.get("url");
            if (urlObj instanceof String) {
                dto.setRequestUrl((String) urlObj);
            } else if (urlObj instanceof JSONObject) {
                JSONObject urlJson = (JSONObject) urlObj;
                dto.setRequestUrl(urlJson.getString("raw"));
            }
            
            // Headers
            JSONArray headers = request.getJSONArray("header");
            if (headers != null) {
                JSONObject headerMap = new JSONObject();
                for (int i = 0; i < headers.size(); i++) {
                    JSONObject header = headers.getJSONObject(i);
                    if (!header.getBooleanValue("disabled")) {
                        headerMap.put(header.getString("key"), header.getString("value"));
                    }
                }
                dto.setRequestHeader(headerMap.toJSONString());
            }
            
            // Body
            JSONObject body = request.getJSONObject("body");
            if (body != null) {
                String mode = body.getString("mode");
                
                switch (mode != null ? mode : "") {
                    case "raw":
                        dto.setInputText(body.getString("raw"));
                        // 检查 options 获取 Content-Type
                        JSONObject options = body.getJSONObject("options");
                        if (options != null) {
                            JSONObject rawOptions = options.getJSONObject("raw");
                            if (rawOptions != null) {
                                String language = rawOptions.getString("language");
                                if ("json".equals(language)) {
                                    dto.setRequestMediaType("application/json");
                                } else if ("xml".equals(language)) {
                                    dto.setRequestMediaType("application/xml");
                                }
                            }
                        }
                        break;
                    case "urlencoded":
                        dto.setRequestMediaType("application/x-www-form-urlencoded");
                        JSONArray urlencoded = body.getJSONArray("urlencoded");
                        if (urlencoded != null) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < urlencoded.size(); i++) {
                                JSONObject param = urlencoded.getJSONObject(i);
                                if (sb.length() > 0) sb.append("&");
                                sb.append(param.getString("key")).append("=").append(param.getString("value"));
                            }
                            dto.setInputText(sb.toString());
                        }
                        break;
                    case "formdata":
                        dto.setRequestMediaType("multipart/form-data");
                        break;
                }
            }
            
            return dto;
            
        } catch (Exception e) {
            logger.error("Failed to parse request item", e);
            return null;
        }
    }
    
    /**
     * 解析简单请求格式
     */
    private static RestWidgetDto parseRequest(JSONObject request) {
        try {
            RestWidgetDto dto = new RestWidgetDto();
            dto.setName(request.getString("name"));
            dto.setRequestMethod(request.getString("method"));
            dto.setRequestUrl(request.getString("url"));
            dto.setRequestHeader(request.getString("headers"));
            dto.setInputText(request.getString("body"));
            dto.setIsLeaf(true);
            return dto;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 验证是否为有效的 Postman Collection
     */
    public static boolean isValidCollection(String json) {
        try {
            JSONObject obj = JSONObject.parseObject(json);
            return obj.containsKey("info") && obj.containsKey("item");
        } catch (Exception e) {
            return false;
        }
    }
}
