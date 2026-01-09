package com.opencgl.experience.controls;

import javafx.concurrent.Worker;
import javafx.beans.value.ChangeListener;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Singleton Manager for the shared Monaco WebView.
 * Handles initialization and state injection.
 */
public class MonacoWebViewManager {

    private static MonacoWebViewManager instance;
    private final WebView webView;
    private final WebEngine webEngine;
    private boolean ready = false;
    private boolean disposed;
    private final ChangeListener<Worker.State> loadStateListener;
    
    // Queue tasks if WebView isn't ready
    private final Queue<Runnable> pendingTasks = new LinkedList<>();

    public static synchronized MonacoWebViewManager getInstance() {
        if (instance == null) {
            instance = new MonacoWebViewManager();
        }
        return instance;
    }

    private MonacoWebViewManager() {
        this.webView = new WebView();
        this.webEngine = webView.getEngine();

        URL url = getClass().getResource("/com/opencgl/editor/experience/html/monaco.html");
        if (url != null) {
            webEngine.load(url.toExternalForm());
        }

        loadStateListener = (obs, oldState, newState) -> {
            if (disposed) return;
            if (newState == Worker.State.SUCCEEDED) {
                ready = true;
                processPendingTasks();
            }
        };
        webEngine.getLoadWorker().stateProperty().addListener(loadStateListener);
    }

    public WebView getWebView() {
        return webView;
    }
    
    public boolean isReady() {
        return ready;
    }

    /**
     * Detaches the WebView from its current parent (if any).
     */
    public void detach() {
        if (webView.getParent() != null) {
            if (webView.getParent() instanceof Pane) {
                ((Pane) webView.getParent()).getChildren().remove(webView);
            }
        }
    }

    /**
     * Executes a script immediately if ready, or queues it.
     */
    public void executeScript(String script) {
        if (disposed) return;
        if (ready) {
            webEngine.executeScript(script);
        } else {
            pendingTasks.add(() -> webEngine.executeScript(script));
        }
    }
    
    public Object executeScriptReturns(String script) {
        if (disposed || !ready) return null;
        return webEngine.executeScript(script);
    }

    public void setContent(String text, String lang, boolean isDark) {
        if (disposed) return;
        Runnable task = () -> {
            JSObject window = (JSObject) webEngine.executeScript("window");
            if (window.getMember("javaEditor") != null) {
                // Set Text
                String safeText = text.replace("\\", "\\\\").replace("`", "\\`");
                webEngine.executeScript("window.javaEditor.setValue(`" + safeText + "`)");
                
                // Set Language
                String mode = lang.toLowerCase();
                if (mode.equals("js")) mode = "javascript";
                webEngine.executeScript("window.javaEditor.setLanguage('" + mode + "')");
                
                // Set Theme
                String theme = isDark ? "vs-dark" : "vs";
                webEngine.executeScript("monaco.editor.setTheme('" + theme + "')");
                
                // Clear any old markers
                webEngine.executeScript("window.javaEditor.clearMarkers()");
            }
        };
        
        if (ready) task.run();
        else pendingTasks.add(task);
    }
    
    private void processPendingTasks() {
        while (!pendingTasks.isEmpty()) {
            pendingTasks.poll().run();
        }
    }

    public WebEngine getWebEngine() {
        return webEngine;
    }

    public static synchronized void disposeInstance() {
        if (instance == null) return;
        instance.dispose();
        instance = null;
    }

    private void dispose() {
        if (disposed) return;
        disposed = true;
        ready = false;
        pendingTasks.clear();
        try {
            webEngine.getLoadWorker().stateProperty().removeListener(loadStateListener);
        } catch (Exception ignored) {
        }
        try {
            if (webEngine.getLoadWorker().isRunning()) webEngine.getLoadWorker().cancel();
        } catch (Exception ignored) {
        }
        try {
            webEngine.executeScript("window.javaEditor && window.javaEditor.dispose && window.javaEditor.dispose()");
        } catch (Exception ignored) {
        }
        try {
            webEngine.load("about:blank");
        } catch (Exception ignored) {
        }
        try {
            detach();
        } catch (Exception ignored) {
        }
    }
}
