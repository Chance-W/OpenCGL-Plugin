package com.opencgl.pingscan;

import com.opencgl.api.PluginUI;
import com.opencgl.pingscan.i18n.I18N;
import com.opencgl.pingscan.controller.PingScanController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

public class PingScanPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();
    private PingScanController controller;

    public PingScanPluginUI() {
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
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setResources(I18N.getBundle(I18N.getLocale()));
        loader.setLocation(pluginCl.getResource("com/opencgl/pingscan/views/PingScanView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load PingScanView.fxml", e);
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
