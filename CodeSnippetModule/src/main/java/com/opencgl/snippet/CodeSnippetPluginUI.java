package com.opencgl.snippet;

import com.opencgl.api.PluginUI;
import com.opencgl.snippet.i18n.I18N;
import com.opencgl.snippet.controller.CodeSnippetController;
import javafx.fxml.FXMLLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 代码片段管理器插件入口
 */
public class CodeSnippetPluginUI implements PluginUI {
    private static final Logger logger = LoggerFactory.getLogger(CodeSnippetPluginUI.class);
    private CodeSnippetController controller;

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
        return getClass().getResource("/images/code.png");
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
            loader.setLocation(getClass().getResource("/CodeSnippetView.fxml"));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (Exception e) {
            logger.error("加载代码片段界面失败", e);
            throw new RuntimeException("加载代码片段界面失败", e);
        }
    }

    @Override
    public void dispose() {
        CodeSnippetController current = controller;
        controller = null;
        if (current != null) current.dispose();
    }
}
