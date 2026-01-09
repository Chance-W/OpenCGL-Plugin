package com.opencgl.scriptdebug;

import com.opencgl.api.PluginUI;
import com.opencgl.scriptdebug.i18n.I18N;
import com.opencgl.scriptdebug.controller.ScriptDebugWidgetController;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.net.URL;

/**
 * 脚本调试模块入口
 */
public class ScriptDebugAppLauncherPlugin implements PluginUI {


    private final ClassLoader pluginCl;
    private ScriptDebugWidgetController controller;

    public ScriptDebugAppLauncherPlugin() {
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
        return pluginCl.getResource("images/script_debug_icon.png");
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
            loader.setLocation(pluginCl.getResource("ScriptDebugWidgetView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("加载脚本调试界面失败", e);
        }
    }

    @Override
    public void dispose() {
        ScriptDebugWidgetController current = controller;
        controller = null;
        if (current != null) current.dispose();
    }
}
