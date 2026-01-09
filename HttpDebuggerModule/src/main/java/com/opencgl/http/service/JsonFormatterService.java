package com.opencgl.http.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

/**
 * JSON格式化服务
 */
public class JsonFormatterService {

    /**
     * 格式化JSON字符串
     */
    public String formatJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return "";
        }

        try {
            Object obj = JSON.parse(jsonStr);
            return JSON.toJSONString(obj, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue);
        } catch (Exception e) {
            // 如果不是有效JSON，返回原字符串
            return jsonStr;
        }
    }

    /**
     * 压缩JSON（移除空格和换行）
     */
    public String compactJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return "";
        }

        try {
            Object obj = JSON.parse(jsonStr);
            return JSON.toJSONString(obj, SerializerFeature.WriteMapNullValue);
        } catch (Exception e) {
            return jsonStr;
        }
    }

    /**
     * 验证JSON格式
     */
    public boolean isValidJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return false;
        }

        try {
            JSON.parse(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
