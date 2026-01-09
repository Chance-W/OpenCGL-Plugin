package com.opencgl.decompiler.model;

/**
 * 树节点模型
 */
public class ClassNode {
    private final String name;
    private final String fullPath;
    private final boolean isClass;
    private final boolean isDirectory;
    private byte[] classBytes;

    public ClassNode(String name, String fullPath, boolean isClass, boolean isDirectory) {
        this.name = name;
        this.fullPath = fullPath;
        this.isClass = isClass;
        this.isDirectory = isDirectory;
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
