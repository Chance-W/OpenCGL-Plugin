package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.rest.i18n.I18N;
import com.opencgl.controller.RestWidgetController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class RestServiceTestPluginUI implements PluginUI {


    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;
    private RestWidgetController controller;

    public RestServiceTestPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/restful.png");
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
        loader.setLocation(this.getClass().getClassLoader().getResource("RestWidgetView.fxml"));
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
        RestWidgetController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) {
            controllerToDispose.dispose();
        }
    }
}
