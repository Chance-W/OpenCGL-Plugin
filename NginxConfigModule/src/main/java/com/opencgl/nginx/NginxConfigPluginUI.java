package com.opencgl.nginx;

import com.opencgl.api.PluginUI;
import com.opencgl.nginx.i18n.I18N;
import javafx.fxml.FXMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * Nginx \u914D\u7F6E\u751F\u6210\u5668\u63D2\u4EF6\u5165\u53E3\u3002
 */
public class NginxConfigPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(NginxConfigPluginUI.class);

    @Override
    public String name() {
        return I18N.get("label.name");
    }

    @Override
    public String directoryName() {
        return I18N.get("label.category");
    }

    @Override
    public URL iconPath() {
        return getClass().getResource("/images/nginx.png");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setClassLoader(getClass().getClassLoader());
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setLocation(getClass().getResource("/NginxConfigView.fxml"));
            return loader.load();
        } catch (Exception e) {
            logger.error("Load Nginx config view failed", e);
            throw new RuntimeException("Load Nginx config view failed", e);
        }
    }
}
