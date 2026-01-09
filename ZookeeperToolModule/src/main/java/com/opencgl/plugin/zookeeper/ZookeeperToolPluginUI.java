package com.opencgl.plugin.zookeeper;

import java.io.IOException;
import java.net.URL;

import com.opencgl.plugin.zookeeper.i18n.I18N;
import com.opencgl.plugin.zookeeper.controller.ZookeeperToolController;
import com.opencgl.api.PluginUI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class ZookeeperToolPluginUI implements PluginUI {

    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;
    private ZookeeperToolController controller;

    public ZookeeperToolPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/dubbo.png");
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
        loader.setLocation(this.getClass().getClassLoader().getResource("ZookeeperTool.fxml"));
        Parent root;
        try {
            root = loader.load();
            controller = loader.getController();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
    }
}
