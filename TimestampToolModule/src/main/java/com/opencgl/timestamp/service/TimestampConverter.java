package com.opencgl.timestamp.service;

import com.opencgl.timestamp.i18n.I18N;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * 时间戳转换服务
 * 支持多种格式转换
 */
public class TimestampConverter {

    // 预定义格式
    public static final Map<String, String> PREDEFINED_FORMATS = new LinkedHashMap<>();

    static {
        PREDEFINED_FORMATS.put("ISO 8601", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        PREDEFINED_FORMATS.put("ISO 8601 (No Millis)", "yyyy-MM-dd'T'HH:mm:ssXXX");
        PREDEFINED_FORMATS.put("ISO Date", "yyyy-MM-dd");
        PREDEFINED_FORMATS.put("ISO Time", "HH:mm:ss");
        PREDEFINED_FORMATS.put("RFC 2822", "EEE, dd MMM yyyy HH:mm:ss Z");
        PREDEFINED_FORMATS.put("CN Standard", "yyyy\u5e74MM\u6708dd\u65e5 HH:mm:ss");
        PREDEFINED_FORMATS.put("CN Date", "yyyy\u5e74MM\u6708dd\u65e5");
        PREDEFINED_FORMATS.put("US Format", "MM/dd/yyyy HH:mm:ss");
        PREDEFINED_FORMATS.put("EU Format", "dd/MM/yyyy HH:mm:ss");
        PREDEFINED_FORMATS.put("MySQL DateTime", "yyyy-MM-dd HH:mm:ss");
        PREDEFINED_FORMATS.put("Log Format", "yyyy-MM-dd HH:mm:ss.SSS");
        PREDEFINED_FORMATS.put("Compact Format", "yyyyMMddHHmmss");
        PREDEFINED_FORMATS.put("Compact Date", "yyyyMMdd");
        PREDEFINED_FORMATS.put("12 Hour", "yyyy-MM-dd hh:mm:ss a");
        PREDEFINED_FORMATS.put("Unix Path", "yyyy/MM/dd");
    }

    // 常用时区
    public static final Map<String, String> COMMON_TIMEZONES = new LinkedHashMap<>();

    static {
        COMMON_TIMEZONES.put(I18N.get("tz.local"), ZoneId.systemDefault().getId());
        COMMON_TIMEZONES.put("UTC", "UTC");
        COMMON_TIMEZONES.put(I18N.get("msg.beijing") + " (CST)", "Asia/Shanghai");
        COMMON_TIMEZONES.put(I18N.get("msg.tokyo") + " (JST)", "Asia/Tokyo");
        COMMON_TIMEZONES.put(I18N.get("msg.newyork") + " (EST)", "America/New_York");
        COMMON_TIMEZONES.put("America/Los_Angeles (PST)", "America/Los_Angeles");
        COMMON_TIMEZONES.put("Europe/London (GMT)", "Europe/London");
        COMMON_TIMEZONES.put("Europe/Paris (CET)", "Europe/Paris");
        COMMON_TIMEZONES.put("Australia/Sydney (AEST)", "Australia/Sydney");
        COMMON_TIMEZONES.put("Asia/Singapore", "Asia/Singapore");
        COMMON_TIMEZONES.put("Asia/Hong_Kong", "Asia/Hong_Kong");
        COMMON_TIMEZONES.put("Asia/Seoul", "Asia/Seoul");
        COMMON_TIMEZONES.put("Europe/Moscow", "Europe/Moscow");
        COMMON_TIMEZONES.put("Asia/Dubai", "Asia/Dubai");
    }

    /**
     * Unix 时间戳(秒) 转日期时间
     */
    public static ZonedDateTime fromUnixSeconds(long seconds, ZoneId zone) {
        return Instant.ofEpochSecond(seconds).atZone(zone);
    }

    /**
     * Unix 时间戳(毫秒) 转日期时间
     */
    public static ZonedDateTime fromUnixMillis(long millis, ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone);
    }

    /**
     * 日期时间 转 Unix 时间戳(秒)
     */
    public static long toUnixSeconds(ZonedDateTime dateTime) {
        return dateTime.toEpochSecond();
    }

    /**
     * 日期时间 转 Unix 时间戳(毫秒)
     */
    public static long toUnixMillis(ZonedDateTime dateTime) {
        return dateTime.toInstant().toEpochMilli();
    }

    /**
     * 格式化日期时间
     */
    public static String format(ZonedDateTime dateTime, String pattern) {
        return format(dateTime, pattern, Locale.getDefault());
    }

