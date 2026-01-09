package com.opencgl.lanmsg;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.lanmsg.i18n.I18N;
import com.opencgl.lanmsg.controller.LanMessengerController;
import javafx.fxml.FXMLLoader;

/**
 * 局域网通讯插件入口
 */
public class LanMessengerPluginUI implements PluginUI {


    private final ClassLoader pluginCl;
    private LanMessengerController controller;

    public LanMessengerPluginUI() {
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
        loader.setLocation(pluginCl.getResource("com/opencgl/lanmsg/views/LanMessengerView.fxml"));
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
