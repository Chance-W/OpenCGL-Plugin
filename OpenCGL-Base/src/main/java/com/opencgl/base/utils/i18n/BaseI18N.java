package com.opencgl.base.utils.i18n;

import java.util.Locale;

import com.opencgl.base.listener.Config;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.binding.StringBinding;
import java.util.concurrent.Callable;

/**
 * 全局国际化管理器。
 * 该类持有唯一的全局 Locale 状态，并为主程序提供基础资源的解析。
 */
public class BaseI18N {
    // 全局唯一的语言状态
    private static final ObjectProperty<Locale> locale = new SimpleObjectProperty<>();

    // Base 模块自身的解析器
    private static final I18nResolver BASE_RESOLVER = new I18nResolver("com/opencgl/i18n/base");

    static {
        // 优先从配置加载
        String savedLang = Config.get("language");
        BaseLanguage lang = BaseLanguage.defaultLanguage();
        if (savedLang != null) {
            try {
                lang = BaseLanguage.valueOf(savedLang);
            } catch (Exception e) {
                // 忽略非法配置，使用默认
            }
        }
        setLanguage(lang);
        locale.addListener(invalidated -> Locale.setDefault(getLocale()));
    }

    public static ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    public static Locale getLocale() {
        return locale.get();
    }

    public static void setLanguage(BaseLanguage baseLanguage) {
        locale.set(baseLanguage.getLocale());
        // 持久化保存
        com.opencgl.base.listener.Config.updateSingleConfig("language", baseLanguage.name());
    }

    // ====== 静态代理方法，用于加载 Base 模块自身的资源 ======

    public static String get(String key, Object... args) {
        return BASE_RESOLVER.get(key, args);
    }

    public static String getOrDefault(String key, Object... args) {
        return BASE_RESOLVER.getOrDefault(key, args);
    }

    public static String getOrDefault(String key, String def, Object... args) {
        return BASE_RESOLVER.getOrDefault(key, def, args);
    }

    public static StringBinding getBinding(String key, Object... args) {
        return BASE_RESOLVER.getBinding(key, args);
    }

    public static StringBinding getBinding(Callable<String> callable) {
        return BASE_RESOLVER.getBinding(callable);
    }

    public static BaseLanguage[] getSupportedLanguages() {
        return BaseLanguage.values();
    }
}
