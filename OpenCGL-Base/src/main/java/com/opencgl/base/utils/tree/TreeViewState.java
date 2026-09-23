package com.opencgl.base.utils.tree;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import java.util.*;
import java.util.function.Function;

/** Snapshot of an already-loaded, local tree. Callers retain control of lazy loading and callbacks. */
public final class TreeViewState<T, K> {
    private static final Object RESTORING = new Object();
    private final Set<K> expanded;
    private final K selected;
    private final Function<TreeItem<T>, K> key;

    private TreeViewState(Set<K> expanded, K selected, Function<TreeItem<T>, K> key) {
        this.expanded = expanded; this.selected = selected; this.key = key;
    }

    public static <T, K> TreeViewState<T, K> capture(TreeView<T> tree, Function<TreeItem<T>, K> key) {
        Set<K> expanded = new HashSet<>();
        for (var item : loadedItems(tree.getRoot())) {
            K id = key.apply(item);
            if (id != null && item.isExpanded()) expanded.add(id);
        }
        var selection = tree.getSelectionModel().getSelectedItem();
        return new TreeViewState<>(expanded, selection == null ? null : key.apply(selection), key);
    }

    /** Does not select another item when the original key no longer exists. */
    public void restore(TreeView<T> tree) {
        Object previous = tree.getProperties().put(RESTORING, Boolean.TRUE);
        try {
        TreeItem<T> target = null;
        // Snapshot first: expanding must not cause us to recursively walk newly fetched data.
        for (var item : loadedItems(tree.getRoot())) {
            K id = key.apply(item);
            item.setExpanded(id != null && expanded.contains(id));
            if (selected != null && Objects.equals(selected, id)) target = item;
        }
        tree.getSelectionModel().clearSelection();
        if (target != null) {
            for (var parent = target.getParent(); parent != null; parent = parent.getParent()) parent.setExpanded(true);
            tree.getSelectionModel().select(target);
        }
        } finally {
            if (previous == null) tree.getProperties().remove(RESTORING);
            else tree.getProperties().put(RESTORING, previous);
        }
    }

    public static boolean isRestoring(TreeView<?> tree) {
        return Boolean.TRUE.equals(tree.getProperties().get(RESTORING));
    }

    public TreeViewState<T, K> withSelection(K id) {
        return new TreeViewState<>(expanded, id, key);
    }

    /** Keeps user disclosure changes while other pages are still pending. */
    public TreeViewState<T, K> withExpansion(K id, boolean value) {
        Set<K> updated = new HashSet<>(expanded);
        if (id != null) {
            if (value) updated.add(id); else updated.remove(id);
        }
        return new TreeViewState<>(updated, selected, key);
    }

    /** Transfers only public DTO collection trees; unrelated/remote trees are deliberately ignored. */
    public static void transferCollections(javafx.scene.Node oldView, javafx.scene.Node newView) {
        TreeView<com.opencgl.base.model.BaseDataDto> oldTree = collectionTree(oldView);
        TreeView<com.opencgl.base.model.BaseDataDto> newTree = collectionTree(newView);
        if (oldTree != null && newTree != null) {
            capture(oldTree, item -> item.getValue() == null ? null : item.getValue().getId()).restore(newTree);
        }
    }

    @SuppressWarnings("unchecked")
    private static TreeView<com.opencgl.base.model.BaseDataDto> collectionTree(javafx.scene.Node node) {
        if (node instanceof TreeView<?> tree) {
            return tree.getRoot() != null && tree.getRoot().getValue() instanceof com.opencgl.base.model.BaseDataDto
                    ? (TreeView<com.opencgl.base.model.BaseDataDto>) tree : null;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) {
                var found = collectionTree(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    public void restoreSelection(TreeView<T> tree) {
        tree.getSelectionModel().clearSelection();
        if (selected == null) return;
        for (var item : loadedItems(tree.getRoot())) {
            if (Objects.equals(selected, key.apply(item))) {
                tree.getSelectionModel().select(item);
                return;
            }
        }
    }

    /** Only use for TreeItems whose getChildren() does not perform network I/O. */
    public static <T> List<TreeItem<T>> loadedItems(TreeItem<T> root) {
        List<TreeItem<T>> items = new ArrayList<>();
        if (root == null) return items;
        Set<TreeItem<T>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<TreeItem<T>> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            TreeItem<T> item = pending.pop();
            if (!visited.add(item)) continue;
            items.add(item);
            var children = item.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) pending.push(children.get(i));
        }
        return items;
    }
}
