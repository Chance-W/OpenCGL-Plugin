package com.opencgl.base64;

import com.opencgl.api.PluginUI;
import com.opencgl.base64.i18n.I18N;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

public class Base64ToolPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();

    public Base64ToolPluginUI() {
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
        loader.setLocation(pluginCl.getResource("com/opencgl/base64/views/Base64ToolView.fxml"));
        try {
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Base64ToolView.fxml", e);
        }
    }

    @Override
    public void dispose() {
    }
}
