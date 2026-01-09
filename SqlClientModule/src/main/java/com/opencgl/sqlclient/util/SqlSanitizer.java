package com.opencgl.sqlclient.util;

/** 执行前清理编辑器中的注释和语句分隔符。不会改动字符串字面量中的 --、/* 或分号。 */
public final class SqlSanitizer {
    private SqlSanitizer() {}

    public static String prepare(String sql) {
        if (sql == null) return "";
        StringBuilder out = new StringBuilder(sql.length());
        boolean single = false, dbl = false, line = false, block = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i), n = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (line) { if (c == '\n' || c == '\r') { line = false; out.append(c); } continue; }
            if (block) { if (c == '*' && n == '/') { block = false; i++; out.append(' '); } continue; }
            if (!dbl && c == '\'' ) { single = !single; out.append(c); continue; }
            if (!single && c == '"') { dbl = !dbl; out.append(c); continue; }
            if (!single && !dbl && c == '-' && n == '-') { line = true; i++; out.append(' '); continue; }
            if (!single && !dbl && c == '/' && n == '*') { block = true; i++; out.append(' '); continue; }
            out.append(c);
        }
        return out.toString().trim().replaceFirst(";+\\s*$", "");
    }
}
