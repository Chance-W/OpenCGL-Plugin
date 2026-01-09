package com.opencgl.components;

import java.nio.file.Files;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

/**
 * 响应预览面板
 * 支持 Pretty/Raw/Preview 三种模式
 */
public class ResponsePreviewPane extends VBox {
    
    public enum ViewMode {
        PRETTY("Pretty"),
        RAW("Raw"),
        PREVIEW("Preview");
        
        private final String label;
        ViewMode(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final StackPane contentPane = new StackPane();
    private final CodeEditor prettyEditor;
    private final TextArea rawTextArea;
    private final javafx.scene.web.WebView previewWebView;
    
    private String currentContent = "";
    private String contentType = "application/json";
    private ViewMode currentMode = ViewMode.PRETTY;
    
    public ResponsePreviewPane() {
        setSpacing(0);
        
        // 模式切换工具栏
        HBox toolbar = createToolbar();
        
        // Pretty 模式 - 语法高亮
        prettyEditor = new CodeEditor();
        prettyEditor.getCodeArea().setEditable(false);
        
        // Raw 模式 - 纯文本
        rawTextArea = new TextArea();
        rawTextArea.setEditable(false);
        rawTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 13px;");
        rawTextArea.setWrapText(true);
        
        // Preview 模式 - HTML 渲染
        previewWebView = new WebView();
        
        VBox.setVgrow(contentPane, Priority.ALWAYS);
        
        getChildren().addAll(toolbar, contentPane);
        
        // 默认显示 Pretty
        showMode(ViewMode.PRETTY);
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(4);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 8, 4, 8));
        toolbar.setStyle("-fx-background-color: #f1f3f4; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        
        for (ViewMode mode : ViewMode.values()) {
            ToggleButton btn = new ToggleButton(mode.getLabel());
            btn.setToggleGroup(modeGroup);
            btn.setStyle("-fx-background-color: transparent; -fx-padding: 4 12; -fx-font-size: 12px;");
            btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    btn.setStyle("-fx-background-color: #1a73e8; -fx-text-fill: white; -fx-padding: 4 12; -fx-font-size: 12px; -fx-background-radius: 4;");
                    showMode(mode);
                } else {
                    btn.setStyle("-fx-background-color: transparent; -fx-padding: 4 12; -fx-font-size: 12px;");
                }
            });
            
            if (mode == ViewMode.PRETTY) {
                btn.setSelected(true);
            }
            
            toolbar.getChildren().add(btn);
        }
        
        // 复制按钮
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button copyBtn = new Button("复制");
        copyBtn.setStyle("-fx-background-color: #e8eaed; -fx-padding: 4 12; -fx-font-size: 11px;");
        copyBtn.setOnAction(e -> copyContent());
        
        Button downloadBtn = new Button("下载");
        downloadBtn.setStyle("-fx-background-color: #e8eaed; -fx-padding: 4 12; -fx-font-size: 11px;");
        downloadBtn.setOnAction(e -> downloadContent());
        
        toolbar.getChildren().addAll(spacer, copyBtn, downloadBtn);
        
        return toolbar;
    }
    
    private void showMode(ViewMode mode) {
        this.currentMode = mode;
        contentPane.getChildren().clear();
        
        switch (mode) {
            case PRETTY:
                updatePrettyView();
                contentPane.getChildren().add(prettyEditor);
                break;
            case RAW:
                rawTextArea.setText(currentContent);
                contentPane.getChildren().add(rawTextArea);
                break;
            case PREVIEW:
                updatePreviewView();
                contentPane.getChildren().add(previewWebView);
                break;
        }
    }
    
    private void updatePrettyView() {
        if (contentType.contains("json")) {
            prettyEditor.setLanguage(CodeEditor.Language.JSON);
            prettyEditor.setText(formatJson(currentContent));
        } else if (contentType.contains("xml")) {
            prettyEditor.setLanguage(CodeEditor.Language.XML);
            prettyEditor.setText(formatXml(currentContent));
        } else {
            prettyEditor.setLanguage(CodeEditor.Language.PLAIN);
            prettyEditor.setText(currentContent);
        }
    }
    
    private void updatePreviewView() {
        if (contentType.contains("html")) {
            previewWebView.getEngine().loadContent(currentContent);
        } else if (contentType.contains("json")) {
            // JSON 渲染为带样式的 HTML
            String html = "<html><head><style>" +
                "body { font-family: Consolas, Monaco, monospace; font-size: 13px; background: #1e1e1e; color: #d4d4d4; padding: 16px; }" +
                "pre { white-space: pre-wrap; word-wrap: break-word; }" +
                "</style></head><body><pre>" + escapeHtml(formatJson(currentContent)) + "</pre></body></html>";
            previewWebView.getEngine().loadContent(html);
        } else {
            previewWebView.getEngine().loadContent("<html><body><pre>" + escapeHtml(currentContent) + "</pre></body></html>");
        }
    }

    public void dispose() {
        previewWebView.getEngine().load("about:blank");
        contentPane.getChildren().clear();
    }
    
    private String formatJson(String json) {
        if (json == null || json.isEmpty()) return json;
        
        try {
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
                        case '{': case '[':
                            result.append(c).append('\n');
                            indent++;
                            result.append("  ".repeat(indent));
                            break;
                        case '}': case ']':
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
                        case ' ': case '\n': case '\r': case '\t':
                            break;
                        default:
                            result.append(c);
                    }
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        } catch (Exception e) {
            return json;
        }
    }
    
    private String formatXml(String xml) {
        if (xml == null || xml.isEmpty()) return xml;
        
        try {
            StringBuilder result = new StringBuilder();
            int indent = 0;
            String[] lines = xml.replaceAll(">\\s*<", ">\n<").split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.startsWith("</")) indent--;
                result.append("  ".repeat(Math.max(0, indent))).append(line).append('\n');
                if (line.startsWith("<") && !line.startsWith("</") && !line.startsWith("<?") 
                    && !line.startsWith("<!") && !line.endsWith("/>") && !line.contains("</")) {
                    indent++;
                }
            }
            return result.toString().trim();
        } catch (Exception e) {
            return xml;
        }
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
    
    private void copyContent() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(currentContent);
        clipboard.setContent(content);
    }
    
    private void downloadContent() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存响应内容");
        
        String ext = "txt";
        if (contentType.contains("json")) ext = "json";
        else if (contentType.contains("xml")) ext = "xml";
        else if (contentType.contains("html")) ext = "html";
        
        fileChooser.setInitialFileName("response." + ext);
        java.io.File file = fileChooser.showSaveDialog(getScene().getWindow());
        
        if (file != null) {
            try {
                Files.writeString(file.toPath(), currentContent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    // Public API
    
    public void setContent(String content, String contentType) {
        this.currentContent = content != null ? content : "";
        this.contentType = contentType != null ? contentType : "text/plain";
        showMode(currentMode);
    }
    
    public void clear() {
        this.currentContent = "";
        showMode(currentMode);
    }
    
    public String getContent() {
        return currentContent;
    }
}
