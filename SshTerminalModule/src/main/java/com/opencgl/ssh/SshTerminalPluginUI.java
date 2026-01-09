package com.opencgl.ssh;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.ssh.controller.SshTerminalController;
import com.opencgl.ssh.i18n.I18N;
import com.opencgl.ssh.lifecycle.LifecycleDisposer;
import javafx.fxml.FXMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSH\u7EC8\u7AEF\u5DE5\u5177\u63D2\u4EF6\u5165\u53E3\u3002
 */
public class SshTerminalPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(SshTerminalPluginUI.class);
    private final ClassLoader pluginCl = this.getClass().getClassLoader();
    private final LifecycleDisposer lifecycle = new LifecycleDisposer();
    private SshTerminalController controller;

    public SshTerminalPluginUI() {
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
            throw new IllegalStateException("SSH Terminal plugin is already disposed");
        }
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setResources(I18N.getBundle(I18N.getLocale()));
        loader.setLocation(pluginCl.getResource("com/opencgl/ssh/views/SshTerminalView.fxml"));
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
        lifecycle.dispose(error -> logger.warn("Failed to dispose SSH Terminal plugin resource", error),
                () -> {
                    if (controller != null) {
                        controller.dispose();
                    }
                },
                () -> controller = null);
    }
}
