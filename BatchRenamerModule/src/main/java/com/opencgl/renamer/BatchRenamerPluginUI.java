package com.opencgl.renamer;

import com.opencgl.api.PluginUI;
import com.opencgl.renamer.controller.BatchRenamerController;
import com.opencgl.renamer.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class BatchRenamerPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(BatchRenamerPluginUI.class);

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
            URL fxmlUrl = getClass().getResource("/com/opencgl/renamer/views/BatchRenamerView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("无法找到FXML文件");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setController(new BatchRenamerController());
            return loader.load();
            
        } catch (Exception e) {
            logger.error("加载批量重命名UI失败", e);
            throw new RuntimeException("加载批量重命名UI失败", e);
        }
    }

    @Override
    public void dispose() {
        logger.info("批量重命名插件已卸载");
    }
}
