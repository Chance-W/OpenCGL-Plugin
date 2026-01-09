package com.opencgl.envvars;

import com.opencgl.api.PluginUI;
import com.opencgl.envvars.i18n.I18N;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

public class EnvVarsPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();

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
        loader.setLocation(pluginCl.getResource("com/opencgl/envvars/views/EnvVarsView.fxml"));
        try {
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load EnvVarsView.fxml", e);
        }
    }

    @Override
    public void dispose() {
    }
}
