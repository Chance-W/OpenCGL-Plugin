package com.opencgl.mml;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.mml.i18n.I18N;
import com.opencgl.mml.controller.MmlWidgetController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class MmlServiceTestestPluginUI implements PluginUI {


    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;
    private MmlWidgetController controller;

    public MmlServiceTestestPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/huawei.png");
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
        loader.setLocation(this.getClass().getClassLoader().getResource("MmlWidgetView.fxml"));
        Parent root;
        try {
            root = loader.load();
            controller = loader.getController();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    @Override
    public void dispose() {
        MmlWidgetController current = controller;
        controller = null;
        if (current != null) {
            current.dispose();
        }
    }
}
