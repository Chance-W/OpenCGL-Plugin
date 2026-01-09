package com.opencgl.elasticsearch;

import com.opencgl.api.PluginUI;
import com.opencgl.elasticsearch.i18n.I18N;
import com.opencgl.elasticsearch.controller.ElasticsearchController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * Elasticsearch 工具插件入口
 * 支持索引管理、文档搜索、聚合分析
 *
 * @author OpenCGL
 */
public class ElasticsearchPluginUI implements PluginUI {

    private final ClassLoader pluginCl;
    private ElasticsearchController controller;

    public ElasticsearchPluginUI() {
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
        return pluginCl.getResource("com/opencgl/elasticsearch/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/elasticsearch/views/ElasticsearchView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ElasticsearchView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        ElasticsearchController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) {
            controllerToDispose.dispose();
        }
    }
}
