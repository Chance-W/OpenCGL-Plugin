package com.opencgl.plugin.api;

import java.net.URL;

public interface PluginUI {

    enum UIType {JAVAFX, SWING, WEB}


    String directoryName();

    /**
     * 显示名称（用于 Tab / 菜单）
     */
    String name();

    /**
     * 图标资源路径（相对插件 jar 内部或 classpath），可选
     */
    URL iconPath();

    /**
     * 类型（JAVAFX / SWING / WEB）
     */
    UIType type();

    /**
     * 按需创建 UI 对象（仅当用户打开 Tab 时调用）
     * - JAVAFX -> javafx.scene.Node
     * - SWING  -> javax.swing.JComponent
     * - WEB    -> String (html content) or java.net.URL or String url
     */
    Object createView();

    /**
     * 关闭/卸载时清理资源
     */
    default void dispose() {
    }

//    default String icon() {
//        System.out.println("4445555"+ iconPath());
//        if (iconPath().isBlank()) {
//            return null;
//        }
//        try {
//            System.out.println("123123"+ iconPath());
//            return Optional.ofNullable(this.getClass().getClassLoader().getResource(iconPath()))
//                .map(String::valueOf)
//                .orElse(null);
//        }
//        catch (Exception e) {
//            System.out.println(e.getMessage());
//            return null;
//        }
//    }

}