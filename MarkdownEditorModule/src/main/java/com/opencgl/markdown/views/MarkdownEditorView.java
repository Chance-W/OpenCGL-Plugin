package com.opencgl.markdown.views;

import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import org.fxmisc.richtext.CodeArea;

/**
 * Markdown编辑器视图基类
 */
public class MarkdownEditorView {
    public BorderPane rootPane;
    
    // 工具栏
    public Button newButton;
    public Button openButton;
    public Button saveButton;
    public Button boldButton;
    public Button italicButton;
    public Button codeButton;
    
    // 编辑器和预览
    public Label markdownLabel;
    public Label previewLabel;
    public SplitPane splitPane;
    public CodeArea markdownEditor;
    public WebView previewWebView;
    
    // 状态栏
    public Label statusLabel;
    public Label wordCountLabel;
}
