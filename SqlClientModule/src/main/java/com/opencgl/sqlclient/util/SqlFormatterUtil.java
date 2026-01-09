package com.opencgl.sqlclient.util;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.languages.Dialect;

public class SqlFormatterUtil {
    
    public static String format(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        
        try {
            return SqlFormatter.of(Dialect.StandardSql).format(sql);
        } catch (Exception e) {
            // 格式化失败，返回原文
            return sql;
        }
    }
    
    public static String formatMySQL(String sql) {
        try {
            return SqlFormatter.of(Dialect.MySql).format(sql);
        } catch (Exception e) {
            return sql;
        }
    }
    
    public static String formatPostgreSQL(String sql) {
        try {
            return SqlFormatter.of(Dialect.PostgreSql).format(sql);
        } catch (Exception e) {
            return sql;
        }
    }

    public static String formatForType(String sql, String dbType) {
        if (dbType == null) return format(sql);
        try {
            return switch (dbType.toUpperCase()) {
                case "MYSQL" -> SqlFormatter.of(Dialect.MySql).format(sql);
                case "MARIADB" -> SqlFormatter.of(Dialect.MariaDb).format(sql);
                case "POSTGRESQL" -> SqlFormatter.of(Dialect.PostgreSql).format(sql);
                case "ORACLE" -> SqlFormatter.of(Dialect.PlSql).format(sql);
                case "SQL_SERVER" -> SqlFormatter.of(Dialect.TSql).format(sql);
                default -> format(sql);
            };
        } catch (Exception e) {
            return sql;
        }
    }
}
