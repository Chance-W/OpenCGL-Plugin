package com.opencgl.base.theme;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SystemThemeDetectorTest {

    @Test
    void recognizesMacDarkAppearanceOutput() {
        assertTrue(SystemThemeDetector.isDarkAppearance("Dark"));
        assertTrue(SystemThemeDetector.isDarkAppearance("  DARK\n"));
    }

    @Test
    void treatsMissingOrLightAppearanceAsLight() {
        assertFalse(SystemThemeDetector.isDarkAppearance(null));
        assertFalse(SystemThemeDetector.isDarkAppearance("Light"));
    }

    @Test
    void closeStopsTheBackgroundDetector() {
        SystemThemeDetector detector = new SystemThemeDetector();

        detector.close();

        assertTrue(detector.isStopped());
    }
}
