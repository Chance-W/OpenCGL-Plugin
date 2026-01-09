package com.opencgl.base.utils.i18n;

import java.text.MessageFormat;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;

/**
 * 国际化解析器，用于支持多模块隔离的资源加载。
 */
public class I18nResolver {

    private final String bundleBaseName;
    private final ClassLoader classLoader;

    /** 找不到任何 bundle 时的空兜底，避免 FXMLLoader.setResources(null) 报 NPE */
    private static final ResourceBundle EMPTY_BUNDLE = new ListResourceBundle() {
        @Override
        protected Object[][] getContents() { return new Object[0][]; }
    };

    /**
     * 构造函数
     *
     * @param bundleBaseName 资源文件基准路径 (e.g., "com/opencgl/i18n/base")
     */
    public I18nResolver(String bundleBaseName) {
        this(bundleBaseName, I18nResolver.class.getClassLoader());
    }

    /**
     * 构造函数
     *
     * @param bundleBaseName 资源文件基准路径
     * @param classLoader    用于加载资源的 ClassLoader (主要用于插件隔离)
     */
    public I18nResolver(String bundleBaseName, ClassLoader classLoader) {
        this.bundleBaseName = bundleBaseName;
        this.classLoader = classLoader;
    }

    /**
     * 获取指定语言的资源包。
     * <p>优先返回目标 locale 的 bundle；若找不到则自动回退：
     * <ol>
     *   <li>尝试系统默认 locale（通常是启动时的系统语言）</li>
     *   <li>尝试中文简体（兜底，大多数 bundle 必有中文版）</li>
     *   <li>返回空 bundle，避免抛出 MissingResourceException 导致整个视图崩溃</li>
     * </ol>
     */
    public ResourceBundle getBundle(Locale locale) {
        // 1. 尝试请求的 locale
        // 注意：捕获 Exception 而非仅 MissingResourceException，
        // 因为 .properties 文件若含非法 \\uXXX 转义（< 4位十六进制），
        // Java Properties 解析时会抛 IllegalArgumentException，需一并拦截。
        try {
            return ResourceBundle.getBundle(bundleBaseName, locale, classLoader);
        } catch (Exception ignored) { }

        // 2. 回退到默认语言（配置的首选语言）
        Locale defaultLocale = BaseLanguage.defaultLanguage().getLocale();
        if (!locale.equals(defaultLocale)) {
            try {
                return ResourceBundle.getBundle(bundleBaseName, defaultLocale, classLoader);
            } catch (Exception ignored) { }
        }

        // 3. 最终兜底：返回空 bundle，保证 FXMLLoader 不 NPE
        return EMPTY_BUNDLE;
    }

    /**
     * 获取翻译文本（找不到 key 时返回 key 本身）
     */
    public String get(String key, Object... args) {
        try {
            ResourceBundle bundle = getBundle(BaseI18N.getLocale());
            String s = bundle.getString(key);
            return MessageFormat.format(s, args);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * 获取翻译文本或默认语言的翻译
     */
    public String getOrDefault(String key, Object... args) {
        try {
            ResourceBundle bundle = getBundle(BaseI18N.getLocale());
            String s = bundle.getString(key);
            return MessageFormat.format(s, args);
        } catch (Exception ex) {
            // 回退到默认语言
            try {
                ResourceBundle bundle = getBundle(BaseLanguage.defaultLanguage().getLocale());
                return MessageFormat.format(bundle.getString(key), args);
            } catch (Exception e) {
                return key;
            }
        }
    }

    /**
     * 获取翻译文本或指定的默认字符串
     */
    public String getOrDefault(String key, String def, Object... args) {
        try {
            ResourceBundle bundle = getBundle(BaseI18N.getLocale());
            String s = bundle.getString(key);
            return MessageFormat.format(s, args);
        } catch (Exception ex) {
            try {
                return MessageFormat.format(def, args);
            } catch (Exception e) {
                return def;
            }
        }
    }

    /**
     * 获取具有全局语言联动能力的 Binding
     */
    public StringBinding getBinding(String key, Object... args) {
        return Bindings.createStringBinding(() -> getOrDefault(key, key, args), BaseI18N.localeProperty());
    }

    /**
     * 获取具有全局语言联动能力的 Binding (基于 Callable)
     */
    public StringBinding getBinding(Callable<String> callable) {
        return Bindings.createStringBinding(callable, BaseI18N.localeProperty());
    }
}
