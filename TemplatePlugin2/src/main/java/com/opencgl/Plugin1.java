package com.opencgl;

import java.net.URL;

import com.opencgl.plugin.api.PluginUI;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

/**
 * 示例插件B：实现通用Plugin接口，包含静态资源
 */
public class Plugin1 implements PluginUI {


    @Override
    public String directoryName() {
        return "插件开发模板演示目录";
    }

    @Override
    public String name() {
        return "插件开发模板2";
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
        // 实例变量：插件自身资源
         Button uiButton = new Button("test1：点击调用Dubbo1");

        StackPane pane = new StackPane();
        pane.setStyle("-fx-background-color: #e0f7fa; -fx-padding: 20px;");

        pane.getChildren().add(uiButton);
        return pane;
    }

    @Override
    public void dispose() {
        System.out.println("[PluginB] 静态资源已清理");
    }
}

