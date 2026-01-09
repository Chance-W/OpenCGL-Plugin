package com.opencgl.timestamp;

import com.opencgl.api.PluginUI;
import com.opencgl.timestamp.i18n.I18N;
import com.opencgl.timestamp.controller.TimestampToolController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * 时间戳工具插件入口
 */
public class TimestampToolLauncherPlugin implements PluginUI {

    private final ClassLoader pluginCl;
    private TimestampToolController controller;

    public TimestampToolLauncherPlugin() {
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
        return pluginCl.getResource("icon/timestamp.png");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setClassLoader(pluginCl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setLocation(pluginCl.getResource("TimestampToolView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException(I18N.get("msg.convert_failed", "FXML load failed"), e);
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
