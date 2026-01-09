package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.soap.i18n.I18N;
import com.opencgl.controller.SoapWidgetController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class SoapServiceTestPluginUI implements PluginUI {

    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;
    private SoapWidgetController controller;

    public SoapServiceTestPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/soap.png");
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
        loader.setLocation(this.getClass().getClassLoader().getResource("SoapTestModule.fxml"));
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
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
    }
}
