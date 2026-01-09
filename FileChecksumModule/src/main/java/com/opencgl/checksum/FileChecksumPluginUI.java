package com.opencgl.checksum;

import com.opencgl.checksum.i18n.I18N;
import com.opencgl.api.PluginUI;
import com.opencgl.checksum.controller.FileChecksumController;
import javafx.fxml.FXMLLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 文件哈希计算器插件入口
 */
public class FileChecksumPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(FileChecksumPluginUI.class);
    private FileChecksumController controller;

    @Override
    public String name() {
        return I18N.get("msg.plugin_name");
    }

    @Override
    public String directoryName() {
        return I18N.get("msg.plugin_group");
    }

    @Override
    public URL iconPath() {
        return getClass().getResource("/images/checksum.png");
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
            loader.setLocation(getClass().getResource("/FileChecksumView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (Exception e) {
            logger.error(I18N.get("msg.load_failed"), e);
            throw new RuntimeException(I18N.get("msg.load_failed"), e);
        }
    }

    @Override
    public void dispose() {
        FileChecksumController current = controller;
        controller = null;
        if (current != null) current.dispose();
    }
}
