package com.opencgl.base.controls;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.input.KeyCode;


/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/07/28 17:10
 * @since v9.0
 */

public class CustomTextArea extends CodeArea {

    private static final String[] JSON_KEYWORDS = new String[]{"true", "false", "null"};
    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", JSON_KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "[()]";
    private static final String BRACE_PATTERN = "[{}]";
    private static final String BRACKET_PATTERN = "[\\[\\]]"; // 注意：在字符组内部，左方括号需要转义
    private static final String SEMICOLON_PATTERN = ";"; // 分号不需要修改，因为它本身就是单个字符
    private static final String STRING_PATTERN = "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

    public boolean isFormatDrawing() {
        return formatDrawing.get();
    }

    public SimpleBooleanProperty formatDrawingProperty() {
        return formatDrawing;
    }

    public void setFormatDrawing(boolean formatDrawing) {
        this.formatDrawing.set(formatDrawing);
    }

    private final SimpleBooleanProperty formatDrawing = new SimpleBooleanProperty(true);

    private static final Pattern PATTERN = Pattern.compile(
        "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
            + "|(?<PAREN>" + PAREN_PATTERN + ")"
            + "|(?<BRACE>" + BRACE_PATTERN + ")"
            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
            + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
            + "|(?<STRING>" + STRING_PATTERN + ")"
            + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );

    public CustomTextArea() {
        getStyleClass().add("custom-text-area");
        getStylesheets().setAll(Objects.requireNonNull(this
                .getClass()
                .getResource("/com/opencgl/base/css/CustomTextArea.css"))
            .toExternalForm());
        
        // 添加键盘事件监听器
        setupCommentShortcuts();
    }

    /**
     * @Override public void replaceText(int start, int end, String text) {
     * Platform.runLater(() -> {
     * if (start >= 0 && start <= getLength() && end >= 0 && end <= getLength() && start <= end) {
     * super.replaceText(start, end, text);
     * if (!text.isEmpty()) {
     * computeAndSetBranches(0, getLength());
     * }
     * }
     * });
     * }
     * @Override public void insertText(int index, String text) {
     * Platform.runLater(() -> {
     * super.insertText(index, text);
     * if (!text.isEmpty()) {
     * computeAndSetBranches(0, getLength());
     * }
     * });
     * }
     * @Override public void deleteText(int start, int end) {
     * Platform.runLater(() -> {
     * super.deleteText(start, end);
     * if (!getText().isEmpty()) {
     * computeAndSetBranches(0, getLength());
     * }
     * });
     * }
     */

    @Override
    public void replaceText(int start, int end, String text) {
        if (start >= 0 && start <= getLength() && end >= 0 && end <= getLength() && start <= end) {
            super.replaceText(start, end, text);
            if (!text.isEmpty()) {
                // 注意：移除 Platform.runLater，直接在当前线程中计算和应用高亮
                computeAndSetBranches(getLength());
            }
        }
    }

    @Override
    public void insertText(int index, String text) {
        super.insertText(index, text);
        if (!text.isEmpty()) {
            // 注意：移除 Platform.runLater，直接在当前线程中计算和应用高亮
            computeAndSetBranches(getLength());
        }
    }

    @Override
    public void deleteText(int start, int end) {
        super.deleteText(start, end);
        if (!getText().isEmpty()) {
            // 注意：移除 Platform.runLater，直接在当前线程中计算和应用高亮
            computeAndSetBranches(getLength());
        }
    }

    /**
     * private void computeAndSetBranches(int start, int length) {
     * int end = start + length;
     * setStyleSpans(start, computeHighlighting(getText().substring(start, end)));
     * }
     */

