package com.opencgl.http.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * 键值对条目（用于Headers和Params表格）
 */
public class KeyValueEntry {
    private final StringProperty key;
    private final StringProperty value;
    private final BooleanProperty enabled;
    private final BooleanProperty readOnly;

    public KeyValueEntry() {
        this("", "", true, false);
    }

    public KeyValueEntry(String key, String value) {
        this(key, value, true, false);
    }

    public KeyValueEntry(String key, String value, boolean enabled) {
        this(key, value, enabled, false);
    }

    public KeyValueEntry(String key, String value, boolean enabled, boolean readOnly) {
        this.key = new SimpleStringProperty(key);
        this.value = new SimpleStringProperty(value);
        this.enabled = new SimpleBooleanProperty(enabled);
        this.readOnly = new SimpleBooleanProperty(readOnly);
    }

    public String getKey() {
        return key.get();
    }

    public void setKey(String key) {
        this.key.set(key);
    }

    public StringProperty keyProperty() {
        return key;
    }

    public String getValue() {
        return value.get();
    }

    public void setValue(String value) {
        this.value.set(value);
    }

    public StringProperty valueProperty() {
        return value;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public boolean isReadOnly() {
        return readOnly.get();
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly.set(readOnly);
    }

    public BooleanProperty readOnlyProperty() {
        return readOnly;
    }
}
