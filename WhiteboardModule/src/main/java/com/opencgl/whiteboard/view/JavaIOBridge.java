package com.opencgl.whiteboard.view;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Base64;

import javafx.scene.layout.Pane;
import com.opencgl.base.utils.ToastUtil;

/**
 * 通过 JSObject.setMember 注入到 window.javaIO，
 * 供 JavaScript 调用 Java 侧的原生文件选择对话框和文件 IO。
 */
public class JavaIOBridge {

    private static final Logger log = LoggerFactory.getLogger(JavaIOBridge.class);

    private final WebEngine engine;
    private final WebView webView;
    
    // 记录当前打开或保存的文件路径
    private File currentFile = null;
    private volatile boolean disposed;

    public JavaIOBridge(WebEngine engine, WebView webView) {
        this.engine = engine;
        this.webView = webView;
    }

    private Stage getOwnerStage() {
        if (webView != null && webView.getScene() != null && webView.getScene().getWindow() instanceof Stage) {
            return (Stage) webView.getScene().getWindow();
        }
        return null; // 回退为无父窗口的独立对话框
    }

    /**
     * 从 JS 调用：保存到当前文件，如果未打开/保存过，则等同于另存为。
     * @param json 白板序列化的 JSON 字符串
     */
    public void saveFile(String json) {
        if (disposed) return;
        if (currentFile != null) {
            // 直接保存
            Platform.runLater(() -> {
                if (disposed) return;
                try (FileWriter fw = new FileWriter(currentFile)) {
                    fw.write(json);
                    log.info("白板已直接保存到: {}", currentFile.getAbsolutePath());
                    if (webView.getParent() instanceof Pane) {
                        ToastUtil.show((Pane) webView.getParent(), "保存成功", true);
                    }
                } catch (Exception e) {
                    log.error("保存白板文件失败", e);
                    engine.executeScript("alert('保存失败: " + e.getMessage().replace("'", "\\'") + "')");
                }
            });
        } else {
            saveAsFile(json);
        }
    }

    /**
     * 从 JS 调用：弹出"另存为"对话框并将 json 写入文件。
     * @param json 白板序列化的 JSON 字符串
     */
    public void saveAsFile(String json) {
        if (disposed) return;
        Platform.runLater(() -> {
            if (disposed) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("另存为");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("手绘白板文件 (*.wb)", "*.wb"));
            File file = chooser.showSaveDialog(getOwnerStage());
            if (file != null) {
                try (FileWriter fw = new FileWriter(file)) {
                    fw.write(json);
                    currentFile = file; // 记录当前文件
                    log.info("白板已保存到: {}", file.getAbsolutePath());
                    if (webView.getParent() instanceof Pane) {
                        ToastUtil.show((Pane) webView.getParent(), "保存成功", true);
                    }
                } catch (Exception e) {
                    log.error("保存白板文件失败", e);
                    engine.executeScript("alert('保存失败: " + e.getMessage().replace("'", "\\'") + "')");
                }
            }
        });
    }

    /**
     * 从 JS 调用：弹出"打开文件"对话框，读取 JSON 后调用 JS 加载数据。
     */
    public void openFile() {
        if (disposed) return;
        Platform.runLater(() -> {
            if (disposed) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("打开文件");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("手绘白板文件 (*.wb)", "*.wb"));
            File file = chooser.showOpenDialog(getOwnerStage());
            if (file != null) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
                    currentFile = file; // 记录当前文件
                    log.info("打开白板文件: {}", file.getAbsolutePath());
                    // JSON 本身是合法 JS 表达式，直接赋值给临时变量再传入函数
                    engine.executeScript(
                            "window.__pendingData = " + content + ";" +
                            "window.loadDrawingData && window.loadDrawingData(window.__pendingData);"
                    );
                } catch (Exception e) {
                    log.error("打开白板文件失败", e);
                    engine.executeScript("alert('打开失败: " + e.getMessage().replace("'", "\\'") + "')");
                }
            }
        });
    }

    /**
     * 从 JS 调用：新建文件时清除当前已绑定的文件路径。
     */
    public void newFile() {
        if (disposed) return;
        this.currentFile = null;
    }

    /**
     * 从 JS 调用：接收 base64 图片数据并保存为 PNG 文件。
     */
    public void exportImage(String base64Data) {
        if (disposed) return;
        Platform.runLater(() -> {
            if (disposed) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("导出图片");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PNG 图片 (*.png)", "*.png"));
            File file = chooser.showSaveDialog(getOwnerStage());
            if (file != null) {
                try {
                    String b64 = base64Data;
                    int commaIdx = b64.indexOf(',');
                    if (commaIdx != -1) {
                        b64 = b64.substring(commaIdx + 1);
                    }
                    byte[] imageBytes = Base64.getDecoder().decode(b64);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                        fos.write(imageBytes);
                    }
                    log.info("图片已导出到: {}", file.getAbsolutePath());
                    if (webView.getParent() instanceof Pane) {
                        ToastUtil.show((Pane) webView.getParent(), "导出成功", true);
                    }
                } catch (Exception e) {
                    log.error("导出图片失败", e);
                    engine.executeScript("alert('导出失败: " + e.getMessage().replace("'", "\\'") + "')");
                }
            }
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        currentFile = null;
    }
}
