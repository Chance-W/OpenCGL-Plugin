package com.opencgl.pathextractor.service;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonPathService {
    private static final Logger logger = LoggerFactory.getLogger(JsonPathService.class);
    
    public String extract(String json, String path) {
        if (json == null || json.trim().isEmpty()) {
            return "错误: JSON内容为空";
        }
        
        if (path == null || path.trim().isEmpty()) {
            return "错误: 路径表达式为空";
        }
        
        try {
            Configuration conf = Configuration.defaultConfiguration();
            Object result = JsonPath.using(conf).parse(json).read(path);
            
            // 格式化输出
            if (result == null) {
                return "null";
            }
            
            return com.alibaba.fastjson2.JSON.toJSONString(result, 
                com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
                
        } catch (PathNotFoundException e) {
            return "错误: 路径不存在\n" + e.getMessage();
        } catch (Exception e) {
            logger.error("JSONPath提取失败", e);
            return "错误: " + e.getMessage();
        }
    }
    
    public boolean isValidJson(String json) {
        try {
            com.alibaba.fastjson2.JSON.parse(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
