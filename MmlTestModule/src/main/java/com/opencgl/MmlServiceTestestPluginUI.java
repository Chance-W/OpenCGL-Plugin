package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.plugin.api.PluginUI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class MmlServiceTestestPluginUI implements PluginUI {

    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;

    public MmlServiceTestestPluginUI() {
        this.pluginCl = this.getClass().getClassLoader();
    }


    @Override
    public String directoryName() {
        return "测试工具";
    }

    @Override
    public String name() {
        return "MML测试工具";
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
        loader.setLocation(this.getClass().getClassLoader().getResource("MmlWidgetView.fxml"));
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
