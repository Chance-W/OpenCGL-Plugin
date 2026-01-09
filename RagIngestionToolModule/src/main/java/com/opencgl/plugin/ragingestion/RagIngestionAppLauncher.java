package com.opencgl.plugin.ragingestion;

import javafx.application.Application;

/**
 * 非 Application 继承的独立启动包装主类 (Bootstrap Launcher)。
 * 解决 JDK 11+ / JDK 21 直接启动继承自 javafx.application.Application 的类时报
 * “缺少 JavaFX 运行时组件, 需要使用该组件来运行此应用程序”的问题。
 */
public class RagIngestionAppLauncher {

    public static void main(String[] args) {
        Application.launch(RagIngestionStartApplication.class, args);
    }
}
