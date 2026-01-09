package com.opencgl.base.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用变量格式化工具类
 * 支持 ${VAR} 格式替换，包括 UUID, 时间戳, 随机数以及动态日期格式
 * @author Chance.W
 */
public class FormatVariableUtil {
    private static final Logger log = LoggerFactory.getLogger(FormatVariableUtil.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * 格式化字符串，替换其中的变量
     * @param template 模板字符串
     * @return 替换后的字符串
     */
    public static String format(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        StringBuffer sb = new StringBuffer();
        Matcher matcher = VAR_PATTERN.matcher(template);

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement;
            try {
                replacement = resolveVariable(varName);
            } catch (Exception e) {
                // log.warn("Resolve variable error: " + varName, e);
                // 无法解析时保留原样
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolveVariable(String varName) {
        if (varName == null) return "";
        
        // 1. UUID
        if ("UUID".equalsIgnoreCase(varName)) {
            return UUID.randomUUID().toString();
        }
        if ("UUID_SIMPLE".equalsIgnoreCase(varName)) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        if ("UUID_UPPER".equalsIgnoreCase(varName)) {
            return UUID.randomUUID().toString().toUpperCase();
        }

        // 2. Timestamp
        if ("TIMESTAMP".equalsIgnoreCase(varName) || "DATE".equalsIgnoreCase(varName)) {
            return String.valueOf(System.currentTimeMillis());
        }
        if ("TIMESTAMP_S".equalsIgnoreCase(varName)) {
            return String.valueOf(System.currentTimeMillis() / 1000);
        }

        // 3. Random
        if (varName.startsWith("Random")) {
            String suffix = varName.substring(6);
            int length = 8; // 默认长度
            if (!suffix.isEmpty()) {
                try {
                    length = Integer.parseInt(suffix);
                } catch (NumberFormatException ignored) {}
            }
            return getRandom(length);
        }
        
        // 4. 尝试直接作为 Date Pattern 解析 (e.g. yyyyMMdd)
        try {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern(varName));
        } catch (IllegalArgumentException e) {
            // 无法解析为日期格式，并不是已知变量，返回原字符串
            return "${" + varName + "}";
        }
    }

    public static String getRandom(int length) {
        if (length <= 0) length = 8;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder val = new StringBuilder();
        // 保持原逻辑：首位生成1-9，或者按照原 getRandom 逻辑：首位 (random 10) if 0 -> 1.
        // 原逻辑：
        // int num = random.nextInt(10); if(num!=0) append(num) else append(1);
        int first = random.nextInt(10);
        val.append(first == 0 ? 1 : first);
        
        for (int i = 0; i < length - 1; i++) {
            val.append(random.nextInt(10));
        }
        return val.toString();
    }
}
