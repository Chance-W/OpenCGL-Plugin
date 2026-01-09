package com.opencgl.hash;

import com.opencgl.api.PluginUI;
import com.opencgl.hash.i18n.I18N;
import com.opencgl.hash.controller.HashToolController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * \u54C8\u5E0C\u5DE5\u5177\u63D2\u4EF6\u5165\u53E3\u3002
 */
public class HashToolPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();
    private HashToolController controller;

    public HashToolPluginUI() {
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
        return pluginCl.getResource("com/opencgl/hash/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/hash/views/HashToolView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load HashToolView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        HashToolController current = controller;
        controller = null;
        if (current != null) current.dispose();
    }
}
