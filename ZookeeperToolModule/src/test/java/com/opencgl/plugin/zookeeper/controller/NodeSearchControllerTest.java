package com.opencgl.plugin.zookeeper.controller;

import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NodeSearchControllerTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @BeforeAll static void startFx() throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }
    static void fx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(action, null); Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }

    @Test void matchedParentCanExpandUnloadedChildrenAndGrandchildren() throws Exception {
        var ref = new AtomicReference<Fixture>();
        var parentRef = new AtomicReference<TreeItem<String>>();
        var childRef = new AtomicReference<TreeItem<String>>();
        var loaded = new CountDownLatch(1); var grandchildLoaded = new CountDownLatch(1);
        fx(() -> {
            var f = new Fixture(); ref.set(f);
            f.reader.set(path -> {
                assertFalse(Platform.isFxApplicationThread());
                return switch (path) {
                    case "/apps/Order" -> List.of("worker");
                    case "/apps/Order/worker" -> List.of("config");
                    default -> List.of();
                };
            });
            f.input.setText("Order"); f.input.fireEvent(new javafx.event.ActionEvent());
            var parent = f.tree.getRoot().getChildren().get(0).getChildren().get(0); parentRef.set(parent);
            assertFalse(parent.isLeaf(), "Search parent must retain a lazy-expand affordance");
            parent.getChildren().addListener((javafx.collections.ListChangeListener<TreeItem<String>>)c -> {
                if (parent.getChildren().stream().anyMatch(n -> n.getValue().equals("worker"))) loaded.countDown();
            });
            parent.setExpanded(true);
        });
        try {
            assertTrue(loaded.await(5, TimeUnit.SECONDS));
            fx(() -> {
                var child = parentRef.get().getChildren().get(0); childRef.set(child);
                assertEquals("/apps/Order/worker", NodeSearchTree.path(child));
                child.getChildren().addListener((javafx.collections.ListChangeListener<TreeItem<String>>)c -> {
                    if (child.getChildren().stream().anyMatch(n -> n.getValue().equals("config"))) grandchildLoaded.countDown();
                });
                child.setExpanded(true);
            });
            assertTrue(grandchildLoaded.await(5, TimeUnit.SECONDS));
            fx(() -> {
                var f = ref.get(); var config = childRef.get().getChildren().get(0);
                f.tree.getSelectionModel().select(config);
                assertEquals("/apps/Order/worker/config", f.opened.get());
                f.clear.fire(); assertSame(f.original, f.tree.getRoot());
            });
        } finally { fx(() -> ref.get().controller.close()); }
    }

    @Test void localSearchAndClearPreserveTreeAndSelectionAndOpenResultByPath() throws Exception {
        fx(() -> {
            var f = new Fixture();
            try {
                boolean previouslyExpanded = f.original.getChildren().get(0).isExpanded();
                f.input.setText("ORDER"); f.input.fireEvent(new javafx.event.ActionEvent());
                var result = f.tree.getRoot().getChildren().get(0).getChildren().get(0);
                assertEquals("Order", result.getValue());
                f.tree.getSelectionModel().select(result);
                assertEquals("/apps/Order", f.opened.get());
                f.clear.fire();
                assertSame(f.original, f.tree.getRoot());
                assertSame(f.selected, f.tree.getSelectionModel().getSelectedItem());
                assertEquals(previouslyExpanded, f.original.getChildren().get(0).isExpanded());
                f.input.setText("does-not-exist"); f.input.fireEvent(new javafx.event.ActionEvent());
                assertTrue(f.tree.getRoot().getChildren().isEmpty());
            } finally { f.controller.close(); }
        });
    }

    @Test void expandingPartialParentMergesAllChildrenAndDoesNotReloadCompletedBranch() throws Exception {
        var ref = new AtomicReference<Fixture>(); var parent = new AtomicReference<TreeItem<String>>();
        var oldChild = new AtomicReference<TreeItem<String>>();
        var calls = new java.util.concurrent.atomic.AtomicInteger(); var done = new CountDownLatch(1);
        fx(() -> {
            var f = new Fixture(); ref.set(f);
            f.reader.set(path -> { assertEquals("/apps", path); calls.incrementAndGet(); return List.of("Order", "Payment", "New"); });
            f.input.setText("apps"); f.input.fireEvent(new javafx.event.ActionEvent());
            var apps = f.tree.getRoot().getChildren().get(0); parent.set(apps); oldChild.set(apps.getChildren().get(0));
            apps.getChildren().addListener((javafx.collections.ListChangeListener<TreeItem<String>>)c -> {
                if (apps.getChildren().size() == 3) done.countDown();
            });
            apps.setExpanded(false); apps.setExpanded(true);
        });
        try {
            assertTrue(done.await(5, TimeUnit.SECONDS));
            fx(() -> {
                assertEquals(List.of("Order", "Payment", "New"), parent.get().getChildren().stream().map(TreeItem::getValue).toList());
                assertSame(oldChild.get(), parent.get().getChildren().get(0));
                parent.get().setExpanded(false); parent.get().setExpanded(true);
                assertEquals(1, calls.get());
            });
        } finally { fx(() -> ref.get().controller.close()); }
    }

    @Test void clearingSearchCancelsInFlightExpansion() throws Exception {
        var ref = new AtomicReference<Fixture>();
        var entered = new CountDownLatch(1); var interrupted = new CountDownLatch(1);
        fx(() -> {
            var f = new Fixture(); ref.set(f);
            f.reader.set(path -> {
                entered.countDown();
                try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { interrupted.countDown(); }
                return List.of("late-child");
            });
            f.input.setText("Order"); f.input.fireEvent(new javafx.event.ActionEvent());
            f.tree.getRoot().getChildren().get(0).getChildren().get(0).setExpanded(true);
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            fx(() -> ref.get().clear.fire());
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            fx(() -> {
                assertSame(ref.get().original, ref.get().tree.getRoot());
                assertTrue(ref.get().selected.getChildren().isEmpty());
            });
        } finally { fx(() -> ref.get().controller.close()); }
    }

    @Test void actualPluginFxmlWiresSearchAndDisconnectedFeedback() throws Exception {
        fx(() -> {
            var controller = new ZookeeperToolController(directory.resolve("profiles.db"));
            try {
                var root = loadView(controller);
                new javafx.scene.Scene(root, 1000, 700);
                String styles = new java.io.File("../OpenCGL-Base/src/main/resources/com/opencgl/base/css/").toURI().toString();
                root.getStylesheets().addAll(styles + "themes/ThemeColors-dark.css", styles + "GlobalComponents.css");
                root.applyCss(); root.layout();
                var input = (TextField)root.lookup("#nodeSearchField");
                assertNotNull(input);
                input.setText("Order");
                root.applyCss(); root.layout();
                assertEquals(javafx.scene.paint.Color.color(1, 1, 1, .9),
                        enteredText(input).getFill());
                ((Button)root.lookup("#searchAllButton")).fire();
                assertEquals(com.opencgl.plugin.zookeeper.i18n.I18N.get("message.zk_not_connected"),
                        ((Label)root.lookup("#searchStatus")).getText());
                ((Button)root.lookup("#clearSearchButton")).fire();
                assertEquals("", input.getText());
                assertFalse(root.lookup("#searchProgress").isVisible());
                input.setText("Order");
                root.getStylesheets().setAll(styles + "themes/ThemeColors-default.css", styles + "GlobalComponents.css");
                root.applyCss(); root.layout();
                assertEquals(javafx.scene.paint.Color.web("#172033"),
                        enteredText(input).getFill());
            } finally { controller.dispose(); }
        });
    }

    static javafx.scene.Parent loadView(ZookeeperToolController controller) {
        try {
            var loader = new javafx.fxml.FXMLLoader(NodeSearchControllerTest.class.getResource("/ZookeeperTool.fxml"),
                    java.util.ResourceBundle.getBundle("com.opencgl.plugin.zookeeper.i18n.ZookeeperTool", java.util.Locale.SIMPLIFIED_CHINESE));
            loader.setControllerFactory(type -> controller);
            return loader.load();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    @Test void savedConnectionSelectionFillsFieldsAndSaveUpdatesTreeAndDatabase() throws Exception {
        var db = directory.resolve("profiles.db");
        var repository = new com.opencgl.plugin.zookeeper.service.ZkConnectionRepository(db);
        var first = repository.create(0L, true, "first");
        first.setServers("zk-a:2181,zk-b:2181"); first.setTimeoutMs(8000); repository.save(first);
        fx(() -> {
            var controller = new ZookeeperToolController(db);
            var stage = new javafx.stage.Stage();
            try {
                var root = loadView(controller); stage.setScene(new javafx.scene.Scene(root, 1300, 800)); stage.show(); root.applyCss(); root.layout();
                var pane = controller.getConnectionsPane();
                assertEquals(1, pane.getTabPane().getTabs().size());
                var tree = pane.getTree();
                tree.getSelectionModel().select(tree.getRoot().getChildren().get(0));
                assertEquals("zk-a:2181,zk-b:2181", controller.getZkServersTextField().getText());
                assertEquals(8000, controller.getConnectionTimeoutSpinner().getValue());
                controller.getConnectionNameField().setText("renamed");
                controller.getZkServersTextField().setText("zk-c:2181");
                controller.getSaveConnectionButton().fire();
                assertEquals("renamed", pane.getTree().getSelectionModel().getSelectedItem().getValue().getName());
                assertEquals("zk-c:2181", new com.opencgl.plugin.zookeeper.service.ZkConnectionRepository(db).load().get(0).getServers());
                assertNotNull(root.lookup("#nodeSearchField"));
            } finally { controller.dispose(); stage.close(); }
        });
    }

    @Test void changingConnectionFieldsClearsOldServerNodes() throws Exception {
        fx(() -> {
            var controller = new ZookeeperToolController(directory.resolve("switch.db"));
            try {
                loadView(controller);
                controller.getNodeTreeView().getRoot().getChildren().add(new TreeItem<>("old-server-node"));
                controller.getNodeDataValueTextArea().setText("old-server-data");
                controller.getZkServersTextField().setText("different:2181");
                assertTrue(controller.getNodeTreeView().getRoot().getChildren().isEmpty());
                assertEquals("", controller.getNodeDataValueTextArea().getText());
            } finally { controller.dispose(); }
        });
    }

    @Test void doubleClickConnectsTheClickedProfileButSingleSelectionDoesNot() throws Exception {
        var db = directory.resolve("doubleclick.db");
        var repository = new com.opencgl.plugin.zookeeper.service.ZkConnectionRepository(db);
        var connection = repository.create(0L, true, "test cluster");
        connection.setServers("test-a:2181,test-b:2181"); repository.save(connection);
        fx(() -> {
            var controller = new ZookeeperToolController(db);
            var requestedServers = new ArrayList<String>();
            // Replace only the external network boundary; retain the real FXML, tree and handlers.
            controller.setZookeeperToolService(new com.opencgl.plugin.zookeeper.service.ZookeeperToolService(controller) {
                @Override public void connectOnAction() { requestedServers.add(controller.getZkServersTextField().getText()); }
            });
            try {
                var root = loadView(controller); new javafx.scene.Scene(root, 1300, 800); root.applyCss(); root.layout();
                var tree = controller.getConnectionsPane().getTree();
                tree.getSelectionModel().select(tree.getRoot().getChildren().get(0));
                assertTrue(requestedServers.isEmpty());
                var cell = tree.lookupAll(".tree-cell").stream().filter(n -> n instanceof TreeCell<?> c
                        && c.getItem() instanceof com.opencgl.plugin.zookeeper.service.ZkConnection item
                        && item.getId().equals(connection.getId())).findFirst().orElseThrow();
                cell.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                        5, 5, 5, 5, javafx.scene.input.MouseButton.PRIMARY, 2,
                        false, false, false, false, false, false, false, false, false, true, null));
                assertEquals(List.of("test-a:2181,test-b:2181"), requestedServers);
            } finally { controller.dispose(); }
        });
    }

    private static javafx.scene.text.Text enteredText(TextField input) {
        return input.lookupAll(".text").stream().filter(n -> n instanceof javafx.scene.text.Text t
                && t.getText().equals("Order")).map(n -> (javafx.scene.text.Text)n).findFirst().orElseThrow();
    }

    @Test void cancelledBackgroundSearchCannotReplaceRestoredTree() throws Exception {
        var entered = new CountDownLatch(1); var released = new CountDownLatch(1);
        var ref = new AtomicReference<Fixture>();
        fx(() -> {
            var f = new Fixture(); ref.set(f);
            f.reader.set(path -> {
                assertFalse(Platform.isFxApplicationThread()); entered.countDown();
                try { released.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                return path.equals("/") ? List.of("Order") : List.of();
            });
            f.input.setText("Order"); f.all.fire();
            assertTrue(f.progress.isVisible()); assertFalse(f.cancel.isDisabled());
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        fx(() -> { ref.get().cancel.fire(); ref.get().clear.fire(); });
        released.countDown();
        fx(() -> {
            var f = ref.get();
            assertSame(f.original, f.tree.getRoot()); assertFalse(f.progress.isVisible());
            f.controller.close();
        });
    }

    @Test void fullSearchFindsUnloadedNodesAndSelectionOpensTheirFullPath() throws Exception {
        var done = new CountDownLatch(1); var ref = new AtomicReference<Fixture>();
        fx(() -> {
            var f = new Fixture(); ref.set(f);
            f.reader.set(path -> path.equals("/") ? List.of("hidden")
                    : path.equals("/hidden") ? List.of("Order") : List.of());
            f.progress.visibleProperty().addListener((o, a, b) -> { if (!b) done.countDown(); });
            f.input.setText("Order"); f.all.fire();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        fx(() -> {
            var f = ref.get();
            try {
                var result = f.tree.getRoot().getChildren().get(0).getChildren().get(0);
                assertEquals("/hidden/Order", NodeSearchTree.path(result));
                f.tree.getSelectionModel().select(result);
                assertEquals("/hidden/Order", f.opened.get());
                f.clear.fire(); assertSame(f.original, f.tree.getRoot());
            } finally { f.controller.close(); }
        });
    }

    static class Fixture {
        final TreeItem<String> original = new TreeItem<>("/");
        final TreeItem<String> selected = new TreeItem<>("Order");
        final TreeView<String> tree = new TreeView<>(original);
        final TextField input = new TextField();
        final Button all = new Button(), cancel = new Button(), clear = new Button();
        final Label status = new Label(); final ProgressIndicator progress = new ProgressIndicator();
        final AtomicReference<com.opencgl.plugin.zookeeper.service.NodeSearch.ChildrenReader> reader = new AtomicReference<>();
        final AtomicReference<String> opened = new AtomicReference<>();
        final NodeSearchController controller;
        Fixture() {
            var app = new TreeItem<>("apps"); app.getChildren().addAll(selected, new TreeItem<>("Payment"));
            original.getChildren().add(app); original.setExpanded(true);
            tree.getSelectionModel().select(selected);
            controller = new NodeSearchController(tree, input, all, cancel, clear, status, progress,
                    reader::get, item -> opened.set(NodeSearchTree.path(item)));
        }
    }
}
