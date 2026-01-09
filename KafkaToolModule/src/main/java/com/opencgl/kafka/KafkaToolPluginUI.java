package com.opencgl.kafka;

import com.opencgl.api.PluginUI;
import com.opencgl.kafka.i18n.I18N;
import com.opencgl.kafka.controller.KafkaToolController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * Kafka \u5DE5\u5177\u63D2\u4EF6\u5165\u53E3\u3002
 * \u901A\u8FC7 I18nResolver \u5B9E\u73B0\u5168\u5C40\u8BED\u8A00\u8054\u52A8\u3002
 */
public class KafkaToolPluginUI implements PluginUI {

    private final ClassLoader pluginCl;
    private KafkaToolController controller;

    public KafkaToolPluginUI() {
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
        return pluginCl.getResource("com/opencgl/kafka/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/kafka/views/KafkaToolView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load KafkaToolView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
    }
}
