package com.opencgl.http.ui;

import com.opencgl.http.views.HttpDebuggerView;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpDebuggerLayoutTest {
    @Test void environmentDialogLoadsBothLanguagesAndCopySelectsIndependentEnvironment() throws Exception {
        fx(() -> {
            assertTrue(System.getProperty("user.home").endsWith("test-home"));
            var service = new com.opencgl.http.service.EnvironmentService();
            String name = "dialog-copy-" + UUID.randomUUID();
            service.updateEnvironment(name, Map.of("中文", "值"));
            try {
                for (var locale : List.of(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)) {
                    var loader = new FXMLLoader(getClass().getResource("/com/opencgl/http/views/HttpDebuggerEnvConfigureView.fxml"),
                        ResourceBundle.getBundle("com.opencgl.http.i18n.HttpDebugger", locale));
                    var dialog = new com.opencgl.http.controller.HttpDebuggerEnvConfigureDialog();
                    loader.setController(dialog);
                    javafx.scene.Parent root;
                    try { root = loader.load(); } catch (Exception e) { throw new AssertionError(e); }
                    var stage = new javafx.stage.Stage();
                    stage.setScene(new Scene(root));
                    try {
                        dialog.init();
                        stage.show();
                        try {
                            var factory = dialog.getClass().getDeclaredMethod("exportConfirmation");
                            factory.setAccessible(true);
                            var confirmation = (Dialog<?>)factory.invoke(dialog);
                            assertSame(stage, confirmation.getOwner());
                            var resolveOwner = dialog.getClass().getDeclaredMethod("fileDialogOwner");
                            resolveOwner.setAccessible(true);
                            assertSame(stage, resolveOwner.invoke(dialog));
                        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                        root.applyCss(); root.layout();
                        var newButton = (Button)root.lookup("#newButton");
                        var deleteButton = (Button)root.lookup("#deleteButton");
                        var exportButton = (Button)root.lookup("#exportEnvButton");
                        var importButton = (Button)root.lookup("#importEnvButton");
                        var copyButton = (Button)root.lookup("#copyEnvButton");
                        assertEquals(copyButton.getWidth(), exportButton.getWidth(), 1);
                        assertEquals(copyButton.getWidth(), importButton.getWidth(), 1);
                        assertEquals(newButton.localToScene(0, 0).getX(), copyButton.localToScene(0, 0).getX(), 1);
                        assertEquals(deleteButton.localToScene(deleteButton.getWidth(), 0).getX(),
                            importButton.localToScene(importButton.getWidth(), 0).getX(), 1);
                        var list = (ListView<String>)root.lookup("#envList");
                        var copy = (Button)root.lookup("#copyEnvButton");
                        assertTrue(copy.isDisabled());
                        assertNotNull(root.lookup("#exportEnvButton"));
                        assertNotNull(root.lookup("#importEnvButton"));
                        list.getSelectionModel().select(name);
                        copy.fire();
                        String created = list.getSelectionModel().getSelectedItem();
                        assertNotEquals(name, created);
                        assertEquals(Map.of("中文", "值"), new com.opencgl.http.service.EnvironmentService().getEnvironment(created));
                        assertEquals(created, ((TextField)root.lookup("#envNameField")).getText());
                        assertNotNull(list.getContextMenu());
                        var table = (TableView<com.opencgl.http.model.KeyValueEntry>)root.lookup("#varTable");
                        table.scrollTo(0);
                        root.applyCss(); root.layout();
                        table.edit(0, table.getColumns().get(1));
                        root.applyCss(); root.layout();
                        var editor = (TextField)table.lookup(".text-field");
                        assertNotNull(editor);
                        editor.setText("切换前的新值");
                        list.getSelectionModel().select(name);
                        assertEquals("切换前的新值", service.getEnvironment(created).get("中文"));
                        assertEquals("值", service.getEnvironment(name).get("中文"));
                    } finally { stage.close(); }
                }
            } finally {
                for (String n : service.getEnvironmentNames()) if (n.startsWith(name)) service.deleteEnvironment(n);
            }
        });
    }
    @Test void toolbarActionsHaveIconsAndSaveIsLast() throws Exception {
        fx(() -> {
            var v = load(Locale.ENGLISH);
            var bar = (FlowPane)v.saveButton.getParent();
            assertSame(v.saveButton, bar.getChildren().getLast());
            for (var b : List.of(v.manageEnvButton, v.codeButton, v.proxyButton, v.importButton, v.saveButton)) {
                assertNotNull(b.getGraphic());
            }
        });
    }
    @BeforeAll static void start() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException running) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
    }
    static void fx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(action, null); Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
    }
    static HttpDebuggerView load(Locale locale) {
        try {
            var loader = new FXMLLoader(HttpDebuggerLayoutTest.class.getResource("/com/opencgl/http/views/HttpDebuggerView.fxml"),
                    ResourceBundle.getBundle("com.opencgl.http.i18n.HttpDebugger", locale));
            // Exercise actual FXML without opening the user's database or initializing network services.
            loader.setControllerFactory(type -> new HttpDebuggerView());
            loader.load();
            HttpDebuggerView view = loader.getController();
            view.emptyState.setManaged(false); view.emptyState.setVisible(false);
            view.requestPanel.setVisible(true); view.requestPanel.setManaged(true);
            return view;
        } catch (Exception e) { throw new AssertionError(e); }
    }
    @Test void compactLayoutKeepsSendAndAuxiliaryActionsReachableInBothLanguages() throws Exception {
        fx(() -> {
            for (var locale : List.of(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)) {
                var v = load(locale); var scene = new Scene(v.mainStackPane, 560, 500);
                scene.getStylesheets().add(HttpDebuggerLayoutTest.class.getResource(
                        "/com/opencgl/base/css/themes/ThemeColors-default.css").toExternalForm());
                v.urlField.setText("https://localhost/example/very/long/path?q=中文");
                v.mainStackPane.resize(560, 500); v.mainStackPane.applyCss(); v.mainStackPane.layout();
                assertTrue(v.urlField.getWidth() >= 180, "URL must retain usable width at compact size");
                assertTrue(v.saveButton.localToScene(v.saveButton.getBoundsInLocal()).getMinY()
                        >= v.sendButton.localToScene(v.sendButton.getBoundsInLocal()).getMaxY(), "Secondary actions belong below Send");
                for (Button button : List.of(v.sendButton, v.saveButton, v.proxyButton, v.importButton)) {
                    var bounds = button.localToScene(button.getBoundsInLocal());
                    assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= 560, "Clipped action: " + button.getText());
                }
                assertNotNull(v.envComboBox); assertNotNull(v.manageEnvButton); assertNotNull(v.codeButton);
                assertTrue(v.requestTabPane.getHeight() > 90);
                assertTrue(v.responseTabPane.getHeight() > 70);
            }
        });
    }
    @Test void authCanScrollAndHistoryIsReadOnlyWithVisibleRestoreAction() throws Exception {
        fx(() -> {
            var v = load(Locale.ENGLISH);
            var auth = v.requestTabPane.getTabs().get(3).getContent();
            assertInstanceOf(ScrollPane.class, auth);
            assertTrue(((ScrollPane)auth).isFitToWidth());
            assertFalse(v.historyFullTextArea.isEditable());
            assertNotNull(v.mainStackPane.lookup("#restoreHistoryButton"));
        });
    }
    @Test void sidebarRemainsUsableAndUserDividerChoiceSurvivesResize() throws Exception {
        fx(() -> {
            var c = HttpUiFixture.load();
            try {
                HttpUiFixture.set(c, "requestManager", new com.opencgl.base.view.RequestManagerView());
                HttpUiFixture.call(c, "setupWorkspaceLayout");
                var scene = new Scene(c.mainStackPane, 800, 500);
                scene.getStylesheets().add(getClass().getResource("/com/opencgl/base/css/themes/ThemeColors-default.css").toExternalForm());
                c.mainStackPane.resize(800, 500); c.mainStackPane.applyCss(); c.mainStackPane.layout();
                var split = (SplitPane)c.mainStackPane.lookup("#workspaceSplit");
                var sidebar = (Region)split.getItems().getFirst();
                assertTrue(sidebar.getWidth() >= 220 && sidebar.getWidth() <= 270);
                split.setDividerPositions(0.42); c.mainStackPane.layout();
                double chosen = sidebar.getWidth();
                c.mainStackPane.resize(1000, 650); c.mainStackPane.layout();
                assertTrue(sidebar.getWidth() >= chosen - 10, "Resize must not reset the user's divider to 260");
                split.setDividerPositions(0.01); c.mainStackPane.layout();
                assertTrue(sidebar.getWidth() >= 220);
            } finally { c.dispose(); }
        });
    }
}
