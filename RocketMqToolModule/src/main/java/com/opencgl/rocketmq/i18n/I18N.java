package com.opencgl.rocketmq.i18n;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.utils.i18n.BaseLanguage;
import com.opencgl.base.utils.i18n.I18nResolver;
import javafx.beans.binding.StringBinding;

/**
 * RocketMQ 模块国际化工具类。
 * 适配 OpenCGL-Base 的 I18nResolver，实现全局语言联动。
 */
public class I18N {
    private static final I18nResolver RESOLVER = new I18nResolver(
            "com.opencgl.rocketmq.i18n.RocketMqTool",
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

    public static void setLanguage(BaseLanguage language) {
        BaseI18N.setLanguage(language);
    }
}
