package com.opencgl.base.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Objects;

/**
 * CSS 资源加载工具类
 * 支持开发模式 (Dev Mode) 下从本地文件系统热重载
 *
 * @author Antigravity
 */
public class CssUtil {

    private static final Logger logger = LoggerFactory.getLogger(CssUtil.class);

    // 系统属性键定义
    public static final String KEY_DEV_MODE = "opencgl.dev.mode";
    public static final String KEY_DEV_CSS_PATH = "opencgl.dev.cssPath";

    /**
     * 获取资源路径 (使用默认的 OpenCGL-Base 开发路径配置)
     *
     * @param path 资源相对路径 (e.g. "/com/opencgl/base/css/style.css")
     * @return 外部形式的 URL 字符串
     */
    public static String getResourcePath(String path) {
        // 从系统属性获取 Base 模块的开发路径
        String defaultDevPath = System.getProperty(KEY_DEV_CSS_PATH);
        return getResourcePath(CssUtil.class, path, defaultDevPath);
    }

    /**
     * 通用资源加载方法
     *
     * @param clazz       用于 Classpath 加载的类 (通常是调用者)
     * @param path        资源相对路径
     * @param devBasePath 开发模式下的绝对根路径 (只在 Dev Mode 开启且不为空时使用)
     * @return 外部形式的 URL 字符串
     */
    public static String getResourcePath(Class<?> clazz, String path, String devBasePath) {
        // 开发模式：尝试从本地源码目录加载
        if (isDevMode() && devBasePath != null && !devBasePath.isEmpty()) {
            try {
                logger.info("Now is devMode and get resource css [{}]",devBasePath);
                // 规范化路径：统一使用 / 处理，避免 Windows 下的转义问题
                String normalizedBasePath = devBasePath.replace("\\", "/");
                // 移除尾部斜杠，避免拼接时出现双斜杠
                if (normalizedBasePath.endsWith("/")) {
                    normalizedBasePath = normalizedBasePath.substring(0, normalizedBasePath.length() - 1);
                }

                File file = new File(normalizedBasePath + path);
                if (file.exists()) {
                    // toURI().toURL() 会自动处理文件名中的空格和特殊字符编码
                    String url = file.toURI().toURL().toExternalForm();
                    // 添加时间戳参数以防止 JavaFX 缓存，确保修改实时生效
                    return url + "?t=" + System.currentTimeMillis();
                } else {
                    logger.debug("Dev resource not found at: {}", file.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.error("Failed to load dev resource: " + path, e);
            }
        }

        // 生产模式: 从 Classpath 加载
        try {
            URL url = clazz.getResource(path);
            return url != null ? url.toExternalForm() : null;
        } catch (Exception e) {
            logger.warn("Resource not found in classpath: {}", path);
            return null;
        }
    }

    /**
     * 判断是否处于开发模式
     */
    public static boolean isDevMode() {
        return Boolean.getBoolean(KEY_DEV_MODE);
    }
}
