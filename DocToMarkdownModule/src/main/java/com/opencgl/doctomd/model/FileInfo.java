package com.opencgl.doctomd.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * 与 C++ doc-converter 一致的单个文件项：路径、输出目录、资源目录、状态。
 */
public class FileInfo {
    private final StringProperty filePath = new SimpleStringProperty("");
    private final StringProperty outputDir = new SimpleStringProperty("");
    private final StringProperty mediaDir = new SimpleStringProperty("media");
    private final StringProperty status = new SimpleStringProperty("");

    public FileInfo() {
    }

    public FileInfo(String filePath, String outputDir, String mediaDir) {
        this.filePath.set(filePath);
        this.outputDir.set(outputDir);
        this.mediaDir.set(mediaDir != null && !mediaDir.isEmpty() ? mediaDir : "media");
    }

    public String getFilePath() { return filePath.get(); }
    public void setFilePath(String v) { filePath.set(v); }
    public StringProperty filePathProperty() { return filePath; }

    public String getOutputDir() { return outputDir.get(); }
    public void setOutputDir(String v) { outputDir.set(v); }
    public StringProperty outputDirProperty() { return outputDir; }

    public String getMediaDir() { return mediaDir.get(); }
    public void setMediaDir(String v) { mediaDir.set(v); }
    public StringProperty mediaDirProperty() { return mediaDir; }

    public String getStatus() { return status.get(); }
    public void setStatus(String v) { status.set(v); }
    public StringProperty statusProperty() { return status; }

    /** 仅用于表格显示：文件名（不含路径） */
    public String getFileName() {
        String p = getFilePath();
        if (p == null || p.isEmpty()) return "";
        int i = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        return i < 0 ? p : p.substring(i + 1);
    }

    /** 扩展名小写 */
    public String getExtension() {
        String name = getFileName();
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
}
