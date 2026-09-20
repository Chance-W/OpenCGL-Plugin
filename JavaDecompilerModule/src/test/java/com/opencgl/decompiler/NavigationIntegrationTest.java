package com.opencgl.decompiler;

import com.opencgl.decompiler.controller.DecompilerController;
import com.opencgl.decompiler.i18n.I18N;
import com.opencgl.decompiler.model.ClassNode;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationIntegrationTest {
    @TempDir Path temp;
    private DecompilerController controller;
    private Stage stage;
    private CodeArea first, target;
    private int usage;

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!fx(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for navigation/UI state");
            Thread.sleep(30);
        }
    }
    private static TreeItem<ClassNode> find(TreeItem<ClassNode> root, String name) {
        if (root.getValue().getName().equals(name)) return root;
        for (var child : root.getChildren()) { var result = find(child, name); if (result != null) return result; }
        return null;
    }
    private static void go(CodeArea editor) {
        boolean mac = System.getProperty("os.name").toLowerCase().contains("mac");
        editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.B, false, !mac, false, mac));
    }
    private void mouseAtUsage(javafx.event.EventType<MouseEvent> type) {
        var bounds = first.getCharacterBoundsOnScreen(usage, usage+1).orElseThrow();
        double sx = bounds.getCenterX(), sy = bounds.getCenterY();
        var point = first.screenToLocal(sx, sy);
        assertEquals(usage, first.hit(point.getX(), point.getY()).getCharacterIndex().orElse(-1));
        boolean mac = System.getProperty("os.name").toLowerCase().contains("mac");
        first.fireEvent(new MouseEvent(type, point.getX(), point.getY(), sx, sy,
                type == MouseEvent.MOUSE_PRESSED ? MouseButton.PRIMARY : MouseButton.NONE, 1,
                false, !mac, false, mac, false, false, false, false, false, false,
                new PickResult(first, new javafx.geometry.Point3D(point.getX(), point.getY(), 0), 0)));
    }

    @Test void compiledJarNavigatesAcrossTabsScrollsAndReturnsWithoutDuplicateTab() throws Exception {
        Files.writeString(temp.resolve("A.java"), "public class A { public void call(B b) { b.run(1); } }");
        String padding = java.util.stream.IntStream.range(0, 120).mapToObj(i -> "public int pad"+i+";\n").collect(java.util.stream.Collectors.joining());
        Files.writeString(temp.resolve("B.java"), "public class B { " + padding + "public void run(String s){} public void run(int value){System.out.println(value);} }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-d", temp.toString(), temp.resolve("A.java").toString(), temp.resolve("B.java").toString()));
        Path jar = temp.resolve("navigation.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String name : new String[]{"A.class", "B.class"}) {
                out.putNextEntry(new JarEntry(name)); out.write(Files.readAllBytes(temp.resolve(name))); out.closeEntry();
            }
        }
        CountDownLatch startup = new CountDownLatch(1);
        try { Platform.startup(startup::countDown); } catch (IllegalStateException alreadyStarted) { startup.countDown(); }
        assertTrue(startup.await(10, TimeUnit.SECONDS));
        try {
            fx(() -> {
                Platform.setImplicitExit(false);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/decompiler/views/DecompilerView.fxml"), I18N.getBundle(I18N.getLocale()));
                StackPane root = loader.load(); controller = loader.getController();
                Scene scene = new Scene(root, 1200, 700);
                for (String css : new String[]{"MFXColors.css", "themes/ThemeTokens-dark.css", "themes/ThemeColors-dark.css", "themes/Accent-teal.css", "GlobalComponents.css"}) {
                    scene.getStylesheets().add(getClass().getResource("/com/opencgl/base/css/"+css).toExternalForm());
                }
                stage = new Stage(); stage.setTitle("OpenCGL 跳转自动测试（请勿操作）"); stage.setScene(scene); stage.show();
                var load = DecompilerController.class.getDeclaredMethod("loadFile", java.io.File.class); load.setAccessible(true); load.invoke(controller, jar.toFile());
                return null;
            });
            await(() -> controller.symbolIndexLabel.getText().startsWith("符号索引完成"));
            fx(() -> { controller.fileTreeView.getSelectionModel().select(find(controller.fileTreeView.getRoot(), "A.class")); return null; });
            await(() -> controller.codeArea != null && controller.codeArea.getText().contains("run(1)"));
            fx(() -> { first = controller.codeArea; usage = first.getText().indexOf("run(1)"); first.moveTo(usage); go(first); return null; });
            await(() -> controller.codeArea != first && controller.codeArea.getSelectedText().equals("run"));
            await(() -> controller.codeArea.estimatedScrollYProperty().getValue() > 0);
            fx(() -> {
                target = controller.codeArea;
                assertEquals(2, controller.codeTabPane.getTabs().size());
                assertTrue(target.getText().substring(target.getSelection().getStart()).startsWith("run(int"));
                assertTrue(target.estimatedScrollYProperty().getValue() > 0, "target method must be scrolled into view");
                assertTrue(target.getCharacterBoundsOnScreen(target.getSelection().getStart(), target.getSelection().getEnd()).isPresent());
                controller.navigationBackButton.fire(); return null;
            });
            await(() -> controller.codeArea == first && first.getCaretPosition() == usage);
            fx(() -> { go(first); return null; });
            await(() -> controller.codeArea == target);
            fx(() -> {
                assertEquals(2, controller.codeTabPane.getTabs().size(), "reuse existing class tab");
                controller.navigationBackButton.fire(); return null;
            });
            await(() -> controller.codeArea == first);
            fx(() -> { controller.navigationForwardButton.fire(); return null; });
            await(() -> controller.codeArea == target);
            fx(() -> { controller.navigationBackButton.fire(); return null; });
            await(() -> controller.codeArea == first);
            fx(() -> { first.moveTo(0); mouseAtUsage(MouseEvent.MOUSE_MOVED); return null; });
            await(() -> first.getCursor() == javafx.scene.Cursor.HAND);
            fx(() -> {
                assertTrue(first.getStyleOfChar(usage).contains("symbol-link"));
                mouseAtUsage(MouseEvent.MOUSE_PRESSED); return null;
            });
            await(() -> controller.codeArea == target);
            fx(() -> {
                assertEquals("run", target.getSelectedText(), "click must use mouse location rather than old caret");
                assertFalse(first.getStyleOfChar(usage).contains("symbol-link"), "hover styling must be restored");
                return null;
            });
        } finally {
            fx(() -> { if (controller != null) controller.dispose(); if (stage != null) stage.close(); return null; });
        }
    }
}
