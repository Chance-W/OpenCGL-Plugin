package com.opencgl.grpc;

import com.opencgl.api.PluginUI;
import com.opencgl.grpc.i18n.I18N;
import com.opencgl.grpc.controller.GrpcTestController;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;

/**
 * gRPC 测试工具插件入口
 * 支持 gRPC 服务调用测试
 *
 * @author OpenCGL
 */
public class GrpcTestPluginUI implements PluginUI {


    private final ClassLoader pluginCl;
    private GrpcTestController controller;

    public GrpcTestPluginUI() {
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
        return pluginCl.getResource("com/opencgl/grpc/icon.png");
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
        loader.setLocation(pluginCl.getResource("com/opencgl/grpc/views/GrpcTestView.fxml"));
        try {
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load GrpcTestView.fxml", e);
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
    }
}
