package com.opencgl;

import com.opencgl.api.PluginUI;
import com.opencgl.mqtrace.i18n.I18N;
import com.opencgl.mqtrace.controller.MqTraceController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * 消息追踪工具插件入口
 * 支持 RocketMQ 和 RabbitMQ 消息追踪
 *
 * @author OpenCGL
 */
public class MqTracePluginUI implements PluginUI {

    private final ClassLoader pluginCl;
    private MqTraceController controller;

    public MqTracePluginUI() {
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
        return pluginCl.getResource("com/opencgl/mqtrace/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/mqtrace/views/MqTraceView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load MqTraceView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        MqTraceController current = controller;
        controller = null;
        if (current != null) {
            current.dispose();
        }
    }
}
