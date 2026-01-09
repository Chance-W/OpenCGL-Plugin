package com.opencgl.dbmatchexecute;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.dbmatchexecute.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class DbMatchExecuteToolPluginUI implements PluginUI {


    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;

    public DbMatchExecuteToolPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/db.png");
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
        loader.setLocation(this.getClass().getClassLoader().getResource("DbMatchExecuteWidgetView.fxml"));
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
