package com.opencgl.clipboard;

import com.opencgl.api.PluginUI;
import com.opencgl.clipboard.controller.ClipboardHistoryController;
import com.opencgl.clipboard.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class ClipboardHistoryPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(ClipboardHistoryPluginUI.class);
    private ClipboardHistoryController controller;

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
            URL fxmlUrl = getClass().getResource("/com/opencgl/clipboard/views/ClipboardHistoryView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("无法找到FXML文件");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setController(new ClipboardHistoryController());
            Node view = loader.load();
            controller = loader.getController();
            return view;
            
        } catch (Exception e) {
            logger.error("加载剪贴板历史UI失败", e);
            throw new RuntimeException("加载剪贴板历史UI失败", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
        logger.info("剪贴板历史插件已卸载");
    }
}
