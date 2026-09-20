package com.opencgl.plugin.zookeeper.controller;

import javafx.scene.control.TreeItem;
import java.util.*;

/** Search projections never reparent or change expansion state of the browsing tree. */
public final class NodeSearchTree {
    private NodeSearchTree() {}

    public static String path(TreeItem<String> item) {
        var parts = new ArrayDeque<String>();
        for (var current = item; current != null; current = current.getParent()) {
            if (!"/".equals(current.getValue())) parts.addFirst(current.getValue());
        }
        return "/" + String.join("/", parts);
    }

    public static List<String> loadedPaths(TreeItem<String> root, String placeholder) {
        var paths = new ArrayList<String>();
        if (root == null) return paths;
        var pending = new ArrayDeque<TreeItem<String>>(); pending.add(root);
        while (!pending.isEmpty()) {
            var item = pending.removeFirst();
            if (Objects.equals(placeholder, item.getValue())) continue;
            paths.add(path(item));
            pending.addAll(item.getChildren());
        }
        return paths;
    }

    public static TreeItem<String> fromPaths(List<String> paths) {
        var root = new TreeItem<>("/");
        var nodes = new HashMap<String, TreeItem<String>>(); nodes.put("/", root);
        for (String path : paths) {
            String prefix = "";
            var parent = root;
            for (String part : path.split("/")) {
                if (part.isEmpty()) continue;
                prefix += "/" + part;
                var node = nodes.get(prefix);
                if (node == null) {
                    node = new TreeItem<>(part);
                    parent.getChildren().add(node);
                    nodes.put(prefix, node);
                }
                parent = node;
            }
        }
        nodes.values().forEach(node -> node.setExpanded(true));
        return root;
    }
}
