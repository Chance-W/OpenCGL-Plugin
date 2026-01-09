package com.opencgl.components;

import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增强的代码编辑器
 * 支持 JSON/XML 语法高亮、注释高亮、行号、自动格式化
 */
public class CodeEditor extends VBox {
    
    // 语言类型
    public enum Language {
        JSON, XML, PLAIN
    }
    
    private final CodeArea codeArea;
    private Language currentLanguage = Language.JSON;
    
    // JSON 语法正则
    private static final Pattern JSON_PATTERN = Pattern.compile(
        "(?<COMMENT>//[^\n]*|/\\*.*?\\*/)" +          // 注释（虽然标准JSON不支持，但很多工具支持）
        "|(?<BRACE>[{}])" +                            // 大括号
        "|(?<BRACKET>[\\[\\]])" +                      // 方括号
        "|(?<STRING>\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\")" + // 字符串
        "|(?<NUMBER>-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?)" + // 数字
        "|(?<BOOL>true|false)" +                       // 布尔值
        "|(?<NULL>null)" +                             // null
        "|(?<COLON>:)" +                               // 冒号
        "|(?<COMMA>,)",                                 // 逗号
        Pattern.DOTALL
    );
    
    // XML 语法正则
    private static final Pattern XML_PATTERN = Pattern.compile(
        "(?<COMMENT><!--[\\s\\S]*?-->)" +              // XML注释
        "|(?<PROLOG><\\?[\\s\\S]*?\\?>)" +             // XML声明
        "|(?<CDATA><!\\[CDATA\\[[\\s\\S]*?\\]\\]>)" +  // CDATA
        "|(?<TAGOPENCLOSE></[^>]+>)" +                 // 关闭标签
        "|(?<TAGOPEN><[^/>]+>)" +                      // 开始标签
        "|(?<TAGSELFCLOSE><[^>]+/>)" +                 // 自闭合标签
        "|(?<STRING>\"[^\"]*\")" +                     // 属性值
        "|(?<ENTITY>&[^;]+;)",                         // 实体
        Pattern.DOTALL
    );
    
    public CodeEditor() {
        setSpacing(0);
        setPadding(new Insets(0));
        
        codeArea = new CodeArea();
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Menlo', monospace; -fx-font-size: 13px;");
        
        // 行号
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        
        // 语法高亮（延迟处理避免性能问题）
        codeArea.multiPlainChanges()
            .successionEnds(Duration.ofMillis(100))
            .subscribe(ignore -> updateHighlighting());
        
        // 快捷键
        setupKeyboardShortcuts();
        
        // 右键菜单
        setupContextMenu();
        
        // 样式类
        codeArea.getStyleClass().add("code-editor");
        
        // 滚动面板
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        getChildren().add(scrollPane);
        
        // 应用样式
        applyStyles();
    }
    
    private void applyStyles() {
        getStylesheets().add(getClass().getResource("/com/opencgl/css/code-editor.css").toExternalForm());
    }
    
