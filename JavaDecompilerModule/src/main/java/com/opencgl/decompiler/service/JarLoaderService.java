package com.opencgl.decompiler.service;

import com.opencgl.decompiler.model.ClassNode;
import javafx.scene.control.TreeItem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Loads class files and archives. Archive handles are short-lived, allowing multiple JARs. */
public class JarLoaderService implements AutoCloseable {
    private final LinkedHashMap<String, File> loadedArchives = new LinkedHashMap<>();

    public static boolean isArchive(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip");
    }
    private String key(File f) { try { return f.getCanonicalPath(); } catch (IOException e) { return f.getAbsolutePath(); } }
    public void addLoadedArchive(File file) { if (file != null) loadedArchives.putIfAbsent(key(file), file.getAbsoluteFile()); }
    public void removeLoadedArchive(File file) { if (file != null) loadedArchives.remove(key(file)); }
    public List<File> getLoadedArchives() { return List.copyOf(loadedArchives.values()); }
    public void clearLoadedArchives() { loadedArchives.clear(); }

    public TreeItem<ClassNode> loadJarFile(File file) throws IOException {
        if (!isArchive(file)) throw new IOException("不支持的归档文件: " + file);
        ClassNode rootNode = new ClassNode(file.getName(), file.getAbsolutePath(), false, true, file.getAbsolutePath(), null);
        TreeItem<ClassNode> root = new TreeItem<>(rootNode); root.setExpanded(true);
        Map<String, TreeItem<ClassNode>> nodes = new HashMap<>(); nodes.put("", root);
        try (JarFile jar = new JarFile(file)) {
            List<JarEntry> entries = Collections.list(jar.entries());
            entries.sort(Comparator.comparing(JarEntry::getName));
            for (JarEntry entry : entries) {
                String path = entry.getName();
                if (entry.isDirectory() || !path.endsWith(".class") || path.startsWith("META-INF/") || path.startsWith("module-info")) continue;
                byte[] bytes;
                try (var in = jar.getInputStream(entry)) { bytes = in.readAllBytes(); }
                buildPathNodes(path, bytes, nodes, root, file);
            }
        }
        return root;
    }

    private void buildPathNodes(String path, byte[] bytes, Map<String, TreeItem<ClassNode>> nodes,
                                TreeItem<ClassNode> root, File source) {
        String[] parts = path.split("/"); StringBuilder current = new StringBuilder(); TreeItem<ClassNode> parent = root;
        for (int i = 0; i < parts.length; i++) {
            if (current.length() > 0) current.append('/'); current.append(parts[i]);
            String key = current.toString(); TreeItem<ClassNode> item = nodes.get(key);
            if (item == null) {
                boolean clazz = i == parts.length - 1;
                ClassNode node = new ClassNode(parts[i], key, clazz, !clazz, source.getAbsolutePath(), clazz ? key : null);
                item = new TreeItem<>(node); nodes.put(key, item); parent.getChildren().add(item);
                if (clazz) node.setClassBytes(bytes);
            }
            parent = item;
        }
    }

    public TreeItem<ClassNode> loadClassFile(File file) throws IOException {
        ClassNode node = new ClassNode(file.getName(), file.getAbsolutePath(), true, false, file.getAbsolutePath(), file.getName());
        node.setClassBytes(Files.readAllBytes(file.toPath())); return new TreeItem<>(node);
    }
    public byte[] getClassBytes(String path) { return null; }
    @Override public void close() { loadedArchives.clear(); }
}
