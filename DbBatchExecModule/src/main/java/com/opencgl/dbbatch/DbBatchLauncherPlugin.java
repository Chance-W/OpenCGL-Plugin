package com.opencgl.dbbatch;

import com.opencgl.api.PluginUI;
import com.opencgl.dbbatch.i18n.I18N;
import com.opencgl.dbbatch.controller.DbBatchController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

public class DbBatchLauncherPlugin implements PluginUI {

    
    private final ClassLoader pluginCl;
    private DbBatchController controller;

    public DbBatchLauncherPlugin() {
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
        return pluginCl.getResource("images/database.png");
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
            loader.setLocation(pluginCl.getResource("DbBatchExecView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("加载SQL批量执行界面失败", e);
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
