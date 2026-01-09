package com.opencgl.base.theme;

import java.util.Locale;

/** Brand accent applied independently from the light/dark display mode. */
public enum AccentColor {
    TEAL,
    BLUE,
    PURPLE,
    OCEAN;

    public String cssName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
