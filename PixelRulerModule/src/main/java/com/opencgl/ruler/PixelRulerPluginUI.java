package com.opencgl.ruler;

import com.opencgl.api.PluginUI;
import com.opencgl.ruler.controller.PixelRulerController;
import com.opencgl.ruler.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class PixelRulerPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(PixelRulerPluginUI.class);
    private PixelRulerController controller;

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
            URL fxmlUrl = getClass().getResource("/com/opencgl/ruler/views/PixelRulerView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("无法找到FXML文件");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setController(new PixelRulerController());
            Node view = loader.load();
            controller = loader.getController();
            return view;
            
        } catch (Exception e) {
            logger.error("加载屏幕尺子UI失败", e);
            throw new RuntimeException("加载屏幕尺子UI失败", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
        logger.info("屏幕尺子插件已卸载");
    }
}
