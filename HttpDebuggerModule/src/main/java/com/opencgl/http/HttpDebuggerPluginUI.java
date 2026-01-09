package com.opencgl.http;

import com.opencgl.http.i18n.I18N;
import com.opencgl.api.PluginUI;
import com.opencgl.http.controller.HttpDebuggerController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * HTTP调试器插件UI入口
 */
public class HttpDebuggerPluginUI implements PluginUI {
    private static final Logger logger = LoggerFactory.getLogger(HttpDebuggerPluginUI.class);
    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;
    private HttpDebuggerController controller;

    public HttpDebuggerPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/http.png");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Node createView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    this.getClass().getResource("/com/opencgl/http/views/HttpDebuggerView.fxml"));
            loader.setClassLoader(pluginCl);
            // 设置当前语言的资源包
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            Node root = loader.load();
            controller = loader.getController();
            return root;

        } catch (Exception e) {
            logger.error("加载HTTP调试器UI失败", e);
            throw new RuntimeException("加载HTTP调试器UI失败", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
        logger.info("HTTP调试器插件已卸载");
    }
}
