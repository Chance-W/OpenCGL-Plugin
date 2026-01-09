package com.opencgl.brower;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.brower.i18n.I18N;
import com.opencgl.brower.controller.WebSourcesToolController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class BrowserToolPluginUI implements PluginUI {


    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;
    private WebSourcesToolController controller;

    public BrowserToolPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/decode.png");
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
        loader.setLocation(this.getClass().getClassLoader().getResource("browserTool.fxml"));
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
        WebSourcesToolController current = controller;
        controller = null;
        if (current != null) current.dispose();
    }
}
