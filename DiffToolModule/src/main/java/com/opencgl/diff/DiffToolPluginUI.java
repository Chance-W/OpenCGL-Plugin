package com.opencgl.diff;

import com.opencgl.api.PluginUI;
import com.opencgl.diff.i18n.I18N;
import com.opencgl.diff.controller.DiffToolController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * 文本差异对比工具插件
 *
 * @author OpenCGL
 */
public class DiffToolPluginUI implements PluginUI {

    private final ClassLoader pluginCl;
    private DiffToolController controller;

    public DiffToolPluginUI() {
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
        return pluginCl.getResource("com/opencgl/diff/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/diff/views/DiffToolView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DiffToolView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        DiffToolController current = controller;
        controller = null;
        if (current != null) current.dispose();
    }
}
