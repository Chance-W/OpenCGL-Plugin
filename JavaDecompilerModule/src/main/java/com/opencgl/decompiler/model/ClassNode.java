package com.opencgl.decompiler.model;

/**
 * 树节点模型
 */
public class ClassNode {
    private final String name;
    private final String fullPath;
    private final boolean isClass;
    private final boolean isDirectory;
    private final String sourcePath;
    private final String entryPath;
    private byte[] classBytes;

    public ClassNode(String name, String fullPath, boolean isClass, boolean isDirectory) {
        this(name, fullPath, isClass, isDirectory, null, null);
    }

    public ClassNode(String name, String fullPath, boolean isClass, boolean isDirectory,
                     String sourcePath, String entryPath) {
        this.name = name;
        this.fullPath = fullPath;
        this.isClass = isClass;
        this.isDirectory = isDirectory;
        this.sourcePath = sourcePath;
        this.entryPath = entryPath;
    }

    public String getName() {
        return name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public boolean isClass() {
        return isClass;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public byte[] getClassBytes() {
        return classBytes;
    }

    public void setClassBytes(byte[] classBytes) {
        this.classBytes = classBytes;
    }

    public String getSourcePath() { return sourcePath; }
    public String getEntryPath() { return entryPath; }

    @Override
    public String toString() {
        return name;
    }

    /**
     * 获取图标
     */
    public String getIcon() {
        if (isDirectory) {
            return "📁";
        } else if (isClass) {
            return "☕";
        } else {
            return "📄";
        }
    }
}
