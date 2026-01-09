package com.opencgl.whiteboard.view.i18n;

import java.util.Locale;
import java.util.ResourceBundle;

import com.opencgl.base.utils.i18n.BaseI18N;

public class I18N {
    private static final String BUNDLE_NAME = "com.opencgl.whiteboard.whiteboard";

    public static String get(String key) {
        return ResourceBundle.getBundle(BUNDLE_NAME, getLocale()).getString(key);
    }

    public static Locale getLocale() {
        return BaseI18N.getLocale();
    }
    
    public static ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_NAME, locale);
    }
}
