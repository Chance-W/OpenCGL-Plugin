package com.opencgl.pathextractor;

import com.opencgl.api.PluginUI;
import com.opencgl.pathextractor.controller.PathExtractorController;
import com.opencgl.pathextractor.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class PathExtractorPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(PathExtractorPluginUI.class);

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
    public Node createView() {
        try {
            URL fxmlUrl = getClass().getResource("/com/opencgl/pathextractor/views/PathExtractorView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("无法找到FXML文件");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setClassLoader(getClass().getClassLoader());
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setController(new PathExtractorController());
            return loader.load();
            
        } catch (Exception e) {
            logger.error("加载Path提取器UI失败", e);
            throw new RuntimeException("加载Path提取器UI失败", e);
        }
    }

    @Override
    public void dispose() {
        logger.info("Path提取器插件已卸载");
    }
}
