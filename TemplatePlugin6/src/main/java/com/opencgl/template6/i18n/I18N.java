package com.opencgl.template6.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * TemplatePlugin6 国际化工具类（自定义实现，不依赖 OpenCGL-Base）。
 * 使用 JDK ResourceBundle + MessageFormat，locale 取当前 JVM 默认。
 */
public class I18N {
    private static final String BUNDLE_BASE = "com.opencgl.template6.i18n.Template6";

    public static String get(String key, Object... args) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, getLocale(), I18N.class.getClassLoader());
        if (!bundle.containsKey(key)) {
            return key;
        }
        String pattern = bundle.getString(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    public static Locale getLocale() {
        return Locale.getDefault();
    }
}
