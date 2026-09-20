package com.opencgl.base.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void selectionContrastAndClosedComboSurviveModeSwitches() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TreeView<String> tree = new TreeView<>(new TreeItem<>("selected"));
                tree.getSelectionModel().select(0);
                javafx.scene.control.ComboBox<String> combo = new javafx.scene.control.ComboBox<>();
                combo.getItems().add("POST");
                combo.getSelectionModel().selectFirst();
                javafx.scene.control.Label badge = new javafx.scene.control.Label("version");
                badge.setStyle("-fx-background-color: -theme-sidebar-selected-bg; -fx-text-fill: -theme-sidebar-selected-text;");
                io.github.palexdev.materialfx.controls.MFXComboBox<String> mfx = new io.github.palexdev.materialfx.controls.MFXComboBox<>();
                mfx.getItems().add("ERROR");
                mfx.selectIndex(0);
                io.github.palexdev.materialfx.controls.cell.MFXComboBoxCell<String> popupCell =
                    new io.github.palexdev.materialfx.controls.cell.MFXComboBoxCell<>(mfx, "ERROR");
                popupCell.updateItem("ERROR");
                javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(tree, combo, badge, popupCell);
                Scene scene = new Scene(root, 320, 300);
                for (String accent : new String[]{"teal", "blue", "purple", "ocean"}) {
                    for (String mode : new String[]{"dark", "default", "dark"}) {
                        scene.getStylesheets().setAll(resource("ThemeColors-" + mode + ".css"),
                            resource("Accent-" + accent + ".css"), resource("../GlobalComponents.css"));
                        root.applyCss();
                        root.layout();
                        TreeCell<?> selected = (TreeCell<?>) tree.lookup(".tree-cell:selected");
                        Color bg = (Color) selected.getBackground().getFills().get(0).getFill();
                        assertTrue(contrast(bg, (Color) selected.getTextFill()) >= 4.5,
                            mode + "/" + accent + " tree contrast: " + bg + " / " + selected.getTextFill());
                        Color badgeBg = (Color) badge.getBackground().getFills().get(0).getFill();
                        assertTrue(contrast(badgeBg, (Color) badge.getTextFill()) >= (mode.equals("dark") ? 4.5 : 3), mode + "/" + accent + " badge contrast");
                        javafx.scene.control.Label popupLabel = (javafx.scene.control.Label) popupCell.lookup(".data-label");
                        Color popupBg = (Color) popupCell.getBackground().getFills().get(0).getFill();
                        assertTrue(contrast(popupBg, (Color) popupLabel.getTextFill()) >= 4.5, mode + "/" + accent + " MFX popup contrast");
                        javafx.scene.control.ListCell<?> cell = (javafx.scene.control.ListCell<?>)
                            ((javafx.scene.control.skin.ComboBoxListViewSkin<?>) combo.getSkin()).getDisplayNode();
                        assertTrue(cell.getBackground() == null || cell.getBackground().getFills().stream()
                            .allMatch(fill -> fill.getFill() instanceof Color color && color.getOpacity() == 0),
                            "Closed combo must not inherit list selection background");
                    }
                }
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                finished.countDown();
            }
        });
        assertTrue(finished.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static double contrast(Color a, Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    private static double luminance(Color c) {
        return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue());
    }

    private static double linear(double c) {
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }

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
