package com.opencgl.plugin.ragingestion;

import com.opencgl.api.PluginUI;
import javafx.fxml.FXMLLoader;
import com.opencgl.plugin.ragingestion.controller.RagIngestionWidgetController;

import java.io.IOException;
import java.net.URL;

/**
 * OpenCGL 插件 UI 接口实现类。
 * 通过 SPI 机制挂载进入 OpenCGL 客户端主界面左侧菜单。
 */
public class RagIngestionPluginUI implements PluginUI {
    private RagIngestionWidgetController controller;

    @Override
    public String directoryName() {
        return "AI/知识库工具";
    }

    @Override
    public String name() {
        return "文档向量化与 RAG 入库台";
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
        loader.setClassLoader(this.getClass().getClassLoader());
        loader.setLocation(this.getClass().getResource("/com/opencgl/plugin/ragingestion/views/RagIngestionWidgetView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("初始化 RagIngestionToolModule 视图失败", e);
        }
    }

    @Override
    public void dispose() {
        RagIngestionWidgetController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) controllerToDispose.dispose();
    }
}
