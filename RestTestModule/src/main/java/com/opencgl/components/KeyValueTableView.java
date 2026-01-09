package com.opencgl.components;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Postman风格的键值对编辑器
 * 特性：
 * - 启用/禁用复选框
 * - 即时编辑（无需双击进入编辑模式）
 * - 最后一行自动为空行，填写后自动添加新行
 * - 鼠标悬停显示删除按钮
 * - 支持快捷键操作
 */
public class KeyValueTableView extends VBox {
    
    private final ObservableList<KeyValueRow> rows = FXCollections.observableArrayList();
    private final VBox rowsContainer = new VBox(2);
    private final boolean showDescription;
    private final String keyPlaceholder;
    private final String valuePlaceholder;
    
    public KeyValueTableView() {
        this(true, "Key", "Value");
    }
    
    public KeyValueTableView(boolean showDescription, String keyPlaceholder, String valuePlaceholder) {
        this.showDescription = showDescription;
        this.keyPlaceholder = keyPlaceholder;
        this.valuePlaceholder = valuePlaceholder;
        
        initUI();
        addEmptyRow(); // 初始添加一个空行
    }
    
    private void initUI() {
        setSpacing(0);
        setPadding(new Insets(0));
        
        // 表头
        HBox header = createHeader();
        
        // 滚动容器
        ScrollPane scrollPane = new ScrollPane(rowsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        rowsContainer.setSpacing(2);
        rowsContainer.setPadding(new Insets(4, 0, 4, 0));
        rowsContainer.setStyle("-fx-background-color: transparent;");
        
        getChildren().addAll(header, scrollPane);
        
        // 监听行变化
        rows.addListener((ListChangeListener<KeyValueRow>) c -> {
            rowsContainer.getChildren().clear();
            for (KeyValueRow row : rows) {
                rowsContainer.getChildren().add(row);
            }
        });
    }
    
    private HBox createHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 12, 8, 12));
        header.setStyle("-fx-background-color: #f1f3f4; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        
        // 复选框占位
        Region checkboxSpacer = new Region();
        checkboxSpacer.setMinWidth(24);
        checkboxSpacer.setMaxWidth(24);
        
        Label keyLabel = new Label(keyPlaceholder.toUpperCase());
        keyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #5f6368; -fx-font-size: 11px;");
        keyLabel.setMinWidth(150);
        HBox.setHgrow(keyLabel, Priority.ALWAYS);
        
        Label valueLabel = new Label("VALUE");
        valueLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #5f6368; -fx-font-size: 11px;");
        valueLabel.setMinWidth(200);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);
        
        header.getChildren().addAll(checkboxSpacer, keyLabel, valueLabel);
        
        if (showDescription) {
            Label descLabel = new Label("DESCRIPTION");
            descLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #5f6368; -fx-font-size: 11px;");
            descLabel.setMinWidth(150);
            HBox.setHgrow(descLabel, Priority.ALWAYS);
            header.getChildren().add(descLabel);
        }
        
        // 删除按钮占位
        Region deleteSpacer = new Region();
        deleteSpacer.setMinWidth(28);
        deleteSpacer.setMaxWidth(28);
        header.getChildren().add(deleteSpacer);
        
        return header;
    }
    
    private void addEmptyRow() {
        KeyValueRow emptyRow = new KeyValueRow("", "", "", true);
        emptyRow.setPlaceholderStyle(true);
        rows.add(emptyRow);
    }
    
    /**
     * 添加一行数据
     */
    public void addRow(String key, String value, String description, boolean enabled) {
        // 在最后一个空行之前插入
        int insertIndex = rows.size() > 0 ? rows.size() - 1 : 0;
        KeyValueRow row = new KeyValueRow(key, value, description, enabled);
        rows.add(insertIndex, row);
    }
    
    /**
     * 清空所有行（保留空行）
     */
    public void clear() {
        rows.clear();
        addEmptyRow();
    }
    
    /**
     * 获取所有启用的键值对
     */
    public Map<String, String> getEnabledKeyValues() {
        Map<String, String> result = new HashMap<>();
        for (KeyValueRow row : rows) {
            if (row.isEnabled() && !row.getKey().isEmpty() && !row.getValue().isEmpty()) {
                result.put(row.getKey(), row.getValue());
            }
        }
        return result;
    }
    
    /**
     * 获取所有键值对（包括禁用的）
     */
    public List<Map<String, Object>> getAllRows() {
        return rows.stream()
            .filter(row -> !row.getKey().isEmpty() || !row.getValue().isEmpty())
            .map(row -> {
                Map<String, Object> map = new HashMap<>();
                map.put("key", row.getKey());
                map.put("value", row.getValue());
                map.put("description", row.getDescription());
                map.put("enabled", row.isEnabled());
                return map;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 从列表加载数据
     */
    public void loadFromList(List<Map<String, Object>> data) {
        rows.clear();
        for (Map<String, Object> item : data) {
            String key = (String) item.getOrDefault("key", "");
            String value = (String) item.getOrDefault("value", "");
            String desc = (String) item.getOrDefault("description", "");
            boolean enabled = (Boolean) item.getOrDefault("enabled", true);
            rows.add(new KeyValueRow(key, value, desc, enabled));
        }
        addEmptyRow();
    }
    
    /**
     * 内部行组件
     */
    private class KeyValueRow extends HBox {
        private final BooleanProperty enabled = new SimpleBooleanProperty(true);
        private final StringProperty key = new SimpleStringProperty("");
        private final StringProperty value = new SimpleStringProperty("");
        private final StringProperty description = new SimpleStringProperty("");
        
        private final CheckBox checkBox;
        private final TextField keyField;
        private final TextField valueField;
        private final TextField descField;
        private final Button deleteButton;
        
        private boolean isPlaceholder = false;
        
        public KeyValueRow(String key, String value, String description, boolean enabled) {
            this.key.set(key);
            this.value.set(value);
            this.description.set(description);
            this.enabled.set(enabled);
            
            setSpacing(8);
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(4, 12, 4, 12));
            setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
            
            // 启用复选框
            checkBox = new CheckBox();
            checkBox.setSelected(enabled);
            checkBox.selectedProperty().bindBidirectional(this.enabled);
            checkBox.setMinWidth(24);
            checkBox.setMaxWidth(24);
            
            // Key 输入框
            keyField = createTextField(keyPlaceholder, 150);
            keyField.textProperty().bindBidirectional(this.key);
            HBox.setHgrow(keyField, Priority.ALWAYS);
            
            // Value 输入框
            valueField = createTextField(valuePlaceholder, 200);
            valueField.textProperty().bindBidirectional(this.value);
            HBox.setHgrow(valueField, Priority.ALWAYS);
            
            // Description 输入框
            descField = createTextField("Description", 150);
            descField.textProperty().bindBidirectional(this.description);
            HBox.setHgrow(descField, Priority.ALWAYS);
            
            // 删除按钮
            deleteButton = new Button("×");
            deleteButton.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-text-fill: #999; " +
                "-fx-font-size: 16px; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 0 4;"
            );
            deleteButton.setMinWidth(28);
            deleteButton.setMaxWidth(28);
            deleteButton.setVisible(false);
            deleteButton.setOnAction(e -> removeRow());
            
            // 鼠标悬停显示删除按钮
            setOnMouseEntered(e -> {
                if (!isPlaceholder) {
                    deleteButton.setVisible(true);
                    setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
                }
            });
            setOnMouseExited(e -> {
                deleteButton.setVisible(false);
                setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
            });
            
            // 删除按钮悬停效果
            deleteButton.setOnMouseEntered(e -> 
                deleteButton.setStyle("-fx-background-color: #ffebee; -fx-text-fill: #d32f2f; -fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 0 4; -fx-background-radius: 4;")
            );
            deleteButton.setOnMouseExited(e -> 
                deleteButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 0 4;")
            );
            
            // 输入时检查是否需要添加新行
            keyField.textProperty().addListener((obs, oldVal, newVal) -> checkAddNewRow());
            valueField.textProperty().addListener((obs, oldVal, newVal) -> checkAddNewRow());
            
            // 快捷键支持
            setupKeyboardShortcuts();
            
            getChildren().addAll(checkBox, keyField, valueField);
            if (showDescription) {
                getChildren().add(descField);
            }
            getChildren().add(deleteButton);
        }
        
        private TextField createTextField(String placeholder, int minWidth) {
            TextField field = new TextField();
            field.setPromptText(placeholder);
            field.setMinWidth(minWidth);
            field.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-border-color: transparent; " +
                "-fx-padding: 4 8; " +
                "-fx-font-size: 13px;"
            );
            field.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    field.setStyle("-fx-background-color: #fff; -fx-border-color: #1a73e8; -fx-border-width: 0 0 2 0; -fx-padding: 4 8; -fx-font-size: 13px;");
                } else {
                    field.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 4 8; -fx-font-size: 13px;");
                }
            });
            return field;
        }
        
        private void setupKeyboardShortcuts() {
            setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    // Enter: 添加新行并聚焦
                    int index = rows.indexOf(this);
                    if (index < rows.size() - 1) {
                        KeyValueRow nextRow = rows.get(index + 1);
                        nextRow.keyField.requestFocus();
                    }
                } else if (event.isControlDown() && event.getCode() == KeyCode.D) {
                    if (event.isShiftDown()) {
                        // Ctrl+Shift+D: 禁用/启用
                        checkBox.setSelected(!checkBox.isSelected());
                    } else {
                        // Ctrl+D: 删除行
                        removeRow();
                    }
                    event.consume();
                }
            });
        }
        
        private void checkAddNewRow() {
            if (isPlaceholder && (!this.key.get().isEmpty() || !this.value.get().isEmpty())) {
                isPlaceholder = false;
                setPlaceholderStyle(false);
                addEmptyRow();
            }
        }
        
        private void removeRow() {
            if (!isPlaceholder && rows.size() > 1) {
                rows.remove(this);
            }
        }
        
        public void setPlaceholderStyle(boolean placeholder) {
            this.isPlaceholder = placeholder;
            if (placeholder) {
                checkBox.setVisible(false);
                setStyle("-fx-background-color: #fafafa; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
            } else {
                checkBox.setVisible(true);
            }
        }
        
        public boolean isEnabled() { return enabled.get(); }
        public String getKey() { return key.get(); }
        public String getValue() { return value.get(); }
        public String getDescription() { return description.get(); }
    }
}
