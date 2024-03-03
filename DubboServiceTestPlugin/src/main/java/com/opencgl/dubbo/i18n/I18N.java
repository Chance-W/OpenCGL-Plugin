package com.opencgl.dubbo.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

@SuppressWarnings("unused")
public class I18N {
    private static final ObjectProperty<Locale> locale = new SimpleObjectProperty<>();

    static {
        setLanguage(Language.defaultLanguage());
        locale.addListener(invalidated -> Locale.setDefault(getLocale()));
    }

    public static String get(String key, Object... args) {
        ResourceBundle bundle = getBundle(getLocale());
        return MessageFormat.format(bundle.getString(key), args);
    }

    public static String get(Language language, String key, Object... args) {
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
            return get(Language.defaultLanguage(), key, args);
        }
    }

    public static String getOrDefault(Language language, String key, Object... args) {
        ResourceBundle bundle = getBundle(language.getLocale());
        try {
            String s = bundle.getString(key);
            return MessageFormat.format(s, args);
        }
        catch (Exception ex) {
            return get(Language.defaultLanguage(), key, args);
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

    public static String getOrDefault(Language language, String key, String def, Object... args) {
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

    public static void setLanguage(Language language) {
        locale.set(language.getLocale());
    }

    public static Language[] getSupportedLanguages() {
        return Language.values();
    }

    public static String getBundleBaseName() {
        return "com/opencgl/dubbo/i18n/i18N";
    }
}
