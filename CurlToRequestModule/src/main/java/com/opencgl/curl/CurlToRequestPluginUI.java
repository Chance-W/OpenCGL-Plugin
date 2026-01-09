package com.opencgl.curl;

import com.opencgl.api.PluginUI;
import com.opencgl.curl.i18n.I18N;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

public class CurlToRequestPluginUI implements PluginUI {

    private final ClassLoader pluginCl = getClass().getClassLoader();

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
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(pluginCl);
            FXMLLoader loader = new FXMLLoader();
            loader.setClassLoader(pluginCl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setLocation(pluginCl.getResource("CurlToRequestView.fxml"));
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Load CurlToRequestView failed", e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public void dispose() {
    }
}
