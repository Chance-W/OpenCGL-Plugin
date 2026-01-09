package com.opencgl.cert;

import com.opencgl.cert.controller.CertGeneratorController;
import com.opencgl.cert.i18n.I18N;
import com.opencgl.api.PluginI18n;
import com.opencgl.api.PluginUI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * HTTPS Certificate Generator Plugin Entry Point
 */
public class CertGeneratorPluginUI implements PluginUI, PluginI18n {

    private static final Logger logger = LoggerFactory.getLogger(CertGeneratorPluginUI.class);
    private CertGeneratorController controller;

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
        return null; // TODO: Add icon later
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Node createView() {
        try {
            URL fxmlUrl = getClass().getResource("/com/opencgl/cert/views/CertGeneratorView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("Cannot find CertGeneratorView.fxml");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            // Manually set controller to avoid ClassLoader issues
           // loader.setController(new CertGeneratorController());
            Node view = loader.load();
            controller = loader.getController();
            return view;

        } catch (Exception e) {
            logger.error("Failed to load Cert Generator UI", e);
            throw new RuntimeException("Failed to load Cert Generator UI", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
        logger.info("Cert Generator Plugin disposed");
    }

    @Override
    public void onLanguageChange(Locale locale) {
        // 插件内部逻辑，大部分 UI 使用了 I18nResolver 和 StringBinding 自动更新
    }
}
