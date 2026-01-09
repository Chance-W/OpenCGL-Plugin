package com.opencgl;

import com.opencgl.api.PluginUI;
import com.opencgl.nacos.i18n.I18N;
import com.opencgl.nacos.controller.NacosController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * Nacos \u914D\u7F6E\u4E2D\u5FC3\u5DE5\u5177\u63D2\u4EF6\u3002
 */
public class NacosPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();
    private NacosController controller;

    public NacosPluginUI() {
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
        return pluginCl.getResource("com/opencgl/nacos/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/nacos/views/NacosView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load NacosView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        NacosController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) {
            controllerToDispose.dispose();
        }
    }
}
