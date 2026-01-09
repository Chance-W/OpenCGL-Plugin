package com.opencgl.ruler.model;

import javafx.beans.property.*;

import java.time.LocalDateTime;

public class ColorHistory {
    private final ObjectProperty<javafx.scene.paint.Color> color = new SimpleObjectProperty<>();
    private final StringProperty hex = new SimpleStringProperty();
    private final StringProperty rgb = new SimpleStringProperty();
    private final ObjectProperty<LocalDateTime> timestamp = new SimpleObjectProperty<>();
    
    public ColorHistory(javafx.scene.paint.Color color) {
        this.color.set(color);
        this.hex.set(toHex(color));
        this.rgb.set(toRGB(color));
        this.timestamp.set(LocalDateTime.now());
    }
    
    private String toHex(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }
    
    private String toRGB(javafx.scene.paint.Color color) {
        return String.format("RGB(%d, %d, %d)",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }
    
    public javafx.scene.paint.Color getColor() { return color.get(); }
    public String getHex() { return hex.get(); }
    public String getRgb() { return rgb.get(); }
    public LocalDateTime getTimestamp() { return timestamp.get(); }
    
    public StringProperty hexProperty() { return hex; }
    public StringProperty rgbProperty() { return rgb; }
}
