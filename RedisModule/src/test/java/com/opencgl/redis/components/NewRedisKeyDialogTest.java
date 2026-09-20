package com.opencgl.redis.components;

import com.opencgl.redis.model.RedisKeyInfo.KeyType;
import com.opencgl.redis.service.RedisConnectionManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NewRedisKeyDialogTest {
    @Test void editorsGrowIntoSpaceAboveButtons() throws Exception {
        fx(() -> {
            for (var type : java.util.List.of(KeyType.STRING, KeyType.LIST, KeyType.HASH, KeyType.STREAM)) {
                var d = new NewRedisKeyDialog(new RedisConnectionManager(), Runnable::run, "key");
                var pane = d.getDialogPane(); theme(pane);
                var combo = (ComboBox<KeyType>)pane.lookup("#new-key-type");
                combo.setValue(type);
                pane.setPrefHeight(700); pane.applyCss(); pane.resize(680, 700); pane.layout();
                var editor = (javafx.scene.layout.Region)pane.lookup(type == KeyType.STRING
                        ? "#new-key-string" : type == KeyType.LIST ? "#new-key-list" : "#new-key-table");
                double initialHeight = editor.getHeight();
                assertTrue(initialHeight >= 160, "Editor must fit three rows and a header: " + type + " height=" + initialHeight);
                pane.setPrefHeight(820); pane.resize(680, 820); pane.layout();
                assertEquals(120, editor.getHeight() - initialHeight, 1, "Unused height must go to editor: " + type);
                double bottom = editor.localToScene(editor.getBoundsInLocal()).getMaxY();
                var button = pane.lookup("#new-key-create");
                double gap = button.localToScene(button.getBoundsInLocal()).getMinY() - bottom;
                assertTrue(gap >= 0 && gap <= 50, "Unexpected blank space: " + gap);
            }
        });
    }
    @Test void realThemeAndEditingGeometry() throws Exception {
        fx(() -> {
            var d = new NewRedisKeyDialog(new RedisConnectionManager(), Runnable::run, "key");
            var pane = d.getDialogPane();
            String base = new java.io.File("../OpenCGL-Base/src/main/resources/com/opencgl/base/css/").toURI().toString();
            pane.getScene().getStylesheets().addAll(base + "themes/ThemeColors-dark.css", base + "GlobalComponents.css");
            ((ComboBox<KeyType>)pane.lookup("#new-key-type")).setValue(KeyType.LIST);
            pane.resize(680, 700); pane.applyCss(); pane.layout();
            var text = (javafx.scene.text.Text)pane.lookup("#new-key-name").lookup(".text");
            assertEquals(javafx.scene.paint.Color.color(1,1,1,.9), text.getFill());
            var list = (ListView<?>)pane.lookup("#new-key-list");
            var cell = list.lookupAll(".list-cell").stream().map(n -> (ListCell<?>)n).filter(c -> c.getIndex() == 0).findFirst().orElseThrow();
            double height = cell.getHeight(), width = cell.getWidth();
            list.edit(0); pane.applyCss(); pane.layout();
            assertEquals(height, cell.getHeight(), .1);
            assertEquals(width, cell.getWidth(), .1);
            ((TextArea)list.lookup(".key-cell-editor")).setText("value");
            list.edit(-1); pane.applyCss(); pane.layout();
            assertEquals(height, cell.getHeight(), .1);
            assertEquals(javafx.scene.paint.Color.color(1,1,1,.9), ((javafx.scene.text.Text)cell.lookup(".text")).getFill());
            ((ComboBox<KeyType>)pane.lookup("#new-key-type")).setValue(KeyType.HASH);
            pane.applyCss(); pane.layout();
            var table = (TableView)pane.lookup("#new-key-table");
            var row = table.lookupAll(".table-row-cell").stream().map(n -> (TableRow<?>)n).filter(r -> r.getIndex() == 0).findFirst().orElseThrow();
            double rowHeight = row.getHeight();
            table.edit(0, (TableColumn)table.getColumns().get(1)); pane.applyCss(); pane.layout();
            assertEquals(rowHeight, row.getHeight(), .1);
            table.edit(-1, null); pane.applyCss(); pane.layout();
            assertEquals(rowHeight, row.getHeight(), .1);
            pane.getScene().getStylesheets().setAll(base + "themes/ThemeColors-default.css", base + "GlobalComponents.css");
            pane.applyCss(); pane.layout();
            assertEquals(javafx.scene.paint.Color.web("#172033"), text.getFill());
        });
    }
    @Test void liveCellEditsReachRequestAndRenderedTextUsesTheme() throws Exception {
        fx(() -> {
            var d = new NewRedisKeyDialog(new RedisConnectionManager(), Runnable::run, "k");
            var pane = d.getDialogPane(); theme(pane);
            var combo = (ComboBox<KeyType>)pane.lookup("#new-key-type");
            combo.setValue(KeyType.LIST);
            pane.resize(680, 700); pane.applyCss(); pane.layout();
            var keyText = (javafx.scene.text.Text)pane.lookup("#new-key-name").lookup(".text");
            assertEquals(javafx.scene.paint.Color.web("#eeeeee"), keyText.getFill());
            var list = (ListView<?>)pane.lookup("#new-key-list");
            list.edit(0); pane.applyCss(); pane.layout();
            var edit = (TextArea)list.lookup(".key-cell-editor");
            assertNotNull(edit);
            edit.setText("value\nsecond line");
            pane.applyCss();
            assertEquals(java.util.List.of("RPUSH", "0", "value\nsecond line"), d.request().arguments());
            assertEquals(javafx.scene.paint.Color.web("#eeeeee"), ((javafx.scene.text.Text)edit.lookup(".text")).getFill());
            combo.setValue(KeyType.HASH); pane.applyCss(); pane.layout();
            var table = (TableView)pane.lookup("#new-key-table");
            table.edit(0, (TableColumn)table.getColumns().get(1)); pane.applyCss(); pane.layout();
            var cellEditor = (TextArea)table.lookup(".key-cell-editor");
            assertNotNull(cellEditor); cellEditor.setText("field value");
            assertEquals(java.util.List.of("HSET", "0", "value\nsecond line", "field value"), d.request().arguments());
        });
    }
    @Test void dialogChromeAndEditorsFollowTheme() throws Exception {
        fx(() -> {
            var d = new NewRedisKeyDialog(new RedisConnectionManager(), Runnable::run, "");
            var pane = d.getDialogPane();
            assertEquals(javafx.stage.StageStyle.UNDECORATED, ((javafx.stage.Stage)pane.getScene().getWindow()).getStyle());
            for (String color : java.util.List.of("#222222", "#eeeeee")) {
                pane.setStyle("-theme-bg-primary: " + color + "; -theme-bg-secondary: " + color + "; -theme-border: #777777; -theme-text-primary: #888888; -theme-text-secondary: #999999; -theme-accent: #009688; -theme-accent-hover: #008577; -theme-text-inverse: white;");
                ((ComboBox<KeyType>)pane.lookup("#new-key-type")).setValue(KeyType.HASH);
                pane.applyCss(); pane.layout();
                var content = (javafx.scene.layout.Region)pane.lookup("#new-key-table");
                assertEquals(javafx.scene.paint.Color.web(color), content.getBackground().getFills().get(0).getFill());
                var create = (Button)pane.lookup("#new-key-create");
                assertEquals(javafx.scene.paint.Color.web("#009688"), create.getBackground().getFills().get(0).getFill());
            }
        });
    }
    @BeforeAll static void start() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        assertTrue(ready.await(15, TimeUnit.SECONDS));
    }
    static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null); Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }
    static void theme(javafx.scene.Parent root) {
        root.setStyle("-theme-bg-primary: #222222; -theme-bg-secondary: #333333; -theme-border: #555555; -theme-text-primary: #eeeeee; -theme-text-secondary: #aaaaaa; -theme-text-inverse: white; -theme-accent: #009688; -theme-secondary: #555555; -theme-success: #008800; -theme-danger: #bb0000; -theme-info: #0088bb;");
    }
    @Test void switchesInputsAndPreservesMultilineValues() throws Exception {
        fx(() -> {
            var d = new NewRedisKeyDialog(new RedisConnectionManager(), Runnable::run, "prefix:");
            var pane = d.getDialogPane();
            theme(pane);
            pane.applyCss();
            assertEquals("prefix:", ((TextField)pane.lookup("#new-key-name")).getText());
            var combo = (ComboBox<KeyType>) pane.lookup("#new-key-type");
            assertEquals(6, combo.getItems().size());
            assertTrue(pane.lookup("#new-key-string").isVisible());
            combo.setValue(KeyType.HASH);
            pane.applyCss();
            assertFalse(pane.lookup("#new-key-string").isManaged());
            ((TextField)pane.lookup("#new-key-name")).setText("k");
            assertInstanceOf(TableView.class, pane.lookup("#new-key-table"));
            assertTrue(pane.lookup("#new-key-table").isManaged());
            combo.setValue(KeyType.LIST);
            assertFalse(pane.lookup("#new-key-table").isManaged());
            var list = (ListView<?>)pane.lookup("#new-key-list");
            assertTrue(list.isManaged());
            int initial = list.getItems().size();
            ((Button)pane.lookup("#new-key-add")).fire();
            assertEquals(initial + 1, list.getItems().size());
            list.getSelectionModel().clearAndSelect(0);
            ((Button)pane.lookup("#new-key-remove")).fire();
            assertEquals(initial, list.getItems().size());
            combo.setValue(KeyType.STREAM);
            assertTrue(pane.lookup("#new-key-stream-id").isManaged());
        });
    }
    @Test void browserOffersCreateMenuEvenWhenTreeIsEmpty() throws Exception {
        fx(() -> {
            var browser = new RedisKeyBrowser(new RedisConnectionManager());
            new javafx.scene.Scene(browser);
            theme(browser);
            browser.applyCss();
            var tree = (TreeView<?>)browser.lookup(".tree-view");
            assertNotNull(tree.getContextMenu());
            assertFalse(tree.getContextMenu().getItems().isEmpty());
            browser.dispose();
        });
    }
    @Test void duplicateSubmissionKeepsInputAndReenablesCreate() throws Exception {
        var ref = new java.util.concurrent.atomic.AtomicReference<NewRedisKeyDialog>();
        var work = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        fx(() -> {
            var manager = new RedisConnectionManager() {
                @Override public boolean createKey(com.opencgl.redis.model.NewRedisKey request) { return false; }
            };
            var dialog = new NewRedisKeyDialog(manager, work::set, "my:key"); ref.set(dialog);
            var pane = dialog.getDialogPane(); theme(pane); pane.applyCss();
            ((TextArea)pane.lookup("#new-key-string")).setText("value to retain");
            var create = (Button)pane.lookup("#new-key-create"); create.fire();
            assertTrue(create.isDisabled());
            assertNotNull(work.get());
        });
        work.get().run();
        fx(() -> {
            var pane = ref.get().getDialogPane();
            assertFalse(pane.lookup("#new-key-create").isDisabled());
            assertEquals("value to retain", ((TextArea)pane.lookup("#new-key-string")).getText());
            assertNull(ref.get().getResult());
        });
    }
}
