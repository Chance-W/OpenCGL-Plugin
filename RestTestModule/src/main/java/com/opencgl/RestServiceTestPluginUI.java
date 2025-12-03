package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.plugin.api.PluginUI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class RestServiceTestPluginUI implements PluginUI {

    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;

    public RestServiceTestPluginUI() {
        this.pluginCl = this.getClass().getClassLoader();
    }


    @Override
    public String directoryName() {
        return "测试工具";
    }

    @Override
    public String name() {
        return "REST测试工具";
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
        loader.setLocation(this.getClass().getClassLoader().getResource("RestWidgetView.fxml"));
        Parent root;
        try {
            root = loader.load();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    @Override
    public void dispose() {

    }
}
