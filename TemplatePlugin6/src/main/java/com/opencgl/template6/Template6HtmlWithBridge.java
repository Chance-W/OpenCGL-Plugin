package com.opencgl.template6;

import com.opencgl.api.PluginUI;
import com.opencgl.api.WebPluginWithBridge;
import com.opencgl.template6.i18n.I18N;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 插件开发模板 6 - 有 Java 接口：加载 HTML 并将 getJsBridge() 暴露为 window.javaBridge，
 * 页面内可通过 window.javaBridge.getGreeting()、window.javaBridge.showMessage(msg) 等调用 Java。
 */
public class Template6HtmlWithBridge implements PluginUI, WebPluginWithBridge {

    private static final String HTML_RESOURCE = "/com/opencgl/template6/html/demo-with-bridge.html";
    private final Template6Bridge bridge = new Template6Bridge();

    @Override
    public String directoryName() {
        return I18N.get("label.category");
    }

    @Override
    public String name() {
        return I18N.get("label.name_with_bridge");
    }

    @Override
    public java.net.URL iconPath() {
        return null;
    }

    @Override
    public UIType type() {
        return UIType.WEB;
    }

    @Override
    public Object createView() {
        try (InputStream is = getClass().getResourceAsStream(HTML_RESOURCE)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "<html><body><p>Missing " + HTML_RESOURCE + "</p></body></html>";
    }

    @Override
    public Object getJsBridge() {
        return bridge;
    }

    @Override
    public void dispose() {
    }

    /**
     * 暴露给页面的 Java 对象，public 方法会在 JS 中通过 window.javaBridge.xxx() 调用。
     */
    public static class Template6Bridge {

        public String getGreeting() {
            return I18N.get("bridge.greeting");
        }

        public void showMessage(String msg) {
            javax.swing.SwingUtilities.invokeLater(() ->
                javax.swing.JOptionPane.showMessageDialog(null, msg));
        }
    }
}
