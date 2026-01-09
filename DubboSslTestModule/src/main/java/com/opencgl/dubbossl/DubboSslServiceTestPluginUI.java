package com.opencgl.dubbossl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.dubbossl.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import com.opencgl.dubbossl.controller.DubboSslWidgetController;

public class DubboSslServiceTestPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();
    private DubboSslWidgetController controller;

    public DubboSslServiceTestPluginUI() {
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
        loader.setLocation(this.getClass().getClassLoader().getResource("DubboSslWidgetView.fxml"));
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
        DubboSslWidgetController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) controllerToDispose.dispose();
    }
}