    private void setupKeyboardShortcuts() {
        // Ctrl+Alt+L 格式化
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.isAltDown() && event.getCode() == KeyCode.L) {
                format();
                event.consume();
            }
            // Ctrl+/ 切换注释
            else if (event.isControlDown() && event.getCode() == KeyCode.SLASH) {
                toggleComment();
                event.consume();
            }
            // Tab 缩进
            else if (event.getCode() == KeyCode.TAB) {
                if (event.isShiftDown()) {
                    unindent();
                } else {
                    indent();
                }
                event.consume();
            }
        });
    }
    
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem formatItem = new MenuItem("格式化 (Ctrl+Alt+L)");
        formatItem.setOnAction(e -> format());
        
        MenuItem commentItem = new MenuItem("切换注释 (Ctrl+/)");
        commentItem.setOnAction(e -> toggleComment());
        
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(codeArea.getSelectedText());
            clipboard.setContent(content);
        });
        
        MenuItem pasteItem = new MenuItem("粘贴");
        pasteItem.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasString()) {
                codeArea.replaceSelection(clipboard.getString());
            }
        });
        
        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setOnAction(e -> codeArea.selectAll());
        
        contextMenu.getItems().addAll(
            formatItem, commentItem,
            new SeparatorMenuItem(),
            copyItem, pasteItem, selectAllItem
        );
        
        codeArea.setContextMenu(contextMenu);
    }
    
    private void updateHighlighting() {
        String text = codeArea.getText();
        if (text.isEmpty()) {
            return;
        }
        
        StyleSpans<Collection<String>> styles;
        switch (currentLanguage) {
            case JSON:
                styles = computeJsonHighlighting(text);
                break;
            case XML:
                styles = computeXmlHighlighting(text);
                break;
            default:
                styles = computePlainHighlighting(text);
                break;
        }
        
        codeArea.setStyleSpans(0, styles);
    }
    
    private StyleSpans<Collection<String>> computeJsonHighlighting(String text) {
        Matcher matcher = JSON_PATTERN.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        
        while (matcher.find()) {
            String styleClass = 
                matcher.group("COMMENT") != null ? "comment" :
                matcher.group("BRACE") != null ? "brace" :
                matcher.group("BRACKET") != null ? "bracket" :
                matcher.group("STRING") != null ? "string" :
                matcher.group("NUMBER") != null ? "number" :
                matcher.group("BOOL") != null ? "keyword" :
                matcher.group("NULL") != null ? "keyword" :
                matcher.group("COLON") != null ? "colon" :
                matcher.group("COMMA") != null ? "comma" :
                null;
            
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        
        return spansBuilder.create();
    }
    
    private StyleSpans<Collection<String>> computeXmlHighlighting(String text) {
        Matcher matcher = XML_PATTERN.matcher(text);
        int lastEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        
        while (matcher.find()) {
            String styleClass = 
                matcher.group("COMMENT") != null ? "comment" :
                matcher.group("PROLOG") != null ? "prolog" :
                matcher.group("CDATA") != null ? "cdata" :
                matcher.group("TAGOPENCLOSE") != null ? "tag" :
                matcher.group("TAGOPEN") != null ? "tag" :
                matcher.group("TAGSELFCLOSE") != null ? "tag" :
                matcher.group("STRING") != null ? "string" :
                matcher.group("ENTITY") != null ? "entity" :
                null;
            
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        
        return spansBuilder.create();
    }
    
    private StyleSpans<Collection<String>> computePlainHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        spansBuilder.add(Collections.emptyList(), text.length());
        return spansBuilder.create();
    }
    
    /**
     * 格式化代码
     */
    public void format() {
        String text = codeArea.getText();
        if (text.isEmpty()) return;
        
        try {
            String formatted;
            switch (currentLanguage) {
                case JSON:
                    formatted = formatJson(text);
                    break;
                case XML:
                    formatted = formatXml(text);
                    break;
                default:
                    formatted = text;
                    break;
            }
            codeArea.replaceText(formatted);
        } catch (Exception e) {
            // 格式化失败，保持原样
        }
    }
    
    private String formatJson(String json) {
        // 简单的 JSON 格式化
        StringBuilder result = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
                result.append(c);
            } else if (!inString) {
                switch (c) {
                    case '{':
                    case '[':
                        result.append(c).append('\n');
                        indent++;
                        result.append("  ".repeat(indent));
                        break;
                    case '}':
                    case ']':
                        result.append('\n');
                        indent--;
                        result.append("  ".repeat(indent)).append(c);
                        break;
                    case ',':
                        result.append(c).append('\n').append("  ".repeat(indent));
                        break;
                    case ':':
                        result.append(c).append(' ');
                        break;
                    case ' ':
                    case '\n':
                    case '\r':
                    case '\t':
                        // 跳过空白
                        break;
                    default:
                        result.append(c);
                }
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    private String formatXml(String xml) {
        // 简单的 XML 格式化
        StringBuilder result = new StringBuilder();
        int indent = 0;
        String[] lines = xml.replaceAll(">\\s*<", ">\n<").split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.startsWith("</")) {
                indent--;
            }
            
            result.append("  ".repeat(Math.max(0, indent))).append(line).append('\n');
            
            if (line.startsWith("<") && !line.startsWith("</") && !line.startsWith("<?") 
                && !line.startsWith("<!") && !line.endsWith("/>") && !line.contains("</")) {
                indent++;
            }
        }
        
        return result.toString().trim();
    }
    
    /**
     * 切换注释
     */
    public void toggleComment() {
        int caretPos = codeArea.getCaretPosition();
        int paragraph = codeArea.getCurrentParagraph();
        String lineText = codeArea.getParagraph(paragraph).getText();
        
        String trimmed = lineText.trim();
        String newLine;
        
        switch (currentLanguage) {
            case JSON:
                // JSON 使用 // 注释
                if (trimmed.startsWith("//")) {
                    newLine = lineText.replaceFirst("//\\s?", "");
                } else {
                    int leadingSpaces = lineText.indexOf(lineText.trim());
                    newLine = lineText.substring(0, leadingSpaces) + "// " + trimmed;
                }
                break;
            case XML:
                // XML 使用 <!-- --> 注释
                if (trimmed.startsWith("<!--") && trimmed.endsWith("-->")) {
                    newLine = trimmed.substring(4, trimmed.length() - 3).trim();
                } else {
                    newLine = "<!-- " + trimmed + " -->";
                }
                break;
            default:
                newLine = lineText;
        }
        
        int lineStart = codeArea.getAbsolutePosition(paragraph, 0);
        int lineEnd = lineStart + lineText.length();
        codeArea.replaceText(lineStart, lineEnd, newLine);
    }
    
    /**
     * 增加缩进
     */
    public void indent() {
        int caretPos = codeArea.getCaretPosition();
        codeArea.insertText(caretPos, "  ");
    }
    
    /**
     * 减少缩进
     */
    public void unindent() {
        int paragraph = codeArea.getCurrentParagraph();
        String lineText = codeArea.getParagraph(paragraph).getText();
        
        if (lineText.startsWith("  ")) {
            int lineStart = codeArea.getAbsolutePosition(paragraph, 0);
            codeArea.replaceText(lineStart, lineStart + 2, "");
        }
    }
    
    // Getters and Setters
    
    public String getText() {
        return codeArea.getText();
    }
    
    public void setText(String text) {
        codeArea.replaceText(text != null ? text : "");
    }
    
    public void clear() {
        codeArea.clear();
    }
    
    public Language getLanguage() {
        return currentLanguage;
    }
    
    public void setLanguage(Language language) {
        this.currentLanguage = language;
        updateHighlighting();
    }
    
    public CodeArea getCodeArea() {
        return codeArea;
    }
}
