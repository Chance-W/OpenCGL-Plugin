package com.opencgl.decompiler.service;

import com.opencgl.base.utils.tree.TreeViewState;
import com.opencgl.decompiler.model.ClassNode;
import javafx.scene.control.TreeItem;
import java.util.*;

/** Local JAR tree projection. Parent matches include descendants; original nodes are never reparented. */
public final class ClassTreeSearch {
    private ClassTreeSearch() {}
    public static TreeItem<ClassNode> filter(TreeItem<ClassNode> root, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        var items = TreeViewState.loadedItems(root);
        Map<TreeItem<ClassNode>, Boolean> matches = new IdentityHashMap<>();
        Map<TreeItem<ClassNode>, TreeItem<ClassNode>> copies = new IdentityHashMap<>();
        for (var item : items) {
            matches.put(item, Boolean.TRUE.equals(matches.get(item.getParent()))
                    || (item.getValue() != null && item.getValue().getName().toLowerCase(Locale.ROOT).contains(needle)));
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            var item = items.get(i);
            var copy = new TreeItem<>(item.getValue());
            for (var child : item.getChildren()) {
                var filtered = copies.get(child);
                if (filtered != null) copy.getChildren().add(filtered);
            }
            if (matches.get(item) || !copy.getChildren().isEmpty()) {
                copy.setExpanded(true); copies.put(item, copy);
            }
        }
        return copies.get(root);
    }
}
