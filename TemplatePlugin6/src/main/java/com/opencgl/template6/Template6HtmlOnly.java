package com.opencgl.template6;

import com.opencgl.api.PluginUI;
import com.opencgl.template6.i18n.I18N;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 插件开发模板 6 - 无 Java 接口：仅加载一个 HTML 文件，不暴露任何 Java 对象给页面。
 * 主程序通过 createView() 得到 HTML 字符串或 URL 后直接加载，无需桥接。
 */
public class Template6HtmlOnly implements PluginUI {

    private static final String HTML_RESOURCE = "/com/opencgl/template6/html/demo.html";

    @Override
    public String directoryName() {
        return I18N.get("label.category");
    }

    @Override
    public String name() {
        return I18N.get("label.name_html_only");
    }

    @Override
    public URL iconPath() {
        return null;
    }

    @Override
    public UIType type() {
        return UIType.WEB;
    }

    @Override
    public Object createView() {
        // 返回 URL 时主程序会 engine.load(url)；也可返回 HTML 字符串
        URL url = getClass().getResource(HTML_RESOURCE);
        if (url != null) {
            return url.toExternalForm();
        }
        return loadHtmlFromResource(HTML_RESOURCE);
    }

    private static String loadHtmlFromResource(String path) {
        try (InputStream is = Template6HtmlOnly.class.getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "<html><body><p>Missing " + path + "</p></body></html>";
    }

    @Override
    public void dispose() {
    }
}
