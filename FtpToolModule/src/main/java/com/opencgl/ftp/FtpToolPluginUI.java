package com.opencgl.ftp;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.ftp.i18n.I18N;
import com.opencgl.ftp.controller.FtpServerController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class FtpToolPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();
    private FtpServerController controller;

    public FtpToolPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/ftp.png");
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
        loader.setLocation(this.getClass().getClassLoader().getResource("FtpServer.fxml"));
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
        FtpServerController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) {
            controllerToDispose.dispose();
        }
    }
}
