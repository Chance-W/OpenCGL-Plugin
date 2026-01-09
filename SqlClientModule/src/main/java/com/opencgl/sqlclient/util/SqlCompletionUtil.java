package com.opencgl.sqlclient.util;

import java.util.List;

/** Lightweight, driver-independent SQL completion helpers. */
public final class SqlCompletionUtil {
    private static final List<String> KEYWORDS = List.of(
            "SELECT", "FROM", "WHERE", "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN",
            "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "OFFSET", "INSERT INTO", "VALUES",
            "UPDATE", "SET", "DELETE FROM", "CREATE TABLE", "ALTER TABLE", "DROP TABLE",
            "AND", "OR", "NOT", "NULL", "AS", "DISTINCT", "COUNT", "SUM", "AVG", "MAX", "MIN"
    );

    private SqlCompletionUtil() { }
    public static List<String> keywords() { return KEYWORDS; }

    public static String currentToken(String sql, int caret) {
        if (sql == null || sql.isEmpty()) return "";
        int end = Math.max(0, Math.min(caret, sql.length()));
        int start = end;
        while (start > 0 && (Character.isLetterOrDigit(sql.charAt(start - 1)) || sql.charAt(start - 1) == '_')) start--;
        return sql.substring(start, end);
    }

    public static String replaceCurrentToken(String sql, int caret, String replacement) {
        int end = Math.max(0, Math.min(caret, sql.length()));
        int start = end;
        while (start > 0 && (Character.isLetterOrDigit(sql.charAt(start - 1)) || sql.charAt(start - 1) == '_')) start--;
        return sql.substring(0, start) + replacement + sql.substring(end);
    }
}
