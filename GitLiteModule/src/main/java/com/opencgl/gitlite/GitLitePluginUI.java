package com.opencgl.gitlite;

import com.opencgl.api.PluginUI;
import com.opencgl.gitlite.i18n.I18N;
import com.opencgl.gitlite.controller.GitLiteController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * Git \u7B80\u6613\u5DE5\u5177\u63D2\u4EF6\u5165\u53E3\u3002
 */
public class GitLitePluginUI implements PluginUI {
    private static final Logger logger = LoggerFactory.getLogger(GitLitePluginUI.class);
    private GitLiteController controller;

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
        return getClass().getResource("/images/git.png");
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
            loader.setLocation(getClass().getResource("/GitLiteView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (Exception e) {
            logger.error("Load GitLite view failed", e);
            throw new RuntimeException("Load GitLite view failed", e);
        }
    }

    @Override
    public void dispose() {
        GitLiteController current = controller;
        controller = null;
        if (current != null) current.dispose();
    }
}
