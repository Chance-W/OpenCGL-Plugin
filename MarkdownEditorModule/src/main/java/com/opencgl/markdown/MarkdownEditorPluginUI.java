package com.opencgl.markdown;

import com.opencgl.api.PluginUI;
import com.opencgl.markdown.controller.MarkdownEditorController;
import com.opencgl.markdown.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * Markdown编辑器插件入口
 */
public class MarkdownEditorPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownEditorPluginUI.class);
    private MarkdownEditorController controller;

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
            URL fxmlUrl = getClass().getResource("/com/opencgl/markdown/views/MarkdownEditorView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("无法找到FXML文件");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setClassLoader(getClass().getClassLoader());
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            controller = new MarkdownEditorController();
            loader.setController(controller);
            return loader.load();
            
        } catch (Exception e) {
            logger.error("加载Markdown编辑器UI失败", e);
            throw new RuntimeException("加载Markdown编辑器UI失败", e);
        }
    }

    @Override
    public void dispose() {
        MarkdownEditorController current = controller;
        controller = null;
        if (current != null) current.dispose();
        logger.info("Markdown编辑器插件已卸载");
    }
}
