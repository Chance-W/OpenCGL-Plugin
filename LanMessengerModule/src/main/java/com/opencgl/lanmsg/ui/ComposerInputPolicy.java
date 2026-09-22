package com.opencgl.lanmsg.ui;

/** Keyboard policy for the multiline composer, kept independent from JavaFX controls for testing. */
public final class ComposerInputPolicy {
    private ComposerInputPolicy() {}

    public static boolean shouldSendOnEnter(boolean composing, boolean shiftDown, String text) {
        return !composing && !shiftDown && text != null && !text.isBlank();
    }
}
