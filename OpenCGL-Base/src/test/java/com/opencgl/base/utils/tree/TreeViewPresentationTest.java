package com.opencgl.base.utils.tree;

import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreeViewPresentationTest {
    @Test void selectionUsesThemeWithoutChangingCellGeometry() throws Exception {
        TreeViewBuilderTest.fx(() -> {
            var root = new TreeItem<>("root"); root.setExpanded(true);
            root.getChildren().add(new TreeItem<>("long child name"));
            var tree = new TreeView<>(root); TreeViewPresentation.install(tree);
            var pane = new javafx.scene.layout.StackPane(tree);
            var scene = new javafx.scene.Scene(pane, 400, 250);
            String base = "/com/opencgl/base/css/";
            for (String theme : new String[]{"default", "dark"}) {
                scene.getStylesheets().setAll(
                        getClass().getResource(base + "themes/ThemeColors-" + theme + ".css").toExternalForm(),
                        getClass().getResource(base + "GlobalComponents.css").toExternalForm());
                pane.resize(400, 250); pane.applyCss(); pane.layout();
                TreeCell<?> cell = tree.lookupAll(".tree-cell").stream().map(n -> (TreeCell<?>)n)
                        .filter(c -> c.getIndex() == 1).findFirst().orElseThrow();
                double height = cell.getHeight(), width = cell.getWidth();
                tree.getSelectionModel().select(1); pane.applyCss(); pane.layout();
                assertEquals(height, cell.getHeight(), .1);
                assertEquals(width, cell.getWidth(), .1);
                // Custom accent must also work; no hardcoded teal in shared presentation.
                pane.setStyle("-theme-accent: #9c27b0; -theme-text-inverse: white;");
                tree.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
                pane.applyCss(); pane.layout();
                assertEquals(javafx.scene.paint.Color.web("#9c27b0"), cell.getBackground().getFills().getFirst().getFill());
                assertEquals(javafx.scene.paint.Color.WHITE, cell.getTextFill());
                tree.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), false);
                tree.getSelectionModel().clearSelection();
            }
        });
    }
    @BeforeAll static void start() throws Exception { TreeViewBuilderTest.toolkit(); }
    @Test void preservesFactoriesInstalledBeforeAndAfterSharedPresentation() throws Exception {
        TreeViewBuilderTest.fx(() -> {
            var tree = new TreeView<>(new TreeItem<>("root"));
            tree.setCellFactory(t -> new BusinessCell());
            TreeViewPresentation.install(tree);
            TreeViewPresentation.install(tree);
            assertInstanceOf(BusinessCell.class, tree.getCellFactory().call(tree));
            tree.setCellFactory(t -> new BusinessCell());
            TreeCell<String> cell = tree.getCellFactory().call(tree);
            cell.updateTreeView(tree); cell.updateIndex(0);
            assertEquals("special root", cell.getText());
            assertEquals("business", cell.getTooltip().getText());
            assertEquals(1, tree.getStylesheets().size());
        });
    }
    @Test void contextMenuTargetsClickedCellWithoutLosingCustomMenu() throws Exception {
        TreeViewBuilderTest.fx(() -> {
            var root = new TreeItem<>("root"); root.getChildren().add(new TreeItem<>("child")); root.setExpanded(true);
            var tree = new TreeView<>(root); tree.setCellFactory(t -> new BusinessCell());
            TreeViewPresentation.install(tree);
            tree.getSelectionModel().select(root);
            var cell = tree.getCellFactory().call(tree); cell.updateTreeView(tree); cell.updateIndex(1);
            cell.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 0, 0, 0, 0, false, null));
            assertEquals("child", tree.getSelectionModel().getSelectedItem().getValue());
            assertEquals("custom", cell.getContextMenu().getItems().getFirst().getText());
        });
    }
    static class BusinessCell extends TreeCell<String> {
        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : "special " + item);
            setTooltip(empty ? null : new Tooltip("business"));
            setContextMenu(empty ? null : new ContextMenu(new MenuItem("custom")));
        }
    }
}
