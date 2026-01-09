package com.opencgl.redis.i18n;

import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.utils.i18n.I18nResolver;
import javafx.beans.binding.StringBinding;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

/**
 * Redis Module I18n Utility
 */
public class I18N {
    private static final I18nResolver RESOLVER = new I18nResolver(
            "com.opencgl.i18n.RedisModule",
            I18N.class.getClassLoader());

    public static String get(String key, Object... args) {
        return RESOLVER.get(key, args);
    }

    public static StringBinding getBinding(String key, Object... args) {
        return RESOLVER.getBinding(key, args);
    }

    public static StringBinding getBinding(Callable<String> callable) {
        return RESOLVER.getBinding(callable);
    }

    public static ResourceBundle getBundle(Locale locale) {
        return RESOLVER.getBundle(locale);
    }

    public static Locale getLocale() {
        return BaseI18N.getLocale();
    }
}
