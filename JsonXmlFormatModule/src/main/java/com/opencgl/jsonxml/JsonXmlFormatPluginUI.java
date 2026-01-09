package com.opencgl.jsonxml;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.jsonxml.i18n.I18N;
import javafx.fxml.FXMLLoader;

public class JsonXmlFormatPluginUI implements PluginUI {


    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;

    public JsonXmlFormatPluginUI() {
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
        return pluginCl.getResource("icon/json.png");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        // 保存当前线程的 ClassLoader
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            // 设置插件 ClassLoader 为线程上下文 ClassLoader
            Thread.currentThread().setContextClassLoader(pluginCl);
            
            FXMLLoader loader = new FXMLLoader();
            // 使用插件 ClassLoader，它的 parent 是主应用 ClassLoader
            // 通过委托机制可以加载 OpenCGL-Base 中的类
            loader.setClassLoader(pluginCl);
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            loader.setLocation(pluginCl.getResource("JsonXmlFormatWidgetView.fxml"));
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("加载JSON/XML格式化界面失败", e);
        } finally {
            // 恢复原来的 ClassLoader
            Thread.currentThread().setContextClassLoader(originalCl);
        }
    }

    @Override
    public void dispose() {

    }
}
