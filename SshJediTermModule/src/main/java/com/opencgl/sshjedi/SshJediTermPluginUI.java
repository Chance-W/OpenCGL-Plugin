package com.opencgl.sshjedi;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.sshjedi.controller.SshJediTermController;
import com.opencgl.sshjedi.i18n.I18N;
import com.opencgl.sshjedi.lifecycle.LifecycleDisposer;
import javafx.fxml.FXMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSH\u7EC8\u7AEF\u5DE5\u5177\u63D2\u4EF6\u5165\u53E3\u3002
 */
public class SshJediTermPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(SshJediTermPluginUI.class);
    private final ClassLoader pluginCl = this.getClass().getClassLoader();
    private final LifecycleDisposer lifecycle = new LifecycleDisposer();
    private SshJediTermController controller;

    public SshJediTermPluginUI() {
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
        return null;
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        if (lifecycle.isDisposed()) {
            throw new IllegalStateException("SSH JediTerm plugin is already disposed");
        }
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setResources(I18N.getBundle(I18N.getLocale()));
        loader.setLocation(pluginCl.getResource("com/opencgl/sshjedi/views/SshJediTermView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dispose() {
        lifecycle.dispose(error -> logger.warn("Failed to dispose SSH JediTerm plugin resource", error),
                () -> {
                    if (controller != null) {
                        controller.dispose();
                    }
                },
                () -> controller = null);
    }
}
