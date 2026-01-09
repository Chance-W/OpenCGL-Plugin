package com.opencgl.jwt;

import com.opencgl.api.PluginUI;
import com.opencgl.jwt.i18n.I18N;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * JWT \u5DE5\u5177\u63D2\u4EF6\u5165\u53E3\u3002
 */
public class JWTToolPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();

    public JWTToolPluginUI() {
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
        return pluginCl.getResource("com/opencgl/jwt/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/jwt/views/JWTToolView.fxml"));
        try {
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load JWTToolView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        // 清理资源
    }
}
