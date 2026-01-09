package com.opencgl.dubbo;

import java.io.IOException;
import java.net.URL;
import com.opencgl.dubbo.i18n.I18N;
import com.opencgl.api.PluginUI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import com.opencgl.dubbo.controller.DubboWidgetController;

/**
 * Dubbo 服务测试插件 UI 实现类。
 * 已集成 OpenCGL-Base，通过 I18nResolver 实现自动语言切换，无需实现 PluginI18n。
 */
public class DubboServiceTestPluginUI implements PluginUI {

    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;
    private DubboWidgetController controller;

    public DubboServiceTestPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/dubbo.png");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        // 设置当前语言的资源包，用于 FXML 内的 %key 解析
        loader.setResources(I18N.getBundle(I18N.getLocale()));
        loader.setLocation(this.getClass().getClassLoader().getResource("DubboWidgetView.fxml"));
        Parent root;
        try {
            root = loader.load();
            controller = loader.getController();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    @Override
    public void dispose() {
        DubboWidgetController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) controllerToDispose.dispose();
    }
}
