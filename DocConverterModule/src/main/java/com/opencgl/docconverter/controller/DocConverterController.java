package com.opencgl.docconverter.controller;

import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.LoadingMask;
import com.opencgl.docconverter.i18n.I18N;
import com.opencgl.docconverter.service.DocConverterService;
import com.opencgl.docconverter.views.DocConverterView;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 文档转换器控制器：与 Markdown 编辑器一致功能，支持多语言、主题、LoadingMask。
 */
public class DocConverterController extends DocConverterView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(DocConverterController.class);

    private DocConverterService docConverterService;
    private WebEngine webEngine;
    private Timer debounceTimer;
    private File currentFile;
    private final LoadingMask loadingMask = new LoadingMask();
    private final ExecutorService convertExecutor = Executors.newSingleThreadExecutor();
    private Future<?> conversionFuture;
    private volatile boolean disposed;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        docConverterService = new DocConverterService();
        webEngine = previewWebView.getEngine();

        ensureThemeRoot();
        setupEditor();
        bindEvents();
        initI18n();
        registerTheme();

        updatePreview("");
        statusLabel.setText(I18N.get("label.statusReady"));
        wordCountLabel.setText(I18N.get("label.wordCount", 0));
    }

    private void ensureThemeRoot() {
        if (rootPane != null && !rootPane.getStyleClass().contains("root")) {
            rootPane.getStyleClass().add("root");
        }
    }

    private void registerTheme() {
        Scene scene = rootPane != null ? rootPane.getScene() : null;
        if (scene != null) {
            ThemeManager.getInstance().registerScene(scene);
        }
    }

    private void initI18n() {
        if (newButton != null) newButton.textProperty().bind(I18N.getBinding("btn.new"));
        if (openButton != null) openButton.textProperty().bind(I18N.getBinding("btn.open"));
        if (saveButton != null) saveButton.textProperty().bind(I18N.getBinding("btn.save"));
        if (convertButton != null) convertButton.textProperty().bind(I18N.getBinding("btn.convert"));
        if (markdownLabel != null) markdownLabel.textProperty().bind(I18N.getBinding("label.markdown"));
        if (previewLabel != null) previewLabel.textProperty().bind(I18N.getBinding("label.preview"));
    }

    private void setupEditor() {
        if (markdownEditor != null) {
            markdownEditor.setPrefHeight(600);
            markdownEditor.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 14px;");
        }
    }

    private void bindEvents() {
        if (markdownEditor != null) {
            markdownEditor.textProperty().addListener((obs, oldVal, newVal) -> {
                if (debounceTimer != null) debounceTimer.cancel();
                debounceTimer = new Timer(true);
                debounceTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            if (disposed) return;
                            updatePreview(newVal);
                            updateWordCount(newVal);
                        });
                    }
                }, 300);
            });
        }

        if (newButton != null) newButton.setOnAction(e -> newFile());
        if (openButton != null) openButton.setOnAction(e -> openFile());
        if (saveButton != null) saveButton.setOnAction(e -> saveFile());
        if (convertButton != null) convertButton.setOnAction(e -> convertWithMask());

        if (boldButton != null) boldButton.setOnAction(e -> insertFormat("**", "**"));
        if (italicButton != null) italicButton.setOnAction(e -> insertFormat("*", "*"));
        if (codeButton != null) codeButton.setOnAction(e -> insertFormat("`", "`"));
    }

    private void convertWithMask() {
        if (rootPane == null) return;
        loadingMask.show(rootPane);
        statusLabel.setText(I18N.get("label.converting"));
        conversionFuture = convertExecutor.submit(() -> {
            try {
                String md = markdownEditor != null ? markdownEditor.getText() : "";
                String html = docConverterService.markdownToHtml(md);
                html = docConverterService.postProcessHtml(html);
                boolean dark = isDarkTheme();
                String wrapped = docConverterService.wrapHtml(html, dark);
                final String finalHtml = wrapped;
                Platform.runLater(() -> {
                    if (disposed) return;
                    webEngine.loadContent(finalHtml);
                    statusLabel.setText(I18N.get("status.convertDone"));
                    loadingMask.hide();
                });
            } catch (Exception e) {
                logger.error("Convert failed", e);
                Platform.runLater(() -> {
                    if (disposed) return;
                    statusLabel.setText(I18N.get("status.saveFailed"));
                    loadingMask.hide();
                });
            }
        });
    }

    private boolean isDarkTheme() {
        try {
            return ThemeManager.getInstance().getCurrentTheme().isDark();
        } catch (Exception e) {
            return false;
        }
    }

    private void updatePreview(String markdown) {
        if (webEngine == null) return;
        String html = docConverterService.markdownToHtml(markdown);
        html = docConverterService.postProcessHtml(html);
        String wrapped = docConverterService.wrapHtml(html, isDarkTheme());
        webEngine.loadContent(wrapped);
    }

    private void updateWordCount(String text) {
        int count = text == null || text.isEmpty() ? 0 : text.split("\\s+").length;
        wordCountLabel.setText(I18N.get("label.wordCount", count));
    }

    private void newFile() {
        if (markdownEditor != null) markdownEditor.clear();
        currentFile = null;
        statusLabel.setText(I18N.get("status.newDoc"));
    }

    private void openFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18N.get("filechooser.openMd"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.markdown"), "*.md", "*.markdown"));
        File file = fc.showOpenDialog(rootPane != null && rootPane.getScene() != null ? rootPane.getScene().getWindow() : null);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                if (markdownEditor != null) markdownEditor.replaceText(content);
                currentFile = file;
                statusLabel.setText(I18N.get("status.opened", file.getName()));
            } catch (IOException e) {
                logger.error("Open file failed", e);
                statusLabel.setText(I18N.get("status.openFailed"));
            }
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            FileChooser fc = new FileChooser();
            fc.setTitle(I18N.get("filechooser.saveMd"));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.markdown"), "*.md"));
            currentFile = fc.showSaveDialog(rootPane != null && rootPane.getScene() != null ? rootPane.getScene().getWindow() : null);
        }
        if (currentFile != null && markdownEditor != null) {
            try {
                Files.writeString(currentFile.toPath(), markdownEditor.getText());
                statusLabel.setText(I18N.get("status.saved", currentFile.getName()));
            } catch (IOException e) {
                logger.error("Save file failed", e);
                statusLabel.setText(I18N.get("status.saveFailed"));
            }
        }
    }

    private void insertFormat(String prefix, String suffix) {
        if (markdownEditor == null) return;
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
        if (conversionFuture != null) conversionFuture.cancel(true);
        convertExecutor.shutdownNow();
        try {
            loadingMask.hide();
        } catch (RuntimeException e) {
            logger.warn("Failed to hide loading mask during disposal", e);
        }
        try {
            if (webEngine != null) webEngine.load(null);
        } catch (RuntimeException e) {
            logger.warn("Failed to clear preview during disposal", e);
        }
    }
}
