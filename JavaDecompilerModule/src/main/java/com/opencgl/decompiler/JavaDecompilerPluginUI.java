package com.opencgl.decompiler;

import com.opencgl.api.PluginUI;
import com.opencgl.decompiler.i18n.I18N;
import com.opencgl.decompiler.controller.DecompilerController;
import javafx.fxml.FXMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Java反编译器插件入口
 */
public class JavaDecompilerPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(JavaDecompilerPluginUI.class);
    private final ClassLoader pluginCl;
    private DecompilerController controller;

    public JavaDecompilerPluginUI() {
        this.pluginCl = this.getClass().getClassLoader();
    }

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
    public Object createView() {
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setResources(I18N.getBundle(I18N.getLocale()));
        loader.setLocation(pluginCl.getResource("com/opencgl/decompiler/views/DecompilerView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            logger.error("加载反编译器UI失败", e);
            throw new RuntimeException("加载反编译器UI失败", e);
        }
    }

    @Override
    public void dispose() {
        DecompilerController current = controller;
        controller = null;
        if (current != null) current.dispose();
        logger.info("Java反编译器插件关闭");
    }
}
