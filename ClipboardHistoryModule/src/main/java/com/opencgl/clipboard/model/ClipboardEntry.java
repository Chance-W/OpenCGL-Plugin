package com.opencgl.clipboard.model;

import javafx.beans.property.*;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ClipboardEntry {
    public enum Type { TEXT, IMAGE, FILE }
    
    private final StringProperty id = new SimpleStringProperty();
    private final ObjectProperty<Type> type = new SimpleObjectProperty<>();
    private final StringProperty content = new SimpleStringProperty();
    private final ObjectProperty<byte[]> imageData = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> timestamp = new SimpleObjectProperty<>();
    private final BooleanProperty favorite = new SimpleBooleanProperty(false);
    
    public ClipboardEntry(Type type, String content) {
        this.id.set(UUID.randomUUID().toString());
        this.type.set(type);
        this.content.set(content);
        this.timestamp.set(LocalDateTime.now());
    }
    
    public ClipboardEntry(byte[] imageData) {
        this.id.set(UUID.randomUUID().toString());
        this.type.set(Type.IMAGE);
        this.imageData.set(imageData);
        this.timestamp.set(LocalDateTime.now());
        this.content.set("[图片]");
    }
    
    public String getDisplayText() {
        if (type.get() == Type.TEXT && content.get() != null) {
            String text = content.get().trim();
            return text.length() > 100 ? text.substring(0, 100) + "..." : text;
        }
        return content.get();
    }
    
    public String getTimeAgo() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime time = timestamp.get();
        long minutes = java.time.Duration.between(time, now).toMinutes();
        
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        if (minutes < 1440) return (minutes / 60) + "小时前";
        return time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
    
    public Image getImage() {
        if (imageData.get() != null) {
            return new Image(new ByteArrayInputStream(imageData.get()));
        }
        return null;
    }
    
    // Getters and Property methods
    public String getId() { return id.get(); }
    public Type getType() { return type.get(); }
    public String getContent() { return content.get(); }
    public byte[] getImageData() { return imageData.get(); }
    public LocalDateTime getTimestamp() { return timestamp.get(); }
    public boolean isFavorite() { return favorite.get(); }
    
    public void setFavorite(boolean value) { favorite.set(value); }
    
    public BooleanProperty favoriteProperty() { return favorite; }
}