    private void computeAndSetBranches(int length) {
        if (!formatDrawing.get()) {
            return;
        }
        setStyleSpans(0, computeHighlighting(getText().substring(0, length)));
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder
            = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("PAREN") != null ? "paren" :
                        matcher.group("BRACE") != null ? "brace" :
                            matcher.group("BRACKET") != null ? "bracket" :
                                matcher.group("SEMICOLON") != null ? "semicolon" :
                                    matcher.group("STRING") != null ? "string" :
                                        matcher.group("COMMENT") != null ? "comment" :
                                            "";
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    public void setText(String text) {
        Platform.runLater(() -> replaceText(text));
        this.requestLayout();
    }

    public String getNonAnnotationText() {
        String text = getText();
        
        // 处理块注释 /* ... */ (包括多行)
        text = text.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        
        // 处理单行注释 // (但保留字符串中的 //)
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            // 检查是否在字符串中
            boolean inString = false;
            boolean escaped = false;
            int commentIndex = -1;
            
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"' && !inString) {
                    inString = true;
                } else if (c == '"' && inString) {
                    inString = false;
                } else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/' && !inString) {
                    commentIndex = i;
                    break;
                }
            }
            
            if (commentIndex >= 0) {
                result.append(line.substring(0, commentIndex));
            } else {
                result.append(line);
            }
            result.append("\n");
        }
        
        return result.toString().trim();
    }

    /**
     * 设置注释快捷键
     */
    private void setupCommentShortcuts() {
        setOnKeyPressed(event -> {
            // 调试信息
            System.out.println("Key pressed: " + event.getCode() + ", Ctrl: " + event.isControlDown() + 
                             ", Cmd: " + event.isMetaDown() + ", Shift: " + event.isShiftDown());
            System.out.println("Event text: '" + event.getText() + "'");
            
            // 检测 Cmd+/ 或 Ctrl+/ 单行注释切换 (macOS 使用 Cmd，Windows/Linux 使用 Ctrl)
            if ((event.isControlDown() || event.isMetaDown()) && !event.isShiftDown() && 
                (event.getCode() == KeyCode.SLASH || event.getCode() == KeyCode.DIVIDE || event.getText().equals("/"))) {
                event.consume();
                System.out.println("Triggering line comment toggle");
                toggleLineComment();
            }
            // 检测 Cmd+Shift+/ 或 Ctrl+Shift+/ 块注释切换
            else if ((event.isControlDown() || event.isMetaDown()) && event.isShiftDown() && 
                     (event.getCode() == KeyCode.SLASH || event.getCode() == KeyCode.DIVIDE || event.getText().equals("/"))) {
                event.consume();
                System.out.println("Triggering block comment toggle");
                toggleBlockComment();
            }
        });
    }

    /**
     * 切换单行注释
     */
    private void toggleLineComment() {
        String text = getText();
        int caretPosition = getCaretPosition();
        
        // 获取当前行
        int lineStart = getCurrentLineStart(caretPosition);
        int lineEnd = getCurrentLineEnd(caretPosition);
        String currentLine = text.substring(lineStart, lineEnd);
        
        // 检查当前行是否已经被注释
        String trimmedLine = currentLine.trim();
        boolean isCommented = trimmedLine.startsWith("//");
        
        if (isCommented) {
            // 取消注释
            String uncommentedLine = currentLine.replaceFirst("^\\s*//\\s?", "");
            replaceText(lineStart, lineEnd, uncommentedLine);
            // 调整光标位置
            int newCaretPos = lineStart + uncommentedLine.length();
            if (newCaretPos <= getLength()) {
                moveTo(newCaretPos);
            }
        } else {
            // 添加注释
            String indentation = getIndentation(currentLine);
            String commentedLine = indentation + "// " + currentLine.substring(indentation.length());
            replaceText(lineStart, lineEnd, commentedLine);
            // 调整光标位置
            int newCaretPos = lineStart + commentedLine.length();
            if (newCaretPos <= getLength()) {
                moveTo(newCaretPos);
            }
        }
    }

    /**
     * 切换块注释
     */
    private void toggleBlockComment() {
        String text = getText();
        int startPos = getSelection().getStart();
        int endPos = getSelection().getEnd();
        
        // 如果没有选中文本，则选中当前行
        if (startPos == endPos) {
            int lineStart = getCurrentLineStart(startPos);
            int lineEnd = getCurrentLineEnd(startPos);
            selectRange(lineStart, lineEnd);
            startPos = lineStart;
            endPos = lineEnd;
        }
        
        String selectedText = text.substring(startPos, endPos);
        
        // 检查选中文本是否已经被块注释包围
        boolean isBlockCommented = selectedText.trim().startsWith("/*") && selectedText.trim().endsWith("*/");
        
        if (isBlockCommented) {
            // 取消块注释
            String uncommentedText = selectedText.trim();
            if (uncommentedText.startsWith("/*") && uncommentedText.endsWith("*/")) {
                uncommentedText = uncommentedText.substring(2, uncommentedText.length() - 2);
            }
            replaceText(startPos, endPos, uncommentedText);
            selectRange(startPos, startPos + uncommentedText.length());
        } else {
            // 添加块注释
            String commentedText = "/* " + selectedText + " */";
            replaceText(startPos, endPos, commentedText);
            selectRange(startPos, startPos + commentedText.length());
        }
    }

    /**
     * 获取当前行的开始位置
     */
    private int getCurrentLineStart(int position) {
        String text = getText();
        int lineStart = position;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        return lineStart;
    }

    /**
     * 获取当前行的结束位置
     */
    private int getCurrentLineEnd(int position) {
        String text = getText();
        int lineEnd = position;
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') {
            lineEnd++;
        }
        return lineEnd;
    }

    /**
     * 获取行的缩进
     */
    private String getIndentation(String line) {
        StringBuilder indentation = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indentation.append(c);
            } else {
                break;
            }
        }
        return indentation.toString();
    }

}
