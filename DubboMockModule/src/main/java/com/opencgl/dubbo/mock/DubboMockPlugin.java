package com.opencgl.dubbo.mock;

import com.opencgl.api.PluginUI;
import com.opencgl.dubbo.mock.i18n.I18N;
import com.opencgl.dubbo.mock.controller.DubboMockController;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class DubboMockPlugin implements PluginUI {
    private static final Logger log = LoggerFactory.getLogger(DubboMockPlugin.class);
    private DubboMockController controller;

    @Override
    public Object createView() {
        try {
            URL resource = getClass().getResource("/com/opencgl/dubbo/mock/views/DubboMockView.fxml");
            if (resource == null) {
                log.error("DubboMockView.fxml 找不到路径");
                return null;
            }
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setClassLoader(getClass().getClassLoader());
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            Object view = loader.load();
            controller = loader.getController();
            return view;
        } catch (Exception e) {
            log.error("加载 Dubbo Mock 模块 UI 失败", e);
            throw new RuntimeException("Loaded Dubbo Mock Module failed", e);
        }
    }

    @Override
    public String name() {
        return I18N.get("label.name");
    }

    @Override
    public String description() {
        return "Dubbo 接口通用 Mock 服务端(泛化实现)，无需依赖 JAR 包";
    }

    @Override
    public String version() {
        String version = this.getClass().getPackage().getImplementationVersion();
        return version != null ? version : "1.0.0";
    }

    @Override
    public String directoryName() {
        return I18N.get("label.category");
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
    public void dispose() {
        DubboMockController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) {
            try { controllerToDispose.dispose(); } catch (RuntimeException e) { log.error("释放 Dubbo Mock 失败", e); }
        }
    }
}
