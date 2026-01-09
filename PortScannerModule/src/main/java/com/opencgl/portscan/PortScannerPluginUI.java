package com.opencgl.portscan;

import com.opencgl.api.PluginUI;
import com.opencgl.portscan.i18n.I18N;
import com.opencgl.portscan.controller.PortScannerController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 端口扫描插件入口
 */
public class PortScannerPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(PortScannerPluginUI.class);
    private PortScannerController controller;

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
        return getClass().getResource("/images/network.png");
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
            loader.setLocation(getClass().getResource("/PortScannerView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (Exception e) {
            logger.error("加载端口扫描界面失败", e);
            throw new RuntimeException("加载端口扫描界面失败", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
    }
}
