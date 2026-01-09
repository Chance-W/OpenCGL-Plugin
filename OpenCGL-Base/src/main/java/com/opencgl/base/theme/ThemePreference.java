package com.opencgl.base.theme;

import java.util.Locale;

/** Immutable theme preference with compatibility parsing for pre-2026 theme names. */
public record ThemePreference(ThemeMode mode, AccentColor accent) {

    public ThemePreference {
        mode = mode == null ? ThemeMode.LIGHT : mode;
        accent = accent == null ? AccentColor.TEAL : accent;
    }

    public static ThemePreference parse(String themeValue, String accentValue) {
        String value = normalize(themeValue);
        AccentColor configuredAccent = parseAccent(accentValue);
        return switch (value) {
            case "system" -> new ThemePreference(ThemeMode.SYSTEM, configuredAccent);
            case "dark" -> new ThemePreference(ThemeMode.DARK, configuredAccent);
            case "blue" -> new ThemePreference(ThemeMode.LIGHT, AccentColor.BLUE);
            case "purple" -> new ThemePreference(ThemeMode.LIGHT, AccentColor.PURPLE);
            case "ocean" -> new ThemePreference(ThemeMode.LIGHT, AccentColor.OCEAN);
            case "light", "default", "" -> new ThemePreference(ThemeMode.LIGHT, configuredAccent);
            default -> new ThemePreference(ThemeMode.LIGHT, AccentColor.TEAL);
        };
    }

    public ThemeMode resolvedMode(boolean systemDark) {
        if (mode != ThemeMode.SYSTEM) {
            return mode;
        }
        return systemDark ? ThemeMode.DARK : ThemeMode.LIGHT;
    }

    private static AccentColor parseAccent(String value) {
        try {
            return AccentColor.valueOf(normalize(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AccentColor.TEAL;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
