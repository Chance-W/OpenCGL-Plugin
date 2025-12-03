package com.opencgl.base.utils.i18n;

import java.util.Locale;

import org.apache.commons.lang.StringUtils;

public enum BaseLanguage {
    ARABIC(Locale.forLanguageTag("ar")),
    CZECH(Locale.forLanguageTag("cs-cz")),
    ENGLISH(Locale.ENGLISH),
    FRENCH(Locale.FRENCH),
    ITALIANO(Locale.ITALIAN),
    RUSSIAN(Locale.forLanguageTag("ru")),
    SIMPLIFIED_CHINESE(Locale.SIMPLIFIED_CHINESE),
    SPANISH(Locale.forLanguageTag("es")),
    TRADITIONAL_CHINESE(Locale.TRADITIONAL_CHINESE);

    private final Locale locale;

    BaseLanguage(Locale locale) {
        this.locale = locale;
    }

    public static BaseLanguage defaultLanguage() {
        String language = Locale.getDefault().getLanguage();
        if (StringUtils.isNotEmpty(language)) {
            try {
                for (BaseLanguage value : BaseLanguage.values()) {
                    if (value.getLocale().getLanguage().equals(Locale.getDefault().getLanguage())) {
                        return value;
                    }
                }
                return BaseLanguage.ENGLISH;
            }
            catch (IllegalArgumentException e) {
                return BaseLanguage.ENGLISH;
            }
        }
        else {
            return BaseLanguage.ENGLISH;
        }
    }


    public Locale getLocale() {
        return locale;
    }
}
