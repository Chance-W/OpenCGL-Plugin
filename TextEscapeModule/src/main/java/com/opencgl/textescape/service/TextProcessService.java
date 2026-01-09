package com.opencgl.textescape.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.text.StringEscapeUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文本处理服务
 */
public class TextProcessService {

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // JSON 处理
    public String formatDateJson(String json) {
        try {
            JsonElement je = JsonParser.parseString(json);
            return PRETTY_GSON.toJson(je);
        } catch (Exception e) {
            return "无效的 JSON 格式: " + e.getMessage();
        }
    }

    public String compressJson(String json) {
        try {
            JsonElement je = JsonParser.parseString(json);
            return new Gson().toJson(je); // Default Gson is compact
        } catch (Exception e) {
            return json.replaceAll("\\s+", ""); // Fallback simple remove
        }
    }
    
    public String escapeJson(String input) {
        return StringEscapeUtils.escapeJson(input);
    }
    
    public String unescapeJson(String input) {
        return StringEscapeUtils.unescapeJson(input);
    }

    // HTML 处理
    public String escapeHtml(String input) {
        return StringEscapeUtils.escapeHtml4(input);
    }
    
    public String unescapeHtml(String input) {
        return StringEscapeUtils.unescapeHtml4(input);
    }

    // XML 处理
    public String escapeXml(String input) {
        return StringEscapeUtils.escapeXml11(input);
    }
    
    public String unescapeXml(String input) {
        return StringEscapeUtils.unescapeXml(input);
    }
    
    // URL 处理
    public String encodeUrl(String input) {
        try {
            return URLEncoder.encode(input, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return e.getMessage();
        }
    }
    
    public String decodeUrl(String input) {
        try {
            return URLDecoder.decode(input, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return e.getMessage();
        }
    }
    
    // Java String 处理
    public String escapeJava(String input) {
        return StringEscapeUtils.escapeJava(input);
    }
    
    public String unescapeJava(String input) {
        return StringEscapeUtils.unescapeJava(input);
    }

    // 通用处理
    public String removeNewlines(String input) {
        if (input == null) return "";
        return input.replace("\n", "").replace("\r", "");
    }
    
    public String addNewlines(String input, String separator) {
         if (input == null) return "";
         // 这里的逻辑比较模糊，通常是把某些分隔符转为换行，或者每隔多长换行
         // 暂时实现为：替换常见的列表分隔符 (逗号, 分号) 为换行，方便查看
         return input.replace(separator, separator + "\n");
    }
    
    public String escapeNewlines(String input) {
        if (input == null) return "";
        return input.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    
    public String unescapeNewlines(String input) {
        if (input == null) return "";
        return input.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }
}
