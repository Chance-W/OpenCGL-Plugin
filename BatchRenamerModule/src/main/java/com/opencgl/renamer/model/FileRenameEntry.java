package com.opencgl.renamer.model;

import javafx.beans.property.*;

import java.io.File;

public class FileRenameEntry {
    private final ObjectProperty<File> originalFile = new SimpleObjectProperty<>();
    private final StringProperty originalName = new SimpleStringProperty();
    private final StringProperty newName = new SimpleStringProperty();
    private final BooleanProperty hasConflict = new SimpleBooleanProperty(false);
    private final StringProperty status = new SimpleStringProperty("待处理");
    
    public FileRenameEntry(File file) {
        this.originalFile.set(file);
        this.originalName.set(file.getName());
        this.newName.set(file.getName());
    }
    
    public void computeNewName(String prefix, String suffix, String find, String replace, 
                               boolean useRegex, int index, int digits) {
        String name = originalName.get();
        String nameOnly = getNameWithoutExtension(name);
        String ext = getExtension(name);
        
        // 正则替换
        if (useRegex && find != null && !find.isEmpty()) {
            try {
                nameOnly = nameOnly.replaceAll(find, replace != null ? replace : "");
            } catch (Exception e) {
                // 正则表达式错误，保持原样
            }
        } else if (find != null && !find.isEmpty()) {
            nameOnly = nameOnly.replace(find, replace != null ? replace : "");
        }
        
        // 前缀后缀
        if (prefix != null && !prefix.isEmpty()) {
            nameOnly = prefix + nameOnly;
        }
        if (suffix != null && !suffix.isEmpty()) {
            nameOnly = nameOnly + suffix;
        }
        
        // 序号
        if (digits > 0) {
            String number = String.format("%0" + digits + "d", index);
            nameOnly = nameOnly + "_" + number;
        }
        
        newName.set(nameOnly + ext);
    }
    
    private String getNameWithoutExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }
    
    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }
    
    public boolean needsRename() {
        return !originalName.get().equals(newName.get());
    }
    
    // Getters and Properties
    public File getOriginalFile() { return originalFile.get(); }
    public String getOriginalName() { return originalName.get(); }
    public String getNewName() { return newName.get(); }
    public boolean isHasConflict() { return hasConflict.get(); }
    public String getStatus() { return status.get(); }
    
    public void setNewName(String value) { newName.set(value); }
    public void setHasConflict(boolean value) { hasConflict.set(value); }
    public void setStatus(String value) { status.set(value); }
    
    public StringProperty originalNameProperty() { return originalName; }
    public StringProperty newNameProperty() { return newName; }
    public BooleanProperty hasConflictProperty() { return hasConflict; }
    public StringProperty statusProperty() { return status; }
}
