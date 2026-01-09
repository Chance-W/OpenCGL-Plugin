package com.opencgl.qr.i18n;

import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.utils.i18n.I18nResolver;
import javafx.beans.binding.StringBinding;

import java.util.Locale;
import java.util.ResourceBundle;

public class I18N {
    private static final I18nResolver RESOLVER = new I18nResolver(
            "com/opencgl/qr/i18n/QRCodeGenerate",
            I18N.class.getClassLoader());

    public static String get(String key) {
        return RESOLVER.get(key);
    }

    public static StringBinding getBinding(String key) {
        return RESOLVER.getBinding(key);
    }

    public static Locale getLocale() {
        return BaseI18N.getLocale();
    }

    public static ResourceBundle getBundle(Locale locale) {
        return RESOLVER.getBundle(locale != null ? locale : BaseI18N.getLocale());
    }

    /** @deprecated Use getBundle(getLocale()) */
    public static ResourceBundle getBundle() {
        return RESOLVER.getBundle(BaseI18N.getLocale());
    }
}
