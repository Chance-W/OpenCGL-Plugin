package com.opencgl;

import com.opencgl.api.PluginUI;
import com.opencgl.yaml.i18n.I18N;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * YAML 工具插件
 *
 * @author OpenCGL
 */
public class YamlToolPluginUI implements PluginUI {

    private final ClassLoader pluginCl;

    public YamlToolPluginUI() {
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
        return pluginCl.getResource("com/opencgl/yaml/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/yaml/views/YamlToolView.fxml"));
        try {
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load YamlToolView.fxml", e);
        }
    }

    @Override
    public void dispose() {
    }
}
