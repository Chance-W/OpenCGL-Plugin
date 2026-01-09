package com.opencgl.regex.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexService {

    public static final Map<String, String> COMMON_PATTERNS = new LinkedHashMap<>();
    
    static {
        // 联系方式
        COMMON_PATTERNS.put("Email", "\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*");
        COMMON_PATTERNS.put("手机号(中国)", "^1[3-9]\\d{9}$");
        COMMON_PATTERNS.put("座机(中国)", "^(\\d{3,4}-)?\\d{7,8}$");
        COMMON_PATTERNS.put("身份证(18位)", "^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$");
        
        // 网络
        COMMON_PATTERNS.put("IP地址(IPv4)", "((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})(\\.((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})){3}");
        COMMON_PATTERNS.put("IPv6地址", "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}");
        COMMON_PATTERNS.put("MAC地址", "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})");
        COMMON_PATTERNS.put("URL", "https?://[^\\s]+");
        COMMON_PATTERNS.put("域名", "[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+");
        
        // 日期时间
        COMMON_PATTERNS.put("日期(yyyy-MM-dd)", "^\\d{4}-\\d{1,2}-\\d{1,2}$");
        COMMON_PATTERNS.put("日期(dd/MM/yyyy)", "^\\d{1,2}/\\d{1,2}/\\d{4}$");
        COMMON_PATTERNS.put("时间(HH:mm:ss)", "^([01]?\\d|2[0-3]):[0-5]\\d:[0-5]\\d$");
        COMMON_PATTERNS.put("ISO8601日期", "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?");
        
        // 数字
        COMMON_PATTERNS.put("整数", "^-?\\d+$");
        COMMON_PATTERNS.put("浮点数", "^-?\\d+(\\.\\d+)?$");
        COMMON_PATTERNS.put("正整数", "^[1-9]\\d*$");
        COMMON_PATTERNS.put("金额", "^\\d+(\\.\\d{1,2})?$");
        
        // 编程
        COMMON_PATTERNS.put("HTML标签", "<(\\S*?)[^>]*>.*?</\\1>|<.*? />");
        COMMON_PATTERNS.put("RGB颜色", "#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})");
        COMMON_PATTERNS.put("UUID", "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        COMMON_PATTERNS.put("变量名(驼峰)", "[a-z][a-zA-Z0-9]*");
        COMMON_PATTERNS.put("Java包名", "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");
        
        // 文本
        COMMON_PATTERNS.put("中文字符", "[\\u4e00-\\u9fa5]");
        COMMON_PATTERNS.put("空白行", "^\\s*$");
        COMMON_PATTERNS.put("行首空格", "^\\s+");
        COMMON_PATTERNS.put("行尾空格", "\\s+$");
        
        // 其他
        COMMON_PATTERNS.put("邮政编码(中国)", "^[1-9]\\d{5}$");
        COMMON_PATTERNS.put("车牌号(中国)", "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{5}$");
        COMMON_PATTERNS.put("Base64字符串", "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$");
    }

    public static class MatchResult {
        public int count;
        public List<String> matches = new ArrayList<>();
        public List<List<String>> groups = new ArrayList<>();
        public String error;
        public long timeCost;
    }

    public MatchResult matches(String regex, String text, int flags) {
        MatchResult result = new MatchResult();
        try {
            long start = System.currentTimeMillis();
            Pattern p = Pattern.compile(regex, flags);
            Matcher m = p.matcher(text);
            
            while (m.find()) {
                result.count++;
                if (result.count <= 1000) { // Limit results
                    result.matches.add(m.group());
                    List<String> groupList = new ArrayList<>();
                    for (int i = 0; i <= m.groupCount(); i++) {
                        groupList.add(m.group(i));
                    }
                    result.groups.add(groupList);
                }
            }
            result.timeCost = System.currentTimeMillis() - start;
        } catch (Exception e) {
            result.error = e.getMessage();
        }
        return result;
    }
    
    public String replace(String regex, String text, String replacement, int flags) {
         try {
            Pattern p = Pattern.compile(regex, flags);
            return p.matcher(text).replaceAll(replacement);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    public String[] split(String regex, String text, int flags) {
         try {
            Pattern p = Pattern.compile(regex, flags);
            return p.split(text);
        } catch (Exception e) {
            return new String[]{"Error: " + e.getMessage()};
        }
    }
    
    // 字符串转义/反转义
    public String escapeJavaString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    public String unescapeJavaString(String str) {
        if (str == null) return "";
        return str.replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
    
    // Code Generation
    public String toJavaString(String regex) {
        return escapeJavaString(regex);
    }
    
    public String toJsString(String regex) {
        return "/" + regex + "/g";
    }
    
    /**
     * 预览替换结果，高亮显示替换的部分
     */
    public ReplacePreview getReplacePreview(String regex, String text, String replacement, int flags) {
        ReplacePreview preview = new ReplacePreview();
        try {
            Pattern p = Pattern.compile(regex, flags);
            Matcher m = p.matcher(text);
            
            List<String> beforeParts = new ArrayList<>();
            List<String> afterParts = new ArrayList<>();
            
            int matchCount = 0;
            while (m.find() && matchCount < 50) {
                beforeParts.add(m.group());
                afterParts.add(m.replaceFirst(replacement));
                matchCount++;
            }
            
            preview.matchCount = matchCount;
            preview.beforeSamples = beforeParts;
            preview.afterSamples = afterParts;
            preview.fullResult = m.replaceAll(replacement);
        } catch (Exception e) {
            preview.error = e.getMessage();
        }
        return preview;
    }
    
    public static class ReplacePreview {
        public int matchCount;
        public List<String> beforeSamples = new ArrayList<>();
        public List<String> afterSamples = new ArrayList<>();
        public String fullResult;
        public String error;
    }
}
