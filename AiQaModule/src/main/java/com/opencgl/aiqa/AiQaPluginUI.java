package com.opencgl.aiqa;

import com.opencgl.api.PluginUI;
import com.opencgl.aiqa.i18n.I18N;
import com.opencgl.aiqa.controller.AiQaController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * AI Q&A 插件 UI
 */
public class AiQaPluginUI implements PluginUI {

    private final ClassLoader pluginCl;
    private AiQaController controller;

    public AiQaPluginUI() {
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
        return null;
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setClassLoader(pluginCl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setLocation(pluginCl.getResource("AiQaView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("加载AI集成界面失败", e);
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
