package com.opencgl.processport;

import com.opencgl.api.PluginUI;
import com.opencgl.processport.i18n.I18N;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

public class ProcessPortPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();

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
        return null;
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
        loader.setLocation(pluginCl.getResource("com/opencgl/processport/views/ProcessPortView.fxml"));
        try {
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ProcessPortView.fxml", e);
        }
    }

    @Override
    public void dispose() {
    }
}
