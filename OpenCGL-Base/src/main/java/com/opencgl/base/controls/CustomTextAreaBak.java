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


/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/07/28 17:10
 * @since v9.0
 */

public class CustomTextAreaBak extends CodeArea {

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

    public CustomTextAreaBak() {
        getStyleClass().add("custom-text-area");
        getStylesheets().setAll(Objects.requireNonNull(this
                .getClass()
                .getResource("/com/opencgl/base/css/CustomTextArea.css"))
            .toExternalForm());
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
        return getText()
            .replaceAll("^(?!(https?://))[^\n]*//[^\\n]*", "") // Remove comments starting with //
            .replaceAll("/\\*[^*]*\\*+([^/*][^*]*\\*+)*/", "") // Remove block comments /** ... **/
            .trim();
    }

}
