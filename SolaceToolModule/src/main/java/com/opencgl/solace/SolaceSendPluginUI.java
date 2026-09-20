package com.opencgl.solace;

import com.opencgl.api.PluginUI;
import com.opencgl.solace.ui.SolaceToolView;

import java.net.URL;

public final class SolaceSendPluginUI implements PluginUI {
    private SolaceToolView view;

    @Override public String pluginId() { return "com.opencgl.solace.send"; }
    @Override public String directoryName() { return "运维工具"; }
    @Override public String category() { return "运维工具"; }
    @Override public String name() { return "Solace 发送"; }
    @Override public String description() { return "Solace Topic/Queue 消息发送与连接配置管理"; }
    @Override public URL iconPath() { return getClass().getClassLoader().getResource("com/opencgl/solace/solace.png"); }
    @Override public UIType type() { return UIType.JAVAFX; }
    @Override public Object createView() {
        view = new SolaceToolView(SolaceToolView.Mode.SEND);
        URL css = getClass().getClassLoader().getResource("com/opencgl/solace/solace.css");
        if (css != null) view.root().getStylesheets().add(css.toExternalForm());
        return view.root();
    }
    @Override public void dispose() { if (view != null) { view.close(); view = null; } }
}
