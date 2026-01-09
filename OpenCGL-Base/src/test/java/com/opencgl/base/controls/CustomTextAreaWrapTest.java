package com.opencgl.base.controls;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomTextAreaWrapTest {
    @BeforeAll
    static void startToolkit() throws Exception {
        if (!Boolean.getBoolean("opencgl.run.fx.tests")) {
            return;
        }
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @AfterAll
    static void keepToolkitAvailableForOtherTests() {
        // JavaFX toolkit is process-wide; intentionally do not call Platform.exit().
    }

    @Test
    void togglingWrapAndResizingKeepsLongParagraphWrapped() throws Exception {
        assumeTrue(Boolean.getBoolean("opencgl.run.fx.tests"),
                "Graphical JavaFX test disabled; run with -Dopencgl.run.fx.tests=true");
        runOnFxThread(() -> {
            CustomTextArea area = new CustomTextArea();
            StackPane root = new StackPane(new VirtualizedScrollPane<>(area));
            new Scene(root, 240, 180);
            area.replaceText("0123456789".repeat(80));

            layout(root, 240, 180);
            ToggleButton wrapButton = (ToggleButton) area.lookup(".wrap-text-button");
            assertTrue(!wrapButton.isManaged(), "overlay controls must not participate in RichTextFX content layout");
            wrapButton.fire();
            layout(root, 240, 180);
            int linesAfterToggle = area.getParagraphLinesCount(0);

            layout(root, 160, 180);
            int linesAfterResize = area.getParagraphLinesCount(0);

            assertTrue(linesAfterToggle > 1, "enabling wrap must remeasure an already rendered paragraph");
            assertTrue(linesAfterResize >= linesAfterToggle,
                    "shrinking the viewport must preserve wrapping and may only increase its line count");
        });
    }

    private static void layout(StackPane root, double width, double height) {
        root.resize(width, height);
        root.applyCss();
        root.layout();
    }

    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                finished.countDown();
            }
        });
        assertTrue(finished.await(10, TimeUnit.SECONDS), "JavaFX test timed out");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
