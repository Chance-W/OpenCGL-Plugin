package com.opencgl;

import com.opencgl.api.PluginUI;
import com.opencgl.mongodb.i18n.I18N;
import com.opencgl.mongodb.controller.MongoDBController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * MongoDB 工具插件入口
 * 支持 MongoDB 数据库操作
 *
 * @author OpenCGL
 */
public class MongoDBPluginUI implements PluginUI {


    private final ClassLoader pluginCl;
    private MongoDBController controller;

    public MongoDBPluginUI() {
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
        return pluginCl.getResource("com/opencgl/mongodb/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/mongodb/views/MongoDBView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load MongoDBView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        MongoDBController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) {
            controllerToDispose.dispose();
        }
    }
}
