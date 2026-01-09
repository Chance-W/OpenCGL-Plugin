package com.opencgl.template2.i18n;

import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.utils.i18n.I18nResolver;
import javafx.beans.binding.StringBinding;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

/**
 * TemplatePlugin2 国际化工具类。
 * 包名使用 com.opencgl.template2.i18n，避免与主程序 com.opencgl.i18n.I18N 发生
 * URLClassLoader 父优先委托冲突（父 ClassLoader 先找到主程序的同名类，导致插件的
 * bundle 永远无法加载）。
 */
public class I18N {
    private static final I18nResolver RESOLVER = new I18nResolver(
            "com.opencgl.template2.i18n.Template2",
            I18N.class.getClassLoader());

    public static String get(String key, Object... args) {
        return RESOLVER.get(key, args);
    }

    public static String getOrDefault(String key, String def, Object... args) {
        return RESOLVER.getOrDefault(key, def, args);
    }

    public static StringBinding getBinding(String key, Object... args) {
        return RESOLVER.getBinding(key, args);
    }

    public static StringBinding getBinding(Callable<String> callable) {
        return RESOLVER.getBinding(callable);
    }

    public static Locale getLocale() {
        return BaseI18N.getLocale();
    }

    public static ResourceBundle getBundle(Locale locale) {
        return RESOLVER.getBundle(locale);
    }
}
