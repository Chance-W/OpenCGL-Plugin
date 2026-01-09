package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.rocketmq.i18n.I18N;
import com.opencgl.api.PluginUI;
import com.opencgl.controller.RocketMqConsumerWidgetController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class RocketMqConsumerToolPluginUI implements PluginUI {

    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;
    private RocketMqConsumerWidgetController controller;

    public RocketMqConsumerToolPluginUI() {
        this.pluginCl = this.getClass().getClassLoader();
    }

    @Override
    public String directoryName() {
        return I18N.get("label.category");
    }

    @Override
    public String name() {
        return I18N.get("label.consumer_name");
    }

    @Override
    public URL iconPath() {
        return this.getClass().getClassLoader().getResource("icon/rmq.png");
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
        loader.setLocation(this.getClass().getClassLoader().getResource("RocketMqConsumerWidgetView.fxml"));
        Parent root;
        try {
            root = loader.load();
            controller = loader.getController();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    @Override
    public void dispose() {
        RocketMqConsumerWidgetController current = controller;
        controller = null;
        if (current != null) {
            current.dispose();
        }
    }
}
