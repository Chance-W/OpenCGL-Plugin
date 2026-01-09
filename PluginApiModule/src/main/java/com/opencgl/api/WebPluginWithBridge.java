package com.opencgl.api;

/**
 * WEB 类型插件的可选扩展：需要从 HTML/JS 调用 Java 时实现此接口。
 * 主程序在加载完页面后会通过 WebEngine 将 getJsBridge() 暴露为 window.javaBridge，
 * 页面内可通过 window.javaBridge.xxx() 调用 Java 方法。
 * <p>
 * 不实现本接口的 WEB 插件仅加载 HTML，无 Java 桥接（纯静态或自包含 JS）。
 */
public interface WebPluginWithBridge {

    /**
     * 暴露给页面脚本的 Java 对象，其 public 方法可在 JS 中通过 window.javaBridge.方法名() 调用。
     * 建议返回专用 POJO，避免暴露内部状态。
     */
    Object getJsBridge();
}
