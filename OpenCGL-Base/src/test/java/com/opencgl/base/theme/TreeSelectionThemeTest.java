package com.opencgl.base.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TreeSelectionThemeTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        if (!started.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX toolkit did not start");
        }
    }

    @Test
    void blueAccentKeepsUnfocusedTreeSelectionClearlyVisible() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                TreeView<String> tree = new TreeView<>(new TreeItem<>("selected item"));
                tree.setShowRoot(true);
                tree.getSelectionModel().select(0);

                StackPane root = new StackPane(tree);
                Scene scene = new Scene(root, 320, 200);
                scene.getStylesheets().add(resource("ThemeColors-default.css"));
                scene.getStylesheets().add(resource("Accent-blue.css"));
                scene.getStylesheets().add(resource("../GlobalComponents.css"));
                root.applyCss();
                root.layout();

                @SuppressWarnings("unchecked")
                TreeCell<String> selected = (TreeCell<String>) tree.lookup(".tree-cell:selected");
                Color background = (Color) selected.getBackground().getFills().get(0).getFill();
                Color leftBorder = (Color) selected.getBorder().getStrokes().get(0).getLeftStroke();

                assertEquals(Color.web("#D6E4FF"), background,
                        "BLUE 强调色下，失焦树节点应保持清晰的蓝色选中背景");
                assertEquals(Color.web("#2563EB"), leftBorder,
                        "选中树节点左侧应显示当前强调色边线");
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                finished.countDown();
            }
        });

        if (!finished.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX assertion timed out");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static String resource(String relativePath) {
        return TreeSelectionThemeTest.class
                .getResource("/com/opencgl/base/css/themes/" + relativePath)
                .toExternalForm();
    }
}
