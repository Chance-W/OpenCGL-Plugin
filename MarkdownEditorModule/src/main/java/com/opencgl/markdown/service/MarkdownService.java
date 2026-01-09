package com.opencgl.markdown.service;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown解析渲染服务（带行号映射）
 */
public class MarkdownService {
    
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Map<Integer, Integer> lineMapping = new HashMap<>();
    
    public MarkdownService() {
        // 配置GFM扩展
        List<Extension> extensions = Arrays.asList(
            TablesExtension.create(),
            StrikethroughExtension.create()
        );
        
        this.parser = Parser.builder()
                .extensions(extensions)
                .build();
        
        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .build();
    }
    
    /**
     * 将Markdown转换为HTML
     */
    public String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
    
    /**
     * 在Markdown中插入行号标记
     */
    private String addLineMarkers(String markdown) {
        String[] lines = markdown.split("\\n", -1);
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            
            // 只在非空行和块级元素开始处添加标记
            if (!line.trim().isEmpty()) {
                // 检测是否是块级元素的开始
                if (isBlockStart(line)) {
                    result.append("<!--LINE:").append(lineNum).append("-->");
                }
            }
            
            result.append(line);
            if (i < lines.length - 1) {
                result.append("\\n");
            }
        }
        
        return result.toString();
    }
    
    /**
     * 判断是否是块级元素的开始
     */
    private boolean isBlockStart(String line) {
        String trimmed = line.trim();
        // 标题
        if (trimmed.startsWith("#")) return true;
        // 列表
        if (trimmed.matches("^[*+-]\\s.*") || trimmed.matches("^\\d+\\.\\s.*")) return true;
        // 代码块
        if (trimmed.startsWith("```")) return true;
        // 引用
        if (trimmed.startsWith(">")) return true;
        // 分隔线
       if (trimmed.matches("^[-*_]{3,}$")) return true;
        // 普通段落（非缩进，非空行）
        if (!line.startsWith("    ") && !trimmed.isEmpty()) return true;
        
        return false;
    }
    
    /**
     * 将HTML注释转换为data-line属性
     */
    private String injectLineNumbers(String html) {
        // 查找<!--LINE:数字-->并将其转换为data-line属性
        return html.replaceAll("<!--LINE:(\\d+)-->\\s*<(\\w+)([^>]*)>", 
                               "<$2$3 data-line=\"$1\">");
    }
    
    /**
     * 包装HTML内容（添加CSS样式）
     */
    public String wrapHtml(String content) {
        return "<!DOCTYPE html>\\n" +
               "<html>\\n" +
               "<head>\\n" +
               "    <meta charset=\"UTF-8\">\\n" +
               "    <style>\\n" +
               "        body {\\n" +
               "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\\n" +
               "            line-height: 1.6;\\n" +
               "            color: #24292e;\\n" +
               "            max-width: 980px;\\n" +
               "            margin: 0 auto;\\n" +
               "            padding: 20px;\\n" +
               "            background-color: #ffffff;\\n" +
               "        }\\n" +
               "        h1, h2, h3, h4, h5, h6 {\\n" +
               "            margin-top: 24px;\\n" +
               "            margin-bottom: 16px;\\n" +
               "            font-weight: 600;\\n" +
               "            line-height: 1.25;\\n" +
               "        }\\n" +
               "        h1 {\\n" +
               "            font-size: 2em;\\n" +
               "            border-bottom: 1px solid #eaecef;\\n" +
               "            padding-bottom: 0.3em;\\n" +
               "        }\\n" +
               "        h2 {\\n" +
               "            font-size: 1.5em;\\n" +
               "            border-bottom: 1px solid #eaecef;\\n" +
               "            padding-bottom: 0.3em;\\n" +
               "        }\\n" +
               "        code {\\n" +
               "            background-color: rgba(27,31,35,0.05);\\n" +
               "            border-radius: 3px;\\n" +
               "            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;\\n" +
               "            font-size: 85%;\\n" +
               "            margin: 0;\\n" +
               "            padding: 0.2em 0.4em;\\n" +
               "        }\\n" +
               "        pre {\\n" +
               "            background-color: #f6f8fa;\\n" +
               "            border-radius: 3px;\\n" +
               "            font-size: 85%;\\n" +
               "            line-height: 1.45;\\n" +
               "            overflow: auto;\\n" +
               "            padding: 16px;\\n" +
               "        }\\n" +
               "        pre code {\\n" +
               "            background-color: transparent;\\n" +
               "            border: 0;\\n" +
               "            display: inline;\\n" +
               "            line-height: inherit;\\n" +
               "            margin: 0;\\n" +
               "            overflow: visible;\\n" +
               "            padding: 0;\\n" +
               "            word-wrap: normal;\\n" +
               "        }\\n" +
               "        table {\\n" +
               "            border-collapse: collapse;\\n" +
               "            border-spacing: 0;\\n" +
               "            display: block;\\n" +
               "            width: 100%;\\n" +
               "            overflow: auto;\\n" +
               "        }\\n" +
               "        table th {\\n" +
               "            font-weight: 600;\\n" +
               "        }\\n" +
               "        table th, table td {\\n" +
               "            border: 1px solid #dfe2e5;\\n" +
               "            padding: 6px 13px;\\n" +
               "        }\\n" +
               "        table tr {\\n" +
               "            background-color: #fff;\\n" +
               "            border-top: 1px solid #c6cbd1;\\n" +
               "        }\\n" +
               "        table tr:nth-child(2n) {\\n" +
               "            background-color: #f6f8fa;\\n" +
               "        }\\n" +
               "        blockquote {\\n" +
               "            border-left: 4px solid #dfe2e5;\\n" +
               "            color: #6a737d;\\n" +
               "            padding: 0 15px;\\n" +
               "            margin: 0;\\n" +
               "        }\\n" +
               "        ul, ol {\\n" +
               "            padding-left: 2em;\\n" +
               "        }\\n" +
               "        a {\\n" +
               "            color: #0366d6;\\n" +
               "            text-decoration: none;\\n" +
               "        }\\n" +
               "        a:hover {\\n" +
               "            text-decoration: underline;\\n" +
               "        }\\n" +
               "    </style>\\n" +
               "</head>\\n" +
               "<body>\\n" +
               content +
               "\\n</body>\\n" +
               "</html>";
    }
}
