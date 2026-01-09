package com.opencgl.jsonxml.util;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码语法高亮工具
 */
public class SyntaxHighlighter {
    
    // JSON 语法模式
    private static final Pattern JSON_PATTERN = Pattern.compile(
        "(?<STRING>\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\")" +
        "|(?<NUMBER>-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?)" +
        "|(?<BOOL>true|false)" +
        "|(?<NULL>null)" +
        "|(?<BRACE>[{}])" +
        "|(?<BRACKET>[\\[\\]])" +
        "|(?<COLON>:)" +
        "|(?<COMMA>,)"
    );
    
    // XML 语法模式
    private static final Pattern XML_TAG = Pattern.compile(
        "(?<ELEMENT></?[\\w:-]+)" +
        "|(?<ATTR>[\\w:-]+(?=\\s*=))" +
        "|(?<VALUE>\"[^\"]*\")" +
        "|(?<COMMENT><!--[\\s\\S]*?-->)" +
        "|(?<TAGEND>[/]?>)"
    );
    
    // SQL 语法模式
    private static final String[] SQL_KEYWORDS = {
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN",
        "ORDER BY", "GROUP BY", "HAVING", "AS", "ON", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "VIEW",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "DEFAULT",
        "NULL", "NOT NULL", "AUTO_INCREMENT",
        "INT", "BIGINT", "VARCHAR", "CHAR", "TEXT", "DECIMAL", "FLOAT", "DOUBLE",
        "DATE", "TIME", "TIMESTAMP", "DATETIME", "BOOLEAN",
        "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "COUNT", "SUM", "AVG", "MAX", "MIN",
        "IF", "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END"
    };
    
    private static final String SQL_KEYWORD_PATTERN = "\\b(" + String.join("|", SQL_KEYWORDS) + ")\\b";
    private static final Pattern SQL_PATTERN = Pattern.compile(
        "(?<KEYWORD>" + SQL_KEYWORD_PATTERN + ")" +
        "|(?<STRING>'[^']*')" +
        "|(?<NUMBER>\\b\\d+(\\.\\d+)?\\b)" +
        "|(?<COMMENT>--[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
        "|(?<PAREN>[()])" +
        "|(?<COMMA>,)",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 应用 JSON 语法高亮
     */
    public static StyleSpans<Collection<String>> highlightJson(String text) {
        Matcher matcher = JSON_PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        
        while (matcher.find()) {
            String styleClass = 
                matcher.group("STRING") != null ? "json-string" :
                matcher.group("NUMBER") != null ? "json-number" :
                matcher.group("BOOL") != null ? "json-bool" :
                matcher.group("NULL") != null ? "json-null" :
                matcher.group("BRACE") != null ? "json-brace" :
                matcher.group("BRACKET") != null ? "json-bracket" :
                matcher.group("COLON") != null ? "json-colon" :
                matcher.group("COMMA") != null ? "json-comma" : null;
            
            if (styleClass != null) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastEnd = matcher.end();
            }
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        
        return spansBuilder.create();
    }
    
    /**
     * 应用 XML 语法高亮
     */
    public static StyleSpans<Collection<String>> highlightXml(String text) {
        Matcher matcher = XML_TAG.matcher(text);
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        
        while (matcher.find()) {
            String styleClass =
                matcher.group("ELEMENT") != null ? "xml-element" :
                matcher.group("ATTR") != null ? "xml-attr" :
                matcher.group("VALUE") != null ? "xml-value" :
                matcher.group("COMMENT") != null ? "xml-comment" :
                matcher.group("TAGEND") != null ? "xml-element" : null;
            
            if (styleClass != null) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastEnd = matcher.end();
            }
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        
        return spansBuilder.create();
    }
    
    /**
     * 应用 SQL 语法高亮
     */
    public static StyleSpans<Collection<String>> highlightSql(String text) {
        Matcher matcher = SQL_PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        
        while (matcher.find()) {
            String styleClass =
                matcher.group("KEYWORD") != null ? "sql-keyword" :
                matcher.group("STRING") != null ? "sql-string" :
                matcher.group("NUMBER") != null ? "sql-number" :
                matcher.group("COMMENT") != null ? "sql-comment" :
                matcher.group("PAREN") != null ? "sql-paren" :
                matcher.group("COMMA") != null ? "sql-comma" : null;
            
            if (styleClass != null) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastEnd = matcher.end();
            }
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        
        return spansBuilder.create();
    }
    
    /**
     * 设置 CodeArea 基础样式
     */
    public static void setupCodeArea(CodeArea codeArea) {
        codeArea.setStyle(
            "-fx-font-family: 'JetBrains Mono', 'Consolas', 'Monaco', monospace;" +
            "-fx-font-size: 14px;" /*+
            "-fx-background-color: #1e1e1e;"*/
        );
        codeArea.setWrapText(true);
    }
}
