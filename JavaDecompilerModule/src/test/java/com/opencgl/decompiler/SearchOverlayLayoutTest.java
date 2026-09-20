package com.opencgl.decompiler;

import com.opencgl.decompiler.controller.DecompilerController;
import com.opencgl.decompiler.i18n.I18N;
import com.opencgl.decompiler.model.ClassNode;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SearchOverlayLayoutTest {
    @Test
    void searchStaysInsideActiveEditorWhenResizingAndSwitchingTabs() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            DecompilerController controller = null;
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/decompiler/views/DecompilerView.fxml"), I18N.getBundle(I18N.getLocale()));
                StackPane root = loader.load();
                controller = loader.getController();
                Scene scene = new Scene(root, 1200, 700);
                for (String stylesheet : new String[]{"MFXColors.css", "themes/ThemeTokens-dark.css", "themes/ThemeColors-dark.css", "themes/Accent-teal.css", "GlobalComponents.css"}) {
                    scene.getStylesheets().add(getClass().getResource("/com/opencgl/base/css/" + stylesheet).toExternalForm());
                }
                var create = DecompilerController.class.getDeclaredMethod("createEditor", ClassNode.class);
                create.setAccessible(true);
                create.invoke(controller, new ClassNode("A.class", "A.class", true, false));
                create.invoke(controller, new ClassNode("B.class", "B.class", true, false));
                for (int index : new int[]{0, 1, 0}) {
                    controller.codeTabPane.getSelectionModel().select(index);
                    for (int width : new int[]{1200, 900, 1500}) {
                        root.resize(width, 700);
                        root.applyCss();
                        root.layout();
                        assertSame(controller.codeTabPane.getSelectionModel().getSelectedItem().getContent(), controller.codeSearchBar.getParent());
                        var search = controller.codeSearchBar.localToScene(controller.codeSearchBar.getBoundsInLocal());
                        var editor = controller.codeArea.localToScene(controller.codeArea.getBoundsInLocal());
                        assertTrue(search.getMinY() >= editor.getMinY() + 7);
                        assertTrue(search.getMaxX() <= editor.getMaxX());
                        assertTrue(search.getMinX() >= editor.getMinX());
                    }
                }
                controller.codeTabPane.getTabs().clear();
                assertNull(controller.codeArea);
                assertNull(controller.codeSearchBar.getParent());
                controller.symbolIndexProgress.setVisible(true);
                controller.symbolIndexProgress.setManaged(true);
                for (double progress : new double[]{0.5, 1.0}) {
                    controller.symbolIndexProgress.setProgress(progress);
                    for (String accent : new String[]{"#a13bf0", "#0b99a2"}) {
                        root.setStyle("-theme-accent: " + accent + ";");
                        root.applyCss();
                        root.layout();
                        var bar = (javafx.scene.layout.Region) controller.symbolIndexProgress.lookup(".bar");
                        assertEquals(javafx.scene.paint.Color.web(accent), bar.getBackground().getFills().get(0).getFill());
                    }
                }
            } catch (Throwable ex) { failure.set(ex); }
            finally {
                if (controller != null) controller.dispose();
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
