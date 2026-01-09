package com.opencgl.base.controls;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.CssUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;


/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/07/28 17:10
 * @since v9.0
 */

public class CustomTextArea extends CodeArea {
    private static final Logger logger = LoggerFactory.getLogger(CustomTextArea.class);
    private static final String[] JSON_KEYWORDS = new String[]{"true", "false", "null"};
    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", JSON_KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "[()]";
    private static final String BRACE_PATTERN = "[{}]";
    private static final String BRACKET_PATTERN = "[\\[\\]]"; // 注意：在字符组内部，左方括号需要转义
    private static final String SEMICOLON_PATTERN = ";"; // 分号不需要修改，因为它本身就是单个字符
    private static final String STRING_PATTERN = "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

    private final ToggleButton wrapTextToggle;
    private final ToggleButton formatButton;
    private boolean wrapLayoutRefreshPending;
    private javafx.beans.value.ChangeListener<Number> parentWidthListener;

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

    static {
        ThemeManager.getInstance().addGlobalStylesheet("/com/opencgl/base/css/CustomTextArea.css");
    }

    public CustomTextArea() {
        // 先清除父类 CodeArea 加载的样式表
        getStylesheets().clear();

        // 移除手动加载逻辑，改为统一由 ThemeManager 管理
        // 这样 CustomTextArea.css 就能够享受到 Dev Mode 的热重载功能
        /*
        ThemeManager.getInstance().getCurrentThemeStylesheets().forEach(sheet -> {
            if (!getStylesheets().contains(sheet)) {
                getStylesheets().add(sheet);
            }
        });

        String customTextAreaCSS = CssUtil.getResourcePath("/com/opencgl/base/css/CustomTextArea.css");
        if (!getStylesheets().contains(customTextAreaCSS)) {
            getStylesheets().add(customTextAreaCSS);
        }
        */

        getStyleClass().add("custom-text-area");

        // Initialize Wrap Text Toggle Button
        wrapTextToggle = new ToggleButton();
        wrapTextToggle.getStyleClass().add("wrap-text-button");
        wrapTextToggle.setTooltip(new Tooltip("Toggle Wrap Text"));
        wrapTextToggle.setText("Wrap");
        wrapTextToggle.setStyle("-fx-font-size: 10px; -fx-padding: 2 5;");
        // This is an overlay. Letting Region manage it makes it participate in
        // RichTextFX's virtual content measurements and can leave paragraph
        // widths stale after toggling wrap or resizing the window.
        wrapTextToggle.setManaged(false);

        // Sync with wrapTextProperty
        wrapTextToggle.selectedProperty().bindBidirectional(wrapTextProperty());

        wrapTextProperty().addListener((obs, oldVal, newVal) -> refreshWrapLayout());
        widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (isWrapText() && !Objects.equals(oldWidth, newWidth)) {
                refreshWrapLayout();
            }
        });
        // When hosted by VirtualizedScrollPane, the viewport can resize while
        // the CodeArea's own width property remains unchanged for a pulse.
        // Observe the immediate Region parent as a second invalidation source.
        parentProperty().addListener((obs, oldParent, newParent) -> {
            detachParentWidthListener(oldParent);
            attachParentWidthListener(newParent);
            if (isWrapText()) refreshWrapLayout();
        });

        getChildren().add(wrapTextToggle);

        // Initialize Format Button
        formatButton = new ToggleButton("Format");
        formatButton.getStyleClass().add("format-button");
        formatButton.setTooltip(new Tooltip("Format JSON/XML"));
        formatButton.setStyle("-fx-font-size: 10px; -fx-padding: 2 5;");
        formatButton.setManaged(false);

        formatButton.setOnAction(e -> formatCode());

        getChildren().add(formatButton);

        // 添加键盘事件监听器
        setupCommentShortcuts();
    }

    private void attachParentWidthListener(Parent parent) {
        if (!(parent instanceof Region region)) return;
        parentWidthListener = (obs, oldWidth, newWidth) -> {
            if (isWrapText() && !Objects.equals(oldWidth, newWidth)) {
                refreshWrapLayout();
            }
        };
        region.widthProperty().addListener(parentWidthListener);
    }

    private void detachParentWidthListener(Parent parent) {
        if (parent instanceof Region region && parentWidthListener != null) {
            region.widthProperty().removeListener(parentWidthListener);
        }
        parentWidthListener = null;
    }

    /**
     * Coalesce wrap-related invalidations into the next JavaFX pulse. Both the
     * editor and its VirtualizedScrollPane parent need a new layout pass: the
     * editor owns paragraph wrapping while the parent owns the viewport width.
     */
    private void refreshWrapLayout() {
        if (wrapLayoutRefreshPending) {
            return;
        }
        wrapLayoutRefreshPending = true;
        Platform.runLater(() -> {
            wrapLayoutRefreshPending = false;
            requestLayout();
            if (getParent() != null) {
                getParent().requestLayout();
            }
            requestFollowCaret();
        });
    }

    private Runnable externalFormatAction;

    /** Explicit setter for external format action (e.g. plugin-provided formatter). */
    public void setExternalFormatAction(Runnable action) {
        this.externalFormatAction = action;
    }

    private void formatCode() {
        if (externalFormatAction != null) {
            externalFormatAction.run();
            return;
        }
        String text = getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String trimmed = text.trim();
        boolean isJson = trimmed.startsWith("{") || trimmed.startsWith("[");
        boolean isXml = trimmed.startsWith("<");

        try {
            if (isJson) {
                // Custom formatter to preserve comments
                String formatted = formatJsonWithComments(text);
                replaceText(formatted);
            }
            else if (isXml) {
                // XML Formatting
                StringReader reader = new StringReader(text);
                StringWriter writer = new StringWriter();
                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer transformer = factory.newTransformer();

                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                transformer.transform(new StreamSource(reader), new StreamResult(writer));
                replaceText(writer.toString());
            }
        }
        catch (Exception e) {
            logger.warn("Format failed", e);
        }
    }

    /**
     * A simple JSON formatter that preserves comments.
     * It tracks indentation and handles strings, but does not validate strict JSON.
     */
    private String formatJsonWithComments(String json) {
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        String indentString = "\t"; // Use tab to match FastJson default
        boolean inString = false;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;

        char[] chars = json.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char nextC = (i + 1 < chars.length) ? chars[i + 1] : '\0';
            char prevC = (i - 1 >= 0) ? chars[i - 1] : '\0';

            // Handle Comments
            if (!inString && !inMultiLineComment && !inSingleLineComment) {
                if (c == '/' && nextC == '/') {
                    inSingleLineComment = true;
                    sb.append(c);
                    continue;
                }
                else if (c == '/' && nextC == '*') {
                    inMultiLineComment = true;
                    sb.append(c);
                    continue;
                }
            }

            if (inSingleLineComment) {
                sb.append(c);
                if (c == '\n') {
                    inSingleLineComment = false;
                    appendIndent(sb, indentLevel, indentString);
                }
                continue;
            }

            if (inMultiLineComment) {
                sb.append(c);
                if (c == '*' && nextC == '/') {
                    inMultiLineComment = false;
                    sb.append(nextC);
                    i++; // Skip /
                    // Force newline after block comment
                    sb.append('\n');
                    // We need to re-apply current indentation for the next token
                    appendIndent(sb, indentLevel, indentString);
                }
                continue;
            }

            // Handle Strings
            if (c == '"' && prevC != '\\') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString) {
                sb.append(c);
                continue;
            }

            // Ignore whitespace outside strings (we will re-add it)
            if (Character.isWhitespace(c)) {
                continue;
            }

            // Formatting Logic
            switch (c) {
                case '{':
                case '[':
                    sb.append(c);
                    sb.append('\n');
                    indentLevel++;
                    appendIndent(sb, indentLevel, indentString);
                    break;
                case '}':
                case ']':
                    sb.append('\n');
                    indentLevel = Math.max(0, indentLevel - 1);
                    appendIndent(sb, indentLevel, indentString);
                    sb.append(c);
                    break;
                case ',':
                    sb.append(c);
                    sb.append('\n');
                    appendIndent(sb, indentLevel, indentString);
                    break;
                case ':':
                    sb.append(c);
                    sb.append(' ');
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    private void appendIndent(StringBuilder sb, int level, String indentStr) {
        sb.append(String.valueOf(indentStr).repeat(Math.max(0, level)));
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        if (wrapTextToggle != null) {
            double w = getWidth();
            double btnW = wrapTextToggle.prefWidth(-1);
            double btnH = wrapTextToggle.prefHeight(-1);

            // Detect Vertical ScrollBar
            double scrollBarWidth = 0;
            Set<Node> scrolls = lookupAll(".scroll-bar");
            for (Node node : scrolls) {
                if (node instanceof ScrollBar) {
                    ScrollBar bar = (ScrollBar) node;
                    if (bar.getOrientation() == javafx.geometry.Orientation.VERTICAL && bar.isVisible()) {
                        scrollBarWidth = bar.getWidth();
                        break;
                    }
                }
            }

            double rightPadding = 5 + scrollBarWidth;
            double x = w - btnW - rightPadding;
            double y = 2; // Top padding

            wrapTextToggle.resizeRelocate(x, y, btnW, btnH);
            wrapTextToggle.toFront();

            if (formatButton != null) {
                double fmtBtnW = formatButton.prefWidth(-1);
                double fmtBtnH = formatButton.prefHeight(-1);

                double fmtX = x - fmtBtnW - 5;
                formatButton.resizeRelocate(fmtX, y, fmtBtnW, fmtBtnH);
                formatButton.toFront();
            }
        }
    }

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
        String safeText = text == null ? "" : text;
        // Normalize line separators to \n
        safeText = safeText.replace("\r\n", "\n").replace("\r", "\n");
        String finalSafeText = safeText;
        Platform.runLater(() -> replaceText(finalSafeText));
        this.requestLayout();
    }

    public String getNonAnnotationText() {
        String text = getText();

        // Normalize line separators
        text = text.replace("\r\n", "\n").replace("\r", "\n");

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
                }
                else if (c == '"' && inString) {
                    inString = false;
                }
                else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/' && !inString) {
                    commentIndex = i;
                    break;
                }
            }

            if (commentIndex >= 0) {
                result.append(line.substring(0, commentIndex));
            }
            else {
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
            // 检测 Cmd+/ 或 Ctrl+/ 单行注释切换 (macOS 使用 Cmd，Windows/Linux 使用 Ctrl)
            if ((event.isControlDown() || event.isMetaDown()) && !event.isShiftDown() &&
                (event.getCode() == KeyCode.SLASH || event.getCode() == KeyCode.DIVIDE || event.getText().equals("/"))) {
                event.consume();
                logger.info("Triggering line comment toggle");
                toggleLineComment();
            }
            // 检测 Cmd+Shift+/ 或 Ctrl+Shift+/ 块注释切换
            else if ((event.isControlDown() || event.isMetaDown()) && event.isShiftDown() &&
                (event.getCode() == KeyCode.SLASH || event.getCode() == KeyCode.DIVIDE || event.getText().equals("/"))) {
                event.consume();
                logger.info("Triggering block comment toggle");
                toggleBlockComment();
            }
        });
    }

    /**
     * 切换单行注释 (支持多行)
     */
    private void toggleLineComment() {
        String text = getText();
        int selectionStart = getSelection().getStart();
        int selectionEnd = getSelection().getEnd();

        // 1. 确定处理范围：扩展至完整行
        int startPos = getCurrentLineStart(selectionStart);
        int endPos = getCurrentLineEnd(selectionEnd);

        // 如果选中范围没有跨越新行且结束位置就在行首，通常不应包含该行，除非它是唯一选择
        if (selectionEnd > selectionStart && selectionEnd == startPos && endPos > startPos) {
            // 这种情况：光标在下一行的开头，通常不处理下一行
            endPos = selectionEnd;
        }

        if (startPos >= endPos) {
            // 空行或异常
            return;
        }

        String targetText = text.substring(startPos, endPos);
        String[] lines = targetText.split("\n", -1); // 保留空行

        // 2. 判断是全部注释还是全部取消
        // 逻辑：只有当所有非空行都已经被注释时，才执行取消注释；否则执行全部注释
        boolean allCommented = true;
        boolean hasContent = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                hasContent = true;
                if (!trimmed.startsWith("//")) {
                    allCommented = false;
                    break;
                }
            }
        }

        if (!hasContent) {
            // 全是空行，默认当做未注释处理，添加注释符 //
            allCommented = false;
        }

        // 3. 构建新文本
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (allCommented) {
                // 取消注释
                // 移除第一个 // 和紧随其后的一个空格（如果有）
                // 使用 replaceFirst 只替换第一个匹配项
                // 也要保留原有缩进
                int commentIdx = line.indexOf("//");
                if (commentIdx >= 0) {
                    String before = line.substring(0, commentIdx);
                    String after = line.substring(commentIdx + 2);
                    if (after.startsWith(" ")) {
                        after = after.substring(1);
                    }
                    sb.append(before).append(after);
                }
                else {
                    sb.append(line);
                }
            }
            else {
                // 添加注释
                // 保持缩进
                // 如果是空行，直接加 // 
                if (line.trim().isEmpty()) {
                    sb.append("// ");
                }
                else {
                    String indentation = getIndentation(line);
                    String content = line.substring(indentation.length());
                    sb.append(indentation).append("// ").append(content);
                }
            }

            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }

        // 4. 替换文本
        String newText = sb.toString();
        replaceText(startPos, endPos, newText);

        // 5. 恢复选区 (尽量合理)
        int lengthDiff = newText.length() - targetText.length();
        // 通常我们选中整个区域
        selectRange(startPos, endPos + lengthDiff);
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
        }
        else {
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
            }
            else {
                break;
            }
        }
        return indentation.toString();
    }

    // PromptText Property
    private final StringProperty promptText = new SimpleStringProperty(this, "promptText", "") {
        @Override
        protected void invalidated() {
            updatePlaceholder();
        }
    };

    public final StringProperty promptTextProperty() {
        return promptText;
    }

    public final String getPromptText() {
        return promptText.get();
    }

    public final void setPromptText(String value) {
        promptText.set(value);
    }

    private void updatePlaceholder() {
        String text = getPromptText();
        if (text != null && !text.isEmpty()) {
            javafx.scene.control.Label placeholder = new javafx.scene.control.Label(text);
            placeholder.setStyle("-fx-text-fill: gray; -fx-padding: 5;");
            placeholder.setWrapText(true);
            this.setPlaceholder(placeholder);
        }
        else {
            this.setPlaceholder(null);
        }
    }
}
