package com.opencgl.checksum.i18n;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.utils.i18n.I18nResolver;
import javafx.beans.binding.StringBinding;

/**
 * File Checksum I18n Utility
 */
public class I18N {
    private static final I18nResolver resolver = new I18nResolver("com.opencgl.checksum.i18n.FileChecksum",
            I18N.class.getClassLoader());

    public static String get(String key, Object... args) {
        return resolver.get(key, args);
    }

    public static StringBinding getBinding(String key, Object... args) {
        return resolver.getBinding(key, args);
    }

    public static String getOrDefault(String key, String def, Object... args) {
        return resolver.getOrDefault(key, def, args);
    }

    public static StringBinding getBinding(Callable<String> callable) {
        return resolver.getBinding(callable);
    }

    public static Locale getLocale() {
        return BaseI18N.getLocale();
    }

    public static ResourceBundle getBundle(Locale locale) {
        return resolver.getBundle(locale);
    }
}
