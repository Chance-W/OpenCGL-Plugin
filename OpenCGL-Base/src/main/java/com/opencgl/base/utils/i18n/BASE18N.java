package com.opencgl.base.utils.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

@SuppressWarnings("unused")
public class BASE18N {
    private static final ObjectProperty<Locale> locale = new SimpleObjectProperty<>();

    static {
        setLanguage(BaseLanguage.defaultLanguage());
        locale.addListener(invalidated -> Locale.setDefault(getLocale()));
    }

    public static String get(String key, Object... args) {
        ResourceBundle bundle = getBundle(getLocale());
        return MessageFormat.format(bundle.getString(key), args);
    }

    public static String get(BaseLanguage language, String key, Object... args) {
        ResourceBundle bundle = getBundle(language.getLocale());
        return MessageFormat.format(bundle.getString(key), args);
    }

    public static String getOrDefault(String key, Object... args) {
        ResourceBundle bundle = getBundle(getLocale());
        try {
            String s = bundle.getString(key);
            return MessageFormat.format(s, args);
        }
        catch (Exception ex) {
            return get(BaseLanguage.defaultLanguage(), key, args);
        }
    }

    public static String getOrDefault(BaseLanguage language, String key, Object... args) {
        ResourceBundle bundle = getBundle(language.getLocale());
        try {
            String s = bundle.getString(key);
            return MessageFormat.format(s, args);
        }
        catch (Exception ex) {
            return get(BaseLanguage.defaultLanguage(), key, args);
        }
    }

    public static String getOrDefault(String key, String def, Object... args) {
        ResourceBundle bundle = getBundle(getLocale());
        try {
            String s = bundle.getString(key);
            return MessageFormat.format(s, args);
        }
        catch (Exception ex) {
            return def;
        }
    }

    public static String getOrDefault(BaseLanguage language, String key, String def, Object... args) {
        ResourceBundle bundle = getBundle(language.getLocale());
        try {
            String s = bundle.getString(key);
            return MessageFormat.format(s, args);
        }
        catch (Exception ex) {
            return def;
        }
    }

    public static StringBinding getBinding(String key, Object... args) {
        return Bindings.createStringBinding(() -> getOrDefault(key, args), locale);
    }

    public static StringBinding getBinding(Callable<String> callable) {
        return Bindings.createStringBinding(callable, locale);
    }

    private static ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle(getBundleBaseName(), locale);
    }

    public static Locale getLocale() {
        return locale.get();
    }

    public static ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    public static void setLanguage(BaseLanguage language) {
        locale.set(language.getLocale());
    }

    public static BaseLanguage[] getSupportedLanguages() {
        return BaseLanguage.values();
    }

    public static String getBundleBaseName() {
        return "com/opencgl/i18n/base";
    }
}
