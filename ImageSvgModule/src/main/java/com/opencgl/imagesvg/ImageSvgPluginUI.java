package com.opencgl.imagesvg;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.imagesvg.i18n.I18N;
import javafx.fxml.FXMLLoader;

/**
 * 图片转SVG工具插件入口
 */
public class ImageSvgPluginUI implements PluginUI {


    private final ClassLoader pluginCl;

    public ImageSvgPluginUI() {
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
        loader.setLocation(pluginCl.getResource("com/opencgl/imagesvg/views/ImageSvgView.fxml"));
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
