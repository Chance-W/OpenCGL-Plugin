package com.opencgl.base.utils;

import com.opencgl.base.theme.ThemeManager;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CSS 文件监听器
 * 用于在开发模式下监听 CSS 文件变化并自动触发热重载
 *
 * @author Antigravity
 */
public class CssFileWatcher extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(CssFileWatcher.class);
    private final Path rootDir;
    private final long DEBOUNCE_DELAY_MS = 500;
    private long lastReloadTime = 0;

    public CssFileWatcher(String rootPath) {
        this.rootDir = Paths.get(rootPath);
        setDaemon(true);
        setName("CssFileWatcher");
    }

    @Override
    public void run() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            // 递归注册所有子目录
            regsiterRecursive(rootDir, watchService);
            
            logger.info("CSS File Watcher started for: {}", rootDir);

            while (!isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    return;
                }

                boolean reloadNeeded = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    // 处理新创建的目录 (以便监听其下的新文件)
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path child = (Path) event.context();
                        Path fullPath = ((Path) key.watchable()).resolve(child);
                        if (Files.isDirectory(fullPath)) {
                            regsiterRecursive(fullPath, watchService);
                        }
                    }

                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path filename = (Path) event.context();
                        if (filename.toString().endsWith(".css")) {
                            reloadNeeded = true;
                        }
                    }
                }

                if (reloadNeeded) {
                    // 防抖动处理
                    long now = System.currentTimeMillis();
                    if (now - lastReloadTime > DEBOUNCE_DELAY_MS) {
                        lastReloadTime = now;
                        logger.info("CSS change detected, reloading themes...");
                        // 稍作延迟以确保文件写入完成
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {}
                        
                        Platform.runLater(() -> ThemeManager.getInstance().reloadCurrentTheme());
                    }
                }

                if (!key.reset()) {
                    break;
                }
            }
        } catch (IOException e) {
            logger.error("Error in CSS File Watcher", e);
        }
    }

    private void regsiterRecursive(Path start, WatchService watcher) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
