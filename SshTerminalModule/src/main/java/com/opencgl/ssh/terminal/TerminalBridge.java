package com.opencgl.ssh.terminal;

import com.opencgl.ssh.i18n.I18N;
import com.opencgl.ssh.service.ITerminalService;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java与xterm.js的桥接类
 * 处理JavaScript回调和SSH数据转发
 */
public class TerminalBridge {

    private static final Logger logger = LoggerFactory.getLogger(TerminalBridge.class);

    private final WebEngine webEngine;
    private final ITerminalService terminalService;
    private Runnable onReadyCallback;
    private int cols = 80;
    private int rows = 24;
    private volatile boolean terminalReady = false;
    private final ConcurrentLinkedQueue<String> writeQueue = new ConcurrentLinkedQueue<>();
    private final ChangeListener<Worker.State> loadStateListener;

    public TerminalBridge(WebEngine webEngine, ITerminalService terminalService) {
        this.webEngine = webEngine;
        this.terminalService = terminalService;
        this.loadStateListener = (obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) this.webEngine.executeScript("window");
                window.setMember("javaBridge", this);
                logger.info("Java Bridge已注入到WebView");
            }
        };
        setupBridge();
    }

    private void setupBridge() {
        webEngine.getLoadWorker().stateProperty().addListener(loadStateListener);
    }

    /**
     * 从JavaScript接收用户输入（含中文等 Unicode）
     * 按 UTF-8 字节发送到 SSH，避免中文变成乱码
     */
    public void onInput(String data) {
        if (data == null || data.isEmpty()) return;
        if (terminalService != null && terminalService.isConnected()) {
            try {
                byte[] utf8 = data.getBytes(StandardCharsets.UTF_8);
                terminalService.sendBytes(utf8);
            } catch (Exception e) {
                logger.error("发送输入到终端失败", e);
            }
        }
    }

    /**
     * 终端大小变化回调
     */
    public void onResize(int newCols, int newRows) {
        this.cols = newCols;
        this.rows = newRows;
        logger.debug("终端大小变化: {}x{}", newCols, newRows);
        // 通知服务器终端大小变化
        if (terminalService != null && terminalService.isConnected()) {
            terminalService.setPtySize(newCols, newRows);
        }
    }

    /**
     * 终端就绪回调（此时 window.terminalAPI 已存在）
     */
    public void onReady(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.terminalReady = true;
        logger.info("xterm.js终端就绪: {}x{}", cols, rows);
        
        // 如果此刻 终端 已经处于连接完成态，为了安全起见也要再下发一次尺寸
        if (terminalService != null && terminalService.isConnected()) {
            terminalService.setPtySize(cols, rows);
        }

        flushWriteQueue();
        updateI18n(); // 初始下发语言组
        
        if (onReadyCallback != null) {
            Platform.runLater(onReadyCallback);
        }
    }

    /**
     * 打开 URL（从终端点击链接时调用）
     */
    public void openUrl(String url) {
        Platform.runLater(() -> {
            try {
                // 使用系统默认浏览器打开URL
                Desktop.getDesktop().browse(new java.net.URI(url));
                logger.info("在浏览器中打开URL: {}", url);
            } catch (Exception e) {
                logger.error("打开URL失败: {}", url, e);
            }
        });
    }

    /**
     * 写入数据到终端（若 terminalAPI 未就绪则先入队，onReady 时再写）
     */
    public void write(String data) {
        if (data == null) return;
        if (!terminalReady) {
            writeQueue.offer(data);
            return;
        }
        Platform.runLater(() -> doWrite(data));
    }

    private void doWrite(String data) {
        if (!terminalReady) return;
        try {
            String escaped = data
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            webEngine.executeScript("window.terminalAPI.write(\"" + escaped + "\")");
        } catch (Exception e) {
            logger.debug("写入终端失败(可能未就绪): {}", e.getMessage());
        }
    }

    private void flushWriteQueue() {
        Platform.runLater(() -> {
            String s;
            while ((s = writeQueue.poll()) != null) {
                doWrite(s);
            }
        });
    }

    /**
     * 清屏
     */
    public void clear() {
        if (!terminalReady) return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("window.terminalAPI.clear()");
            } catch (Exception e) {
                logger.debug("清屏失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 让 xterm 根据当前容器尺寸重新 fit（更新 rows/cols 并通知 SSH PTY）
     * 在 WebView 尺寸变化或 Tab 首次显示时调用，避免 vi 等只显示半屏
     */
    public void fit() {
        if (!terminalReady) return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("if (window.terminalAPI && window.terminalAPI.fit) window.terminalAPI.fit()");
            } catch (Exception e) {
                logger.debug("fit 失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 聚焦终端
     */
    public void focus() {
        if (!terminalReady) return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("window.terminalAPI.focus()");
            } catch (Exception e) {
                logger.debug("聚焦失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 设置就绪回调
     */
    public void setOnReadyCallback(Runnable callback) {
        this.onReadyCallback = callback;
    }

    /**
     * 设置终端主题
     */
    public void setTheme(boolean isDark) {
        if (!terminalReady) return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("window.terminalAPI.setTheme(" + isDark + ")");
                logger.debug("已下发终端主题设定: isDark={}", isDark);
            } catch (Exception e) {
                logger.debug("设置终端主题失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 更新网页内部的国际化文字（如右键菜单）
     */
    public void updateI18n() {
        if (!terminalReady) return;
        Platform.runLater(() -> {
            try {
                // 读取翻译文本
                String copyText = I18N.getOrDefault("terminal.menu.copy", "Copy");
                String pasteText = I18N.getOrDefault("terminal.menu.paste", "Paste");
                String cutText = I18N.getOrDefault("terminal.menu.cut", "Cut");
                String selectAllText = I18N.getOrDefault("terminal.menu.selectAll", "Select All");

                // 组装 JSON 字典
                String dictJson = String.format(
                    "{\"copy\":\"%s\", \"paste\":\"%s\", \"cut\":\"%s\", \"selectAll\":\"%s\"}",
                    copyText, pasteText, cutText, selectAllText
                );

                String script = "window.terminalAPI.setI18n('" + dictJson + "')";
                webEngine.executeScript(script);
            } catch (Exception e) {
                logger.debug("下发终端I18N失败", e);
            }
        });
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    /**
     * 供 JS 调用的剪贴板写入（复制/剪切时把选中内容写入系统剪贴板）
     * 必须在 JavaFX 线程执行。
     */
    public void setClipboardText(String text) {
        if (text == null) return;
        Platform.runLater(() -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            clipboard.setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, text));
        });
    }

    /**
     * 供 JS 调用的剪贴板读取（粘贴时从系统剪贴板取文本）
     * 若在非 JavaFX 线程调用则投递到 FX 线程并等待结果，避免 WebView 回调线程无法读剪贴板。
     */
    public String getClipboardText() {
        if (Platform.isFxApplicationThread()) {
            return getClipboardTextImpl();
        }
        AtomicReference<String> ref = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ref.set(getClipboardTextImpl());
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(300, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ref.get() != null ? ref.get() : "";
    }

    private String getClipboardTextImpl() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        return clipboard.hasString() ? clipboard.getString() : "";
    }

    /**
     * 从系统剪贴板粘贴到终端（供 Java 端快捷键回退使用）
     */
    public void pasteFromClipboard() {
        String text = getClipboardText();
        if (text != null && !text.isEmpty()) {
            write(text);
        }
    }

    public void dispose() {
        terminalReady = false;
        onReadyCallback = null;
        writeQueue.clear();
        webEngine.getLoadWorker().stateProperty().removeListener(loadStateListener);
        try {
            JSObject window = (JSObject) webEngine.executeScript("window");
            window.removeMember("javaBridge");
        } catch (RuntimeException error) {
            logger.debug("移除 Java Bridge 失败(页面可能尚未就绪): {}", error.getMessage());
        }
    }
}
