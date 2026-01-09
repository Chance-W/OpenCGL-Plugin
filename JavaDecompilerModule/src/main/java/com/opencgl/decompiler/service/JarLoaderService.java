package com.opencgl.decompiler.service;

import com.opencgl.decompiler.model.ClassNode;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Jar/Zip文件加载服务
 */
public class JarLoaderService {
    private static final Logger logger = LoggerFactory.getLogger(JarLoaderService.class);
    
    private File currentFile;
    private JarFile jarFile;
    private final Map<String, byte[]> classDataCache = new HashMap<>();

    /**
     * 加载Jar文件并构建树结构
     */
    public TreeItem<ClassNode> loadJarFile(File file) throws IOException {
        this.currentFile = file;
        this.jarFile = new JarFile(file);
        this.classDataCache.clear();

        ClassNode rootNode = new ClassNode(file.getName(), file.getAbsolutePath(), false, true);
        TreeItem<ClassNode> root = new TreeItem<>(rootNode);
        root.setExpanded(true);

        // 使用Map来避免重复节点
        Map<String, TreeItem<ClassNode>> nodeMap = new HashMap<>();
        nodeMap.put("", root);

        // 收集所有entry
        List<JarEntry> entries = new ArrayList<>();
        Enumeration<JarEntry> enumeration = jarFile.entries();
        while (enumeration.hasMoreElements()) {
            entries.add(enumeration.nextElement());
        }

        // 排序确保父目录先创建
        entries.sort(Comparator.comparing(JarEntry::getName));

        for (JarEntry entry : entries) {
            String fullPath = entry.getName();
            
            // 跳过META-INF等无关目录
            if (fullPath.startsWith("META-INF/") || fullPath.startsWith("module-info")) {
                continue;
            }

            // 跳过目录entry本身
            if (entry.isDirectory()) {
                continue;
            }

            // 只处理class文件
            if (!fullPath.endsWith(".class")) {
                continue;
            }

            // 构建路径节点
            buildPathNodes(fullPath, entry, nodeMap, root);
        }

        return root;
    }

    /**
     * 构建路径节点（避免重复）
     */
    private void buildPathNodes(String fullPath, JarEntry entry, 
                                  Map<String, TreeItem<ClassNode>> nodeMap, 
                                  TreeItem<ClassNode> root) {
        String[] parts = fullPath.split("/");
        StringBuilder currentPath = new StringBuilder();
        TreeItem<ClassNode> parent = root;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            
            // 构建当前完整路径
            if (currentPath.length() > 0) {
                currentPath.append("/");
            }
            currentPath.append(part);
            String pathKey = currentPath.toString();

            // 检查节点是否已存在
            if (nodeMap.containsKey(pathKey)) {
                parent = nodeMap.get(pathKey);
            } else {
                // 创建新节点
                boolean isClass = i == parts.length - 1 && part.endsWith(".class");
                boolean isDir = !isClass;

                ClassNode node = new ClassNode(part, pathKey, isClass, isDir);
                TreeItem<ClassNode> item = new TreeItem<>(node);

                // 添加到父节点
                parent.getChildren().add(item);
                nodeMap.put(pathKey, item);

                // 如果是class文件，缓存字节码
                if (isClass) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        byte[] data = is.readAllBytes();
                        classDataCache.put(pathKey, data);
                        node.setClassBytes(data);
                    } catch (IOException e) {
                        logger.error("读取class文件失败: " + pathKey, e);
                    }
                }

                parent = item;
            }
        }
    }

    /**
     * 加载单个class文件
     */
    public TreeItem<ClassNode> loadClassFile(File file) throws IOException {
        byte[] classData = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(classData);
        }

        ClassNode node = new ClassNode(file.getName(), file.getAbsolutePath(), true, false);
        node.setClassBytes(classData);
        
        TreeItem<ClassNode> root = new TreeItem<>(node);
        return root;
    }

    /**
     * 获取class文件的字节码
     */
    public byte[] getClassBytes(String path) {
        return classDataCache.get(path);
    }

    /**
     * 关闭资源
     */
    public void close() {
        if (jarFile != null) {
            try {
                jarFile.close();
            } catch (IOException e) {
                logger.error("关闭jar文件失败", e);
            }
        }
        classDataCache.clear();
    }

    public File getCurrentFile() {
        return currentFile;
    }
}
