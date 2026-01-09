package com.opencgl.markdown.controller;

import com.opencgl.markdown.i18n.I18N;
import com.opencgl.markdown.service.MarkdownService;
import com.opencgl.markdown.views.MarkdownEditorView;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.Initializable;
import javafx.scene.web.WebEngine;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Markdown编辑器控制器
 */
public class MarkdownEditorController extends MarkdownEditorView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(MarkdownEditorController.class);
    
    private MarkdownService markdownService;
    private WebEngine webEngine;
    private Timer debounceTimer;
    private File currentFile;
    private volatile boolean disposed;
    private ChangeListener<String> textListener;
    private ChangeListener<Double> scrollListener;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        markdownService = new MarkdownService();
        webEngine = previewWebView.getEngine();
        
        setupEditor();
        bindEvents();
        setupScrollSync();
        initI18n();
        
        // 初始预览
        updatePreview("");
        statusLabel.setText(I18N.get("label.statusReady"));
        wordCountLabel.setText(I18N.get("label.wordCount", 0));
    }

    private void initI18n() {
        if (newButton != null) newButton.textProperty().bind(I18N.getBinding("btn.new"));
        if (openButton != null) openButton.textProperty().bind(I18N.getBinding("btn.open"));
        if (saveButton != null) saveButton.textProperty().bind(I18N.getBinding("btn.save"));
        if (markdownLabel != null) markdownLabel.textProperty().bind(I18N.getBinding("label.markdown"));
        if (previewLabel != null) previewLabel.textProperty().bind(I18N.getBinding("label.preview"));
    }

    private void setupEditor() {
        markdownEditor.setPrefHeight(600);
        markdownEditor.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 14px;");
    }

    private void bindEvents() {
        // 实时预览（防抖动）
        textListener = (obs, oldVal, newVal) -> {
            if (disposed) return;
            if (debounceTimer != null) {
                debounceTimer.cancel();
            }
            debounceTimer = new Timer("markdown-preview-debounce", true);
            debounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        if (disposed) return;
                        updatePreview(newVal);
                        updateWordCount(newVal);
                    });
                }
            }, 300); // 300ms延迟
        };
        markdownEditor.textProperty().addListener(textListener);

        // 文件操作
        newButton.setOnAction(e -> newFile());
        openButton.setOnAction(e -> openFile());
        saveButton.setOnAction(e -> saveFile());

        // 格式化按钮
        boldButton.setOnAction(e -> insertFormat("**", "**"));
        italicButton.setOnAction(e -> insertFormat("*", "*"));
        codeButton.setOnAction(e -> insertFormat("`", "`"));
    }

    private void updatePreview(String markdown) {
        String html = markdownService.markdownToHtml(markdown);
        String wrappedHtml = markdownService.wrapHtml(html);
        
        // 保存当前滚动位置
        double scrollY = 0;
        try {
            Object result = webEngine.executeScript("document.documentElement.scrollTop || document.body.scrollTop");
            if (result instanceof Number) {
                scrollY = ((Number) result).doubleValue();
            }
        } catch (Exception e) {
            // 忽略
        }
        
        final double savedScrollY = scrollY;
        webEngine.loadContent(wrappedHtml);
        
        // 恢复滚动位置
        if (savedScrollY > 0) {
            webEngine.documentProperty().addListener((obs, oldDoc, newDoc) -> {
                if (newDoc != null) {
                    webEngine.executeScript("window.scrollTo(0, " + savedScrollY + ")");
                }
            });
        }
    }
    
    private void setupScrollSync() {
        // 监听CodeArea滚动
        scrollListener = (obs, oldVal, newVal) -> {
            if (!disposed) syncPreviewScroll();
        };
        markdownEditor.estimatedScrollYProperty().addListener(scrollListener);
    }
    
    private void syncPreviewScroll() {
        try {
            // 使用百分比同步（简单可靠）
            double editorScrollY = markdownEditor.getEstimatedScrollY();
            double editorTotalHeight = markdownEditor.getTotalHeightEstimate();
            double editorVisibleHeight = markdownEditor.getHeight();
            
            if (editorTotalHeight <= editorVisibleHeight) {
                return; // 不需要滚动
            }
            
            double scrollPercentage = editorScrollY / (editorTotalHeight - editorVisibleHeight);
            scrollPercentage = Math.max(0, Math.min(1, scrollPercentage));
            
            // 同步到预览窗口
            String script = String.format(
                "var maxScroll = document.documentElement.scrollHeight - document.documentElement.clientHeight;" +
                "window.scrollTo({top: maxScroll * %.4f, behavior: 'auto'});",
                scrollPercentage
            );
            webEngine.executeScript(script);
            
        } catch (Exception e) {
            logger.error("同步滚动失败", e);
        }
    }

    private void updateWordCount(String text) {
        int count = text.isEmpty() ? 0 : text.split("\\s+").length;
        wordCountLabel.setText(I18N.get("label.wordCount", count));
    }

    private void newFile() {
        markdownEditor.clear();
        currentFile = null;
        statusLabel.setText(I18N.get("status.newDoc"));
    }

    private void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("filechooser.openMd"));
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(I18N.get("filter.markdown"), "*.md", "*.markdown")
        );
        
        File file = fileChooser.showOpenDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                markdownEditor.replaceText(content);
                currentFile = file;
                statusLabel.setText(I18N.get("status.opened", file.getName()));
            } catch (IOException e) {
                logger.error("打开文件失败", e);
                statusLabel.setText(I18N.get("status.openFailed"));
            }
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(I18N.get("filechooser.saveMd"));
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18N.get("filter.markdown"), "*.md")
            );
            currentFile = fileChooser.showSaveDialog(rootPane.getScene().getWindow());
        }
        
        if (currentFile != null) {
            try {
                Files.writeString(currentFile.toPath(), markdownEditor.getText());
                statusLabel.setText(I18N.get("status.saved", currentFile.getName()));
            } catch (IOException e) {
                logger.error("保存文件失败", e);
                statusLabel.setText(I18N.get("status.saveFailed"));
            }
        }
    }

    private void insertFormat(String prefix, String suffix) {
        int pos = markdownEditor.getCaretPosition();
        markdownEditor.insertText(pos, prefix + suffix);
        markdownEditor.moveTo(pos + prefix.length());
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (debounceTimer != null) {
            debounceTimer.cancel();
            debounceTimer = null;
        }
        if (textListener != null) {
            markdownEditor.textProperty().removeListener(textListener);
            textListener = null;
        }
        if (scrollListener != null) {
            markdownEditor.estimatedScrollYProperty().removeListener(scrollListener);
            scrollListener = null;
        }
        if (webEngine != null) {
            webEngine.load("about:blank");
            webEngine = null;
        }
        newButton.setOnAction(null);
        openButton.setOnAction(null);
        saveButton.setOnAction(null);
        boldButton.setOnAction(null);
        italicButton.setOnAction(null);
        codeButton.setOnAction(null);
    }
}
