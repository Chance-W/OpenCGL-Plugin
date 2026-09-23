package com.opencgl.base.utils.tree;

import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;

/** Shared presentation only: never changes node models, queries data or replaces business handlers. */
public final class TreeViewPresentation {
    private static final Object INSTALLED = new Object();
    private TreeViewPresentation() {}

    public static <T> void install(TreeView<T> tree) {
        if (tree.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) return;
        tree.getStyleClass().add("opencgl-tree");
        tree.getStylesheets().add(TreeViewPresentation.class
                .getResource("/com/opencgl/base/css/tree-view.css").toExternalForm());
        boolean[] wrapping = {false};
        tree.cellFactoryProperty().addListener((o, old, factory) -> {
            if (wrapping[0]) return;
            wrapping[0] = true;
            try { tree.setCellFactory(decorate(factory)); }
            finally { wrapping[0] = false; }
        });
        wrapping[0] = true;
        try { tree.setCellFactory(decorate(tree.getCellFactory())); }
        finally { wrapping[0] = false; }
    }

    private static <T> Callback<TreeView<T>, TreeCell<T>> decorate(Callback<TreeView<T>, TreeCell<T>> factory) {
        return tree -> {
            TreeCell<T> cell = factory == null ? new TreeCell<>() {
                @Override protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.toString());
                    setGraphic(null);
                }
            } : factory.call(tree);
            Tooltip name = new Tooltip();
            Runnable updateTip = () -> {
                if (cell.isEmpty() || cell.getText() == null || cell.getText().isBlank()) {
                    if (cell.getTooltip() == name) cell.setTooltip(null);
                } else {
                    name.setText(cell.getText());
                    if (cell.getTooltip() == null) cell.setTooltip(name);
                }
            };
            cell.textProperty().addListener((o, a, b) -> updateTip.run());
            cell.emptyProperty().addListener((o, a, b) -> updateTip.run());
            cell.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> updateTip.run());
            // Context menu factories often use the selection; point it at the clicked row first.
            cell.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
                if (!cell.isEmpty() && cell.getTreeItem() != null
                        && !tree.getSelectionModel().getSelectedItems().contains(cell.getTreeItem()))
                    tree.getSelectionModel().clearAndSelect(cell.getIndex());
            });
            return cell;
        };
    }
}
