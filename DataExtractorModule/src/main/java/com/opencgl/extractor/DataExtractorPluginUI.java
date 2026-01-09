package com.opencgl.extractor;

import com.opencgl.api.PluginUI;
import com.opencgl.extractor.i18n.I18N;
import com.opencgl.extractor.controller.DataExtractorController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * JSON/XML Path 提取器插件入口
 */
public class DataExtractorPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractorPluginUI.class);
    private DataExtractorController controller;

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
        return getClass().getResource("/images/extract.png");
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
            loader.setLocation(getClass().getResource("/DataExtractorView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (Exception e) {
            logger.error("加载数据提取器界面失败", e);
            throw new RuntimeException("加载数据提取器界面失败", e);
        }
    }

    @Override
    public void dispose() {
        DataExtractorController current = controller;
        controller = null;
        if (current != null) current.dispose();
    }
}
