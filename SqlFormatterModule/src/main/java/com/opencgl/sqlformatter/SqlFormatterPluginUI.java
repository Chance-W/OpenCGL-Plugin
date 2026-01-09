package com.opencgl.sqlformatter;

import com.opencgl.api.PluginUI;
import com.opencgl.sqlformatter.i18n.I18N;
import javafx.fxml.FXMLLoader;

import java.net.URL;

public class SqlFormatterPluginUI implements PluginUI {

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
        try {
            ClassLoader cl = getClass().getClassLoader();
            URL fxmlUrl = cl.getResource("com/opencgl/sqlformatter/views/SqlFormatterView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("FXML not found: SqlFormatterView.fxml");
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setClassLoader(cl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            return loader.load();
        } catch (Exception e) {
            throw new RuntimeException("Load SQL Formatter UI failed", e);
        }
    }
}
