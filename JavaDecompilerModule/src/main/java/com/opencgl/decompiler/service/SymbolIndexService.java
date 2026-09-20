package com.opencgl.decompiler.service;

import com.opencgl.decompiler.model.ClassNode;
import javafx.scene.control.TreeItem;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.io.*;
import java.nio.file.*;
import javassist.bytecode.ClassFile;

/** Lightweight asynchronous-friendly index of loaded class nodes. It does not decompile classes. */
public final class SymbolIndexService {
    private final Map<String, List<ClassNode>> classes = new ConcurrentHashMap<>();
    public record Entry(ClassNode node, String qualifiedName) {}
    private List<Entry> entries = List.of();
    public List<Entry> entries() { return entries; }

    public void index(TreeItem<ClassNode> root, BiConsumer<Integer, Integer> progress) {
        index(snapshot(root), progress);
    }

    /** Take this snapshot on the FX thread; workers must not traverse live TreeItems. */
    public static List<ClassNode> snapshot(TreeItem<ClassNode> root) {
        List<ClassNode> all = new ArrayList<>(); collect(root, all); return List.copyOf(all);
    }

    public void index(List<ClassNode> all, BiConsumer<Integer, Integer> progress) {
        classes.clear();
        List<Entry> indexed = new ArrayList<>();
        int done = 0;
        for (ClassNode node : all) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                byte[] bytes = node.getClassBytes();
                if (bytes == null) bytes = Files.readAllBytes(Path.of(node.getFullPath()));
                String qualified = new ClassFile(new DataInputStream(new ByteArrayInputStream(bytes))).getName();
                indexed.add(new Entry(node, qualified));
                classes.computeIfAbsent(qualified, ignored -> new ArrayList<>()).add(node);
                String simple = qualified.substring(qualified.lastIndexOf('.') + 1);
                if (!simple.equals(qualified)) classes.computeIfAbsent(simple, ignored -> new ArrayList<>()).add(node);
            } catch (IOException | RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(SymbolIndexService.class).warn("无法索引 class: {}", node.getFullPath(), ex);
            }
            if (++done % 64 == 0) progress.accept(done, all.size());
        }
        entries = List.copyOf(indexed);
        progress.accept(done, all.size());
    }

    public List<ClassNode> findClass(String name) { return classes.getOrDefault(name, List.of()); }
    public void clear() { classes.clear(); entries = List.of(); }

    private static void collect(TreeItem<ClassNode> item, List<ClassNode> out) {
        if (item == null) return;
        if (item.getValue().isClass()) out.add(item.getValue());
        for (TreeItem<ClassNode> child : item.getChildren()) collect(child, out);
    }
}
