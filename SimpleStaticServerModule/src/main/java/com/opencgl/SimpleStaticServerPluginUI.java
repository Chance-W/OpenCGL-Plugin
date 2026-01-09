package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.staticserver.i18n.I18N;
import com.opencgl.staticserver.controller.StaticServerController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/**
 * 简易静态文件服务器插件入口
 */
public class SimpleStaticServerPluginUI implements PluginUI {


    private final ClassLoader pluginCl;
    private StaticServerController controller;

    public SimpleStaticServerPluginUI() {
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
        loader.setLocation(pluginCl.getResource("com/opencgl/staticserver/views/StaticServerView.fxml"));
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
        StaticServerController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) {
            controllerToDispose.dispose();
        }
    }
}
