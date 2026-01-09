package com.opencgl.regex;

import com.opencgl.api.PluginUI;
import com.opencgl.regex.i18n.I18N;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

public class RegexToolLauncherPlugin implements PluginUI {


    private final ClassLoader pluginCl;

    public RegexToolLauncherPlugin() {
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
        return pluginCl.getResource("images/regex.png");
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
            loader.setLocation(pluginCl.getResource("RegexToolView.fxml"));
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("加载正则工具界面失败", e);
        }
    }
}
