package com.opencgl.gitlite.i18n;

import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.utils.i18n.I18nResolver;
import javafx.beans.binding.StringBinding;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

/**
 * GitLite \u56FD\u9645\u5316\u5DE5\u5177\u7C7B\u3002
 */
public class I18N {
    private static final I18nResolver RESOLVER = new I18nResolver("com.opencgl.gitlite.i18n.GitLite",
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
