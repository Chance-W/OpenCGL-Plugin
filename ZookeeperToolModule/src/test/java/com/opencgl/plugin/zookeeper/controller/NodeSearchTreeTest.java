package com.opencgl.plugin.zookeeper.controller;

import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NodeSearchTreeTest {
    @Test void retainsAncestorsAndDoesNotMutateOriginalTreeOrIncludeLoadingRows() {
        var root = new TreeItem<>("/");
        var app = new TreeItem<>("apps");
        var order = new TreeItem<>("Order");
        order.getChildren().add(new TreeItem<>("Loading..."));
        app.getChildren().addAll(order, new TreeItem<>("Payment"));
        root.getChildren().add(app);
        var paths = NodeSearchTree.loadedPaths(root, "Loading...");
        assertEquals(List.of("/", "/apps", "/apps/Order", "/apps/Payment"), paths);
        var result = NodeSearchTree.fromPaths(List.of("/apps/Order", "/apps/Order/worker"));
        var match = result.getChildren().get(0).getChildren().get(0);
        assertEquals("/apps/Order", NodeSearchTree.path(match));
        assertEquals("worker", match.getChildren().get(0).getValue());
        assertTrue(result.isExpanded());
        assertFalse(root.isExpanded());
        assertEquals(2, app.getChildren().size());
    }
}
