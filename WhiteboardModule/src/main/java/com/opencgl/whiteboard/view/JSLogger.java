package com.opencgl.whiteboard.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通过 JSObject.setMember 注入到浏览器 window 中，
 * 让 JavaScript 能调用 Java 的 SLF4J 日志输出。
 */
public class JSLogger {
    private static final Logger log = LoggerFactory.getLogger(JSLogger.class);

    public void info(String msg) {
        log.info("[WebView JS] {}", msg);
    }

    public void error(String msg) {
        log.error("[WebView JS] {}", msg);
    }

    public void warn(String msg) {
        log.warn("[WebView JS] {}", msg);
    }
}
