package com.opencgl.docconverter.views;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import org.fxmisc.richtext.CodeArea;

/**
 * 文档转换器视图基类
 */
public class DocConverterView {
    public BorderPane rootPane;

    public Button newButton;
    public Button openButton;
    public Button saveButton;
    public Button convertButton;
    public Button boldButton;
    public Button italicButton;
    public Button codeButton;

    public Label markdownLabel;
    public Label previewLabel;
    public SplitPane splitPane;
    public CodeArea markdownEditor;
    public WebView previewWebView;

    public Label statusLabel;
    public Label wordCountLabel;
}