    public static String format(ZonedDateTime dateTime, String pattern, Locale locale) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, locale);
            return dateTime.format(formatter);
        } catch (Exception e) {
            return I18N.get("msg.format_error", e.getMessage());
        }
    }

    /**
     * 解析日期字符串
     */
    public static ZonedDateTime parse(String dateStr, String pattern, ZoneId zone) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);

            // 尝试解析为 ZonedDateTime
            try {
                return ZonedDateTime.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // 尝试解析为 LocalDateTime
                try {
                    LocalDateTime ldt = LocalDateTime.parse(dateStr, formatter);
                    return ldt.atZone(zone);
                } catch (DateTimeParseException e2) {
                    // 尝试解析为 LocalDate
                    LocalDate ld = LocalDate.parse(dateStr, formatter);
                    return ld.atStartOfDay(zone);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(I18N.get("msg.parse_error", dateStr, pattern));
        }
    }

    /**
     * 智能解析 - 自动检测格式
     */
    public static ZonedDateTime smartParse(String input, ZoneId zone) {
        input = input.trim();

        // 纯数字 - 尝试解析为时间戳
        if (input.matches("\\d+")) {
            long value = Long.parseLong(input);
            if (value < 100000000000L) {
                // 秒级时间戳
                return fromUnixSeconds(value, zone);
            } else {
                // 毫秒级时间戳
                return fromUnixMillis(value, zone);
            }
        }

        // 尝试常见格式
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd",
                "dd/MM/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm:ss",
                "yyyyMMddHHmmss",
                "yyyyMMdd",
                "yyyy年MM月dd日 HH:mm:ss",
                "yyyy年MM月dd日"
        };

        for (String pattern : patterns) {
            try {
                return parse(input, pattern, zone);
            } catch (Exception ignored) {
            }
        }

        throw new IllegalArgumentException(I18N.get("msg.unknown_format", input));
    }

    /**
     * 时区转换
     */
    public static ZonedDateTime convertTimezone(ZonedDateTime dateTime, ZoneId targetZone) {
        return dateTime.withZoneSameInstant(targetZone);
    }

    /**
     * 计算两个时间的差值
     */
    public static Duration between(ZonedDateTime start, ZonedDateTime end) {
        return Duration.between(start, end);
    }

    /**
     * 格式化时间差
     */
    public static String formatDuration(Duration duration) {
        long seconds = Math.abs(duration.getSeconds());
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(days).append(" ").append(I18N.get("unit.day")).append(" ");
        if (hours > 0)
            sb.append(hours).append(" ").append(I18N.get("unit.hour")).append(" ");
        if (minutes > 0)
            sb.append(minutes).append(" ").append(I18N.get("unit.minute")).append(" ");
        sb.append(secs).append(" ").append(I18N.get("unit.second"));

        if (duration.isNegative()) {
            return "-" + sb.toString();
        }
        return sb.toString();
    }

    /**
     * 获取当前时间的多格式输出
     */
    public static Map<String, String> getCurrentTimeFormats(ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, String> result = new LinkedHashMap<>();

        result.put(I18N.get("msg.unix_s"), String.valueOf(toUnixSeconds(now)));
        result.put(I18N.get("msg.unix_ms"), String.valueOf(toUnixMillis(now)));

        for (Map.Entry<String, String> entry : PREDEFINED_FORMATS.entrySet()) {
            try {
                result.put(entry.getKey(), format(now, entry.getValue()));
            } catch (Exception e) {
                result.put(entry.getKey(), I18N.get("msg.not_applicable"));
            }
        }

        return result;
    }

    /**
     * 添加时间
     */
    public static ZonedDateTime addTime(ZonedDateTime dateTime, long amount, ChronoUnit unit) {
        return dateTime.plus(amount, unit);
    }

    /**
     * 获取年份的第几天
     */
    public static int getDayOfYear(ZonedDateTime dateTime) {
        return dateTime.getDayOfYear();
    }

    /**
     * 获取年份的第几周
     */
    public static int getWeekOfYear(ZonedDateTime dateTime) {
        return dateTime.get(WeekFields.ISO.weekOfYear());
    }

    /**
     * 判断是否为闰年
     */
    public static boolean isLeapYear(ZonedDateTime dateTime) {
        return dateTime.toLocalDate().isLeapYear();
    }

    /**
     * 获取所有可用时区
     */
    public static Set<String> getAllTimezones() {
        return ZoneId.getAvailableZoneIds();
    }
}
