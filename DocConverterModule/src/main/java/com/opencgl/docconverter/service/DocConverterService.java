package com.opencgl.docconverter.service;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Arrays;
import java.util.List;

/**
 * 文档转换服务：Markdown → HTML，含代码内替换与主题相关样式包装。
 */
public class DocConverterService {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public DocConverterService() {
        List<Extension> extensions = Arrays.asList(
            TablesExtension.create(),
            StrikethroughExtension.create()
        );
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    /**
     * Markdown 转 HTML，并在代码中做自定义替换（如占位符、变量等）。
     */
    public String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String normalized = applyCodeReplacements(markdown);
        Node document = parser.parse(normalized);
        return renderer.render(document);
    }

    /**
     * 转换前的代码内替换（在 Markdown 字符串上做替换，再交给 commonmark 解析）。
     * 可按需扩展：占位符、自定义语法、变量等。
     */
    private String applyCodeReplacements(String markdown) {
        if (markdown == null) return "";
        String s = markdown;
        // 示例：将 [[BR]] 替换为换行（在部分渲染器里可作为换行）
        s = s.replace("[[BR]]", "\n\n");
        // 示例：高亮占位符保留为 <mark>，commonmark 会转义；若需保留可后处理 HTML
        // 这里仅做简单替换，复杂逻辑可在此扩展
        return s;
    }

    /**
     * 对渲染后的 HTML 做后处理替换（如注入行号、高亮等）。
     */
    public String postProcessHtml(String html) {
        if (html == null || html.isEmpty()) return html;
        // 示例：代码块添加 data-language 等
        return html;
    }

    /**
     * 包装为完整 HTML 页面，支持主题（亮/暗）变量，便于主题适配。
     */
    public String wrapHtml(String content, boolean darkTheme) {
        String bg = darkTheme ? "#1e1e1e" : "#ffffff";
        String text = darkTheme ? "#d4d4d4" : "#24292e";
        String codeBg = darkTheme ? "#2d2d2d" : "#f6f8fa";
        String border = darkTheme ? "#444" : "#eaecef";
        String link = darkTheme ? "#58a6ff" : "#0366d6";

        String bodyStyle = String.format(
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
            "line-height: 1.6; color: %s; max-width: 980px; margin: 0 auto; padding: 20px; background-color: %s; }",
            text, bg);
        String headingStyle = "h1, h2, h3, h4, h5, h6 { margin-top: 24px; margin-bottom: 16px; font-weight: 600; } " +
            "h1, h2 { border-bottom: 1px solid " + border + "; padding-bottom: 0.3em; }";
        String codeStyle = String.format(
            "code { background-color: %s; border-radius: 3px; font-family: Consolas, Monaco, monospace; " +
            "font-size: 85%%; padding: 0.2em 0.4em; } " +
            "pre { background-color: %s; border-radius: 3px; padding: 16px; overflow: auto; } " +
            "pre code { background: transparent; padding: 0; }",
            codeBg, codeBg);
        String tableStyle = "table { border-collapse: collapse; width: 100%%; } " +
            "th, td { border: 1px solid " + border + "; padding: 6px 13px; } " +
            "tr:nth-child(even) { background-color: " + (darkTheme ? "#252526" : "#f6f8fa") + "; }";
        String aStyle = "a { color: " + link + "; text-decoration: none; } a:hover { text-decoration: underline; }";
        String blockquoteStyle = "blockquote { border-left: 4px solid " + border + "; margin: 0; padding: 0 15px; color: " + (darkTheme ? "#8b949e" : "#6a737d") + "; }";

        return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n<style>\n" +
            bodyStyle + "\n" + headingStyle + "\n" + codeStyle + "\n" + tableStyle + "\n" + aStyle + "\n" + blockquoteStyle +
            "\n</style>\n</head>\n<body>\n" + content + "\n</body>\n</html>";
    }
}
