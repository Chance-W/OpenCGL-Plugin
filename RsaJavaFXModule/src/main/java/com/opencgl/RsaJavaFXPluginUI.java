package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.rsa.i18n.I18N;
import com.opencgl.rsa.controller.RsaKeyGeneratorController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/**
 * RSA密钥生成器插件入口
 */
public class RsaJavaFXPluginUI implements PluginUI {

    private final ClassLoader pluginCl;
    private RsaKeyGeneratorController controller;

    public RsaJavaFXPluginUI() {
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
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setResources(I18N.getBundle(I18N.getLocale()));
        loader.setLocation(pluginCl.getResource("com/opencgl/rsa/views/RsaKeyGeneratorView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException(e);
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
