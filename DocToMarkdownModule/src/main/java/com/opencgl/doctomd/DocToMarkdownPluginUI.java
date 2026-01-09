package com.opencgl.doctomd;

import com.opencgl.api.PluginUI;
import com.opencgl.doctomd.i18n.I18N;
import com.opencgl.doctomd.controller.DocToMarkdownController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 文档转 Markdown 插件：与 C++ doc-converter 功能一致，
 * 支持 PDF/DOC/DOCX/HTML → Markdown、多语言、主题、进度条。
 */
public class DocToMarkdownPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(DocToMarkdownPluginUI.class);
    private DocToMarkdownController controller;

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
            URL fxmlUrl = getClass().getResource("/com/opencgl/doctomd/views/DocToMarkdownView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("FXML not found: DocToMarkdownView.fxml");
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setClassLoader(getClass().getClassLoader());
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            // Controller 由 FXML 的 fx:controller 指定，此处不要再 setController 否则报 "Controller value already specified"
            Node view = loader.load();
            controller = loader.getController();
            return view;
        } catch (Exception e) {
            logger.error("Failed to load DocToMarkdown UI", e);
            throw new RuntimeException("Failed to load DocToMarkdown UI", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
        logger.info("DocToMarkdown plugin disposed");
    }
}
