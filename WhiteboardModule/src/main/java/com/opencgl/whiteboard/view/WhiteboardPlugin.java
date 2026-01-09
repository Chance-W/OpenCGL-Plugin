package com.opencgl.whiteboard.view;

import com.opencgl.api.PluginUI;
import com.opencgl.api.ThemeAware;
import com.opencgl.api.ThemeInfo;
import com.opencgl.whiteboard.view.i18n.I18N;
import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.i18n.BaseI18N;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.scene.input.ZoomEvent;
import javafx.scene.input.ScrollEvent;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class WhiteboardPlugin implements PluginUI, ThemeAware {

    private static final Logger log = LoggerFactory.getLogger(WhiteboardPlugin.class);

    private final ClassLoader pluginCl;
    private StackPane rootPane;
    private WebView webView;
    private WebEngine webEngine;
    private boolean isPageLoaded = false;
    private File tempDir;
    private boolean disposed;
    private ChangeListener<Worker.State> loadStateListener;
    private ChangeListener<Locale> localeListener;
    private EventHandler<ZoomEvent> zoomHandler;
    
    // 保持对注入 JS 的 Java 对象的强引用，防止被 JavaFX WebView 顺手当做弱引用 GC 掉
    private JSLogger jsLogger;
    private JavaIOBridge ioBridge;

    public WhiteboardPlugin() {
        this.pluginCl = this.getClass().getClassLoader();
    }

    @Override
    public String directoryName() {
        return I18N.get("label.category");
    }

    @Override
    public String name() {
        return I18N.get("label.name");
    }

    @Override
    public URL iconPath() {
        return pluginCl.getResource("com/opencgl/whiteboard/icon.png");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        if (disposed) return null;
        if (rootPane == null) {
            rootPane = new StackPane();
            initWebView();
        }
        return rootPane;
    }

    /**
     * 将 JAR 内的 web 资源提取到系统临时目录，
     * 因为 JavaFX WebView 无法正确解析 jar: 协议下的相对路径。
     */
    private File extractWebResources() {
        try {
            Path tmpRoot = Files.createTempDirectory("whiteboard-webview-");
            tempDir = tmpRoot.toFile();
            tempDir.deleteOnExit();

            // 需要提取的资源列表（纯 JS 方案，只需 index.html + rough.min.js）
            String[] resources = {
                "com/opencgl/whiteboard/web/index.html",
                "com/opencgl/whiteboard/web/js/rough.min.js"
            };

            for (String res : resources) {
                try (InputStream in = pluginCl.getResourceAsStream(res)) {
                    if (in == null) {
                        log.error("Resource not found in JAR: {}", res);
                        continue;
                    }
                    // 保留目录结构，但只取 web/ 以下的相对路径
                    String relative = res.substring(res.indexOf("/web/") + 5); // e.g. "index.html" 或 "js/whiteboard.min.js"
                    File dest = new File(tempDir, relative);
                    dest.getParentFile().mkdirs();
                    Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Extracted {} -> {}", res, dest.getAbsolutePath());
                }
            }
            return tempDir;
        } catch (Exception e) {
            log.error("Failed to extract Whiteboard web resources", e);
            return null;
        }
    }

    private void initWebView() {
        webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);

        // 监听加载状态
        loadStateListener = (obs, oldState, newState) -> {
            if (disposed) return;
            if (newState == Worker.State.SUCCEEDED) {
                isPageLoaded = true;
                log.info("Whiteboard WebView loaded successfully.");
                // 注入 javaLog 桥接（仅用于 window.onerror 回调）
                JSObject window = (JSObject) webEngine.executeScript("window");
                jsLogger = new JSLogger();
                window.setMember("javaLog", jsLogger);
                // 注入 javaIO 桥接（文件保存/打开）
                ioBridge = new JavaIOBridge(webEngine, webView);
                window.setMember("javaIO", ioBridge);
                // 只挂 onerror，不覆盖 console（覆盖在严格模式下会引发 readonly 错误）
                webEngine.executeScript(
                    "window.onerror = function(msg, url, line) {" +
                    "  if(window.javaLog) window.javaLog.error('JS Error: ' + msg + ' at line ' + line);" +
                    "  return false;" +
                    "};"
                );
                // 由 Java 侧触发首次渲染，确保 javaLog/onerror 已就绪，渲染错误能被捕获
                webEngine.executeScript("window._initRender && window._initRender()");
                // 这里同步一下初始的主题状态
                boolean isDark = ThemeManager.getInstance().getCurrentTheme().isDark();
                webEngine.executeScript("window.setTheme && window.setTheme('" + (isDark ? "dark" : "light") + "')");
                // 首次加载同步当前语言
                syncLanguage();
                // 仅当用户真正切换主题时才同步（见 onThemeChanged / localeProperty 监听）
            } else if (newState == Worker.State.FAILED) {
                Throwable ex = webEngine.getLoadWorker().getException();
                log.error("Failed to load Whiteboard WebView.", ex);
            }
        };
        webEngine.getLoadWorker().stateProperty().addListener(loadStateListener);

        // 监听 Mac 触摸板的捏合动作 (Pinch to Zoom)
        zoomHandler = event -> {
            if (disposed) return;
            if (isPageLoaded) {
                try {
                    // ZoomEvent.getZoomFactor() 返回类似 1.1 (放大) 或 0.9 (缩小) 
                    double factor = event.getZoomFactor();
                    double cx = event.getX();
                    double cy = event.getY();
                    webEngine.executeScript(String.format("window.nativeZoom && window.nativeZoom(%f, %f, %f)", factor, cx, cy));
                } catch (Exception e) {
                    log.error("Failed to forward zoom event to Whiteboard", e);
                }
            }
            event.consume();
        };
        webView.addEventFilter(ZoomEvent.ZOOM, zoomHandler);

        // 监听全局语言切换
        localeListener = (obs, oldVal, newVal) -> Platform.runLater(() -> {
            if (!disposed) syncLanguage();
        });
        BaseI18N.localeProperty().addListener(localeListener);

        // 提取 JAR 内资源到临时目录，从文件系统加载（解决 jar: 相对路径问题）
        File webRoot = extractWebResources();
        if (webRoot != null) {
            File indexHtml = new File(webRoot, "index.html");
            if (indexHtml.exists()) {
                log.info("Loading Whiteboard from: {}", indexHtml.toURI());
                webEngine.load(indexHtml.toURI().toString());
            } else {
                log.error("index.html not found after extraction: {}", indexHtml.getAbsolutePath());
            }
        } else {
            log.error("Failed to extract Whiteboard web resources.");
        }

        rootPane.getChildren().add(webView);
    }

    @Override
    public void onThemeChanged(ThemeInfo themeInfo) {
        if (disposed) return;
        Platform.runLater(() -> {
            if (!disposed) syncTheme(themeInfo.isDark());
        });
    }

    private void syncTheme(boolean isDark) {
        if (!isPageLoaded) return;
        String themeStr = isDark ? "dark" : "light";
        try {
            webEngine.executeScript("window.setTheme && window.setTheme('" + themeStr + "')");
        } catch (Exception e) {
            log.error("Failed to sync theme to Whiteboard", e);
        }
    }

    private void syncLanguage() {
        if (!isPageLoaded) return;
        String currentLang = BaseI18N.getLocale().getLanguage();
        try {
            webEngine.executeScript("window.setLang && window.setLang('" + currentLang + "')");
        } catch (Exception e) {
            log.error("Failed to sync language to Whiteboard", e);
        }
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        isPageLoaded = false;
        if (ioBridge != null) {
            try {
                ioBridge.dispose();
            } catch (Exception ignored) {
            }
            ioBridge = null;
        }
        if (localeListener != null) {
            try {
                BaseI18N.localeProperty().removeListener(localeListener);
            } catch (Exception ignored) {
            }
            localeListener = null;
        }
        if (webView != null && zoomHandler != null) {
            try {
                webView.removeEventFilter(ZoomEvent.ZOOM, zoomHandler);
            } catch (Exception ignored) {
            }
            zoomHandler = null;
        }
        if (webEngine != null) {
            if (loadStateListener != null) {
                try {
                    webEngine.getLoadWorker().stateProperty().removeListener(loadStateListener);
                } catch (Exception ignored) {
                }
                loadStateListener = null;
            }
            try {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.removeMember("javaIO");
                window.removeMember("javaLog");
            } catch (Exception ignored) {
            }
            try {
                webEngine.getLoadWorker().cancel();
                webEngine.load("about:blank");
            } catch (Exception ignored) {
            }
        }
        if (rootPane != null) {
            rootPane.getChildren().clear();
        }
        // 清理临时目录
        if (tempDir != null && tempDir.exists()) {
            deleteRecursive(tempDir);
            tempDir = null;
        }
        jsLogger = null;
        webEngine = null;
        webView = null;
        rootPane = null;
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        f.delete();
    }
}
