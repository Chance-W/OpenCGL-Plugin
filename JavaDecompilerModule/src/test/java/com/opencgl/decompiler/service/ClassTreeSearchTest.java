package com.opencgl.decompiler.service;

import com.opencgl.decompiler.model.ClassNode;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassTreeSearchTest {
    @Test void parentMatchKeepsAllChildrenWithoutReparentingOriginals() {
        var root = new TreeItem<>(new ClassNode("root", "root", false, true));
        var jar = new TreeItem<>(new ClassNode("test.jar", "test.jar", false, true));
        var leaf = new TreeItem<>(new ClassNode("Foo.class", "Foo.class", true, false));
        root.getChildren().add(jar); jar.getChildren().add(leaf);
        var filtered = ClassTreeSearch.filter(root, "TEST.JAR");
        assertEquals(1, filtered.getChildren().getFirst().getChildren().size());
        assertSame(jar, leaf.getParent());
        assertNotSame(leaf, filtered.getChildren().getFirst().getChildren().getFirst());
        assertNull(ClassTreeSearch.filter(root, "not-found"));
    }
}
