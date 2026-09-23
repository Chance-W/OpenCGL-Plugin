package com.opencgl.http.ui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.*;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class HttpThemeIsolationTest {
    @BeforeAll static void start() throws Exception { HttpDebuggerLayoutTest.start(); }
    @Test void localPrimaryActionUsesCustomAccentWithoutStylingSiblingPlugin() throws Exception {
        HttpDebuggerLayoutTest.fx(() -> {
            var v = HttpDebuggerLayoutTest.load(Locale.ENGLISH);
            var sibling = new Button("Other plugin"); sibling.getStyleClass().add("primary-button");
            var host = new HBox(v.mainStackPane, sibling);
            var scene = new Scene(host, 1000, 650);
            host.applyCss();
            var siblingBackground = sibling.getBackground();
            for (String color : new String[]{"#9c27b0", "#008877"}) {
                host.setStyle("-theme-accent: " + color + "; -theme-text-inverse: white; -theme-text-secondary: #aaaaaa; -theme-border: #444444; -theme-bg-primary: #222222;");
                host.applyCss(); host.layout();
                assertEquals(Color.web(color), v.sendButton.getBackground().getFills().getFirst().getFill());
                assertEquals(Color.WHITE, v.sendButton.getTextFill());
                assertEquals(siblingBackground, sibling.getBackground());
                assertTrue(scene.getStylesheets().isEmpty(), "HTTP CSS must not be installed on host Scene");
            }
        });
    }
}
