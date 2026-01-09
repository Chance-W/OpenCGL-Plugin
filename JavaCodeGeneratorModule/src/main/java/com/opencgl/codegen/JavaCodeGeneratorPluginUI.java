package com.opencgl.codegen;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.codegen.i18n.I18N;
import javafx.fxml.FXMLLoader;

/**
 * Java代码生成器插件入口
 */
public class JavaCodeGeneratorPluginUI implements PluginUI {


    private final ClassLoader pluginCl;

    public JavaCodeGeneratorPluginUI() {
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
        loader.setLocation(pluginCl.getResource("com/opencgl/codegen/views/CodeGenView.fxml"));
        try {
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dispose() {
    }
}
