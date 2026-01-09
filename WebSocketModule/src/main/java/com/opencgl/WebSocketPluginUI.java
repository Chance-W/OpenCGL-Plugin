package com.opencgl;

import com.opencgl.api.PluginUI;
import com.opencgl.websocket.i18n.I18N;
import com.opencgl.websocket.controller.WebSocketController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * WebSocket 测试工具插件
 *
 * @author OpenCGL
 */
public class WebSocketPluginUI implements PluginUI {


    private final ClassLoader pluginCl;
    private WebSocketController controller;

    public WebSocketPluginUI() {
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
        return pluginCl.getResource("com/opencgl/websocket/icon.png");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setResources(I18N.getBundle(I18N.getLocale()));
        loader.setLocation(pluginCl.getResource("com/opencgl/websocket/views/WebSocketView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WebSocketView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        WebSocketController current = controller;
        controller = null;
        if (current != null) {
            current.dispose();
        }
    }
}
