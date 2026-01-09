package com.opencgl.base.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThemePreferenceTest {

    @ParameterizedTest
    @CsvSource({
        "default,LIGHT,TEAL",
        "dark,DARK,TEAL",
        "blue,LIGHT,BLUE",
        "purple,LIGHT,PURPLE",
        "ocean,LIGHT,OCEAN",
        "unknown,LIGHT,TEAL"
    })
    void mapsLegacyThemeNames(String legacy, ThemeMode mode, AccentColor accent) {
        ThemePreference actual = ThemePreference.parse(legacy, null);

        assertEquals(mode, actual.mode());
        assertEquals(accent, actual.accent());
    }

    @ParameterizedTest
    @CsvSource({
        "SYSTEM,false,LIGHT",
        "SYSTEM,true,DARK",
        "LIGHT,true,LIGHT",
        "DARK,false,DARK"
    })
    void resolvesSystemMode(String mode, boolean systemDark, ThemeMode expected) {
        ThemePreference preference = new ThemePreference(ThemeMode.valueOf(mode), AccentColor.TEAL);

        assertEquals(expected, preference.resolvedMode(systemDark));
    }
}
