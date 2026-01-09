package com.opencgl.clipboard.service;

import com.opencgl.clipboard.model.ClipboardEntry;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ClipboardService {
    private static final Logger logger = LoggerFactory.getLogger(ClipboardService.class);
    private static final int MAX_HISTORY = 100;
    
    private final ObservableList<ClipboardEntry> history = FXCollections.observableArrayList();
    private Timer monitorTimer;
    private String lastText = "";
    private Image lastImage = null;
    private boolean skipNextCheck = false;
    private volatile boolean monitoring;
    
    public void startMonitoring() {
        if (monitorTimer != null) {
            return;
        }
        
        monitorTimer = new Timer(true);
        monitoring = true;
        monitorTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkClipboard();
            }
        }, 0, 500); // 每500ms检查一次
        
        logger.info("剪贴板监听已启动");
    }
    
    public void stopMonitoring() {
        monitoring = false;
        if (monitorTimer != null) {
            monitorTimer.cancel();
            monitorTimer = null;
            logger.info("剪贴板监听已停止");
        }
    }
    
    private void checkClipboard() {
        Platform.runLater(() -> {
            if (!monitoring) return;
            // 如果是我们自己复制的，跳过这次检查
            if (skipNextCheck) {
                skipNextCheck = false;
                return;
            }
            
            try {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                
                // 检查文本
                if (clipboard.hasString()) {
                    String text = clipboard.getString();
                    if (text != null && !text.equals(lastText) && !text.trim().isEmpty()) {
                        addEntry(new ClipboardEntry(ClipboardEntry.Type.TEXT, text));
                        lastText = text;
                    }
                }
                // 检查图片
                else if (clipboard.hasImage()) {
                    Image image = clipboard.getImage();
                    if (image != null && image != lastImage) {
                        byte[] imageBytes = imageToBytes(image);
                        if (imageBytes != null) {
                            addEntry(new ClipboardEntry(imageBytes));
                            lastImage = image;
                        }
                    }
                }
                // 检查文件
                else if (clipboard.hasFiles()) {
                    List<File> files = clipboard.getFiles();
                    if (files != null && !files.isEmpty()) {
                        String filePaths = files.stream()
                            .map(File::getAbsolutePath)
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("");
                        addEntry(new ClipboardEntry(ClipboardEntry.Type.FILE, filePaths));
                    }
                }
            } catch (Exception e) {
                logger.error("检查剪贴板失败", e);
            }
        });
    }
    
    private void addEntry(ClipboardEntry entry) {
        history.add(0, entry);
        if (history.size() > MAX_HISTORY) {
            history.remove(MAX_HISTORY, history.size());
        }
    }
    
    private byte[] imageToBytes(Image image) {
        try {
            BufferedImage bufferedImage = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("图片转换失败", e);
            return null;
        }
    }
    
    public void copyToClipboard(ClipboardEntry entry) {
        // 标记下次检查跳过，避免循环
        skipNextCheck = true;
        
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        
        switch (entry.getType()) {
            case TEXT:
                content.putString(entry.getContent());
                lastText = entry.getContent();
                break;
            case IMAGE:
                content.putImage(entry.getImage());
                lastImage = entry.getImage();
                break;
            case FILE:
                // 暂不支持文件回填
                content.putString(entry.getContent());
                break;
        }
        
        clipboard.setContent(content);
        logger.info("已复制到剪贴板: " + entry.getDisplayText());
    }
    
    public ObservableList<ClipboardEntry> getHistory() {
        return history;
    }
    
    public void clearHistory() {
        history.clear();
    }
    
    public void removeEntry(ClipboardEntry entry) {
        history.remove(entry);
    }
}
