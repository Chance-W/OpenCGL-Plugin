package com.opencgl.plugin.zookeeper.i18n;

import java.util.Locale;

import org.apache.commons.lang.StringUtils;

import lombok.Getter;

@Getter
public enum Language {
//    ARABIC(Locale.forLanguageTag("ar")),
//    CZECH(Locale.forLanguageTag("cs-cz")),
    ENGLISH(Locale.ENGLISH),
//    FRENCH(Locale.FRENCH),
//    ITALIANO(Locale.ITALIAN),
//    RUSSIAN(Locale.forLanguageTag("ru")),
    SIMPLIFIED_CHINESE(Locale.SIMPLIFIED_CHINESE);
//    SPANISH(Locale.forLanguageTag("es")),
//    TRADITIONAL_CHINESE(Locale.TRADITIONAL_CHINESE);

    private final Locale locale;

    Language(Locale locale) {
        this.locale = locale;
    }

    public Locale getLocale() {
        return locale;
    }

    public static Language defaultLanguage() {
        String language = Locale.getDefault().getLanguage();
        if (StringUtils.isNotEmpty(language)) {
            try {
                for (Language value : Language.values()) {
                    if (value.getLocale().getLanguage().equals(Locale.getDefault().getLanguage())) {
                        return value;
                    }
                }
                return Language.ENGLISH;
            } catch (IllegalArgumentException e) {
                return Language.ENGLISH;
            }
        } else {
            return Language.ENGLISH;
        }
    }

    public static Language fromLocale(Locale locale) {
        if (locale == null)
            return ENGLISH;
        for (Language lang : values()) {
            if (lang.locale.getLanguage().equals(locale.getLanguage())) {
                return lang;
            }
        }
        return ENGLISH;
    }

}
