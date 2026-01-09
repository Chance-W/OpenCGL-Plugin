package com.opencgl.docconverter;

import com.opencgl.api.PluginUI;
import com.opencgl.docconverter.controller.DocConverterController;
import com.opencgl.docconverter.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 文档转换器插件入口（复刻 Markdown 编辑器功能，支持多语言、主题、LoadingMask）。
 */
public class DocConverterPluginUI implements PluginUI {
    private static final Logger logger = LoggerFactory.getLogger(DocConverterPluginUI.class);
    private DocConverterController controller;

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
            URL fxmlUrl = getClass().getResource("/com/opencgl/docconverter/views/DocConverterView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("FXML not found: DocConverterView.fxml");
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setClassLoader(getClass().getClassLoader());
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setController(new DocConverterController());
            Node view = loader.load();
            controller = loader.getController();
            return view;
        } catch (Exception e) {
            logger.error("Load DocConverter UI failed", e);
            throw new RuntimeException("Load DocConverter UI failed", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
        logger.info("DocConverter plugin disposed");
    }
}
