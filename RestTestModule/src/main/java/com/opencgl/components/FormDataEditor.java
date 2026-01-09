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
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Form-Data 编辑器
 * 支持文本和文件类型参数
 */
public class FormDataEditor extends VBox {
    
    private final ObservableList<FormDataRow> rows = FXCollections.observableArrayList();
    private final VBox rowsContainer = new VBox(2);
    
    public FormDataEditor() {
        initUI();
        addEmptyRow();
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
        
        getChildren().addAll(header, scrollPane);
        
        rows.addListener((ListChangeListener<FormDataRow>) c -> {
            rowsContainer.getChildren().clear();
            for (FormDataRow row : rows) {
                rowsContainer.getChildren().add(row);
            }
        });
    }
    
    private HBox createHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 12, 8, 12));
        header.setStyle("-fx-background-color: #f1f3f4; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        
        Region checkboxSpacer = new Region();
        checkboxSpacer.setMinWidth(24);
        checkboxSpacer.setMaxWidth(24);
        
        Label keyLabel = new Label("KEY");
        keyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #5f6368; -fx-font-size: 11px;");
        keyLabel.setMinWidth(150);
        HBox.setHgrow(keyLabel, Priority.ALWAYS);
        
        Label valueLabel = new Label("VALUE");
        valueLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #5f6368; -fx-font-size: 11px;");
        valueLabel.setMinWidth(200);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);
        
        Label typeLabel = new Label("TYPE");
        typeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #5f6368; -fx-font-size: 11px;");
        typeLabel.setMinWidth(80);
        
        Region deleteSpacer = new Region();
        deleteSpacer.setMinWidth(28);
        deleteSpacer.setMaxWidth(28);
        
        header.getChildren().addAll(checkboxSpacer, keyLabel, valueLabel, typeLabel, deleteSpacer);
        
        return header;
    }
    
    private void addEmptyRow() {
        FormDataRow emptyRow = new FormDataRow("", "", "Text", true);
        emptyRow.setPlaceholderStyle(true);
        rows.add(emptyRow);
    }
    
    public void addRow(String key, String value, String type, boolean enabled) {
        int insertIndex = rows.size() > 0 ? rows.size() - 1 : 0;
        FormDataRow row = new FormDataRow(key, value, type, enabled);
        rows.add(insertIndex, row);
    }
    
    public void clear() {
        rows.clear();
        addEmptyRow();
    }
    
    /**
     * 获取所有启用的表单数据
     */
    public List<Map<String, Object>> getFormData() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (FormDataRow row : rows) {
            if (row.isEnabled() && !row.getKey().isEmpty()) {
                Map<String, Object> item = new HashMap<>();
                item.put("key", row.getKey());
                item.put("value", row.getValue());
                item.put("type", row.getType());
                item.put("file", row.getFile());
                result.add(item);
            }
        }
        return result;
    }
    
    /**
     * 内部行组件
     */
    private class FormDataRow extends HBox {
        private final BooleanProperty enabled = new SimpleBooleanProperty(true);
        private final StringProperty key = new SimpleStringProperty("");
        private final StringProperty value = new SimpleStringProperty("");
        private final StringProperty type = new SimpleStringProperty("Text");
        private File file;
        
        private final CheckBox checkBox;
        private final TextField keyField;
        private final TextField valueField;
        private final Button fileButton;
        private final ComboBox<String> typeCombo;
        private final Button deleteButton;
        
        private boolean isPlaceholder = false;
        
        public FormDataRow(String key, String value, String type, boolean enabled) {
            this.key.set(key);
            this.value.set(value);
            this.type.set(type);
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
            keyField = createTextField("Key", 150);
            keyField.textProperty().bindBidirectional(this.key);
            HBox.setHgrow(keyField, Priority.ALWAYS);
            
            // Value 输入框 (文本模式)
            valueField = createTextField("Value", 200);
            valueField.textProperty().bindBidirectional(this.value);
            HBox.setHgrow(valueField, Priority.ALWAYS);
            
            // 文件选择按钮 (文件模式)
            fileButton = new Button("Select File");
            fileButton.setStyle("-fx-background-color: #e8eaed; -fx-text-fill: #202124; -fx-padding: 4 12;");
            fileButton.setMinWidth(200);
            fileButton.setVisible(false);
            fileButton.setOnAction(e -> selectFile());
            HBox.setHgrow(fileButton, Priority.ALWAYS);
            
            // 类型选择
            typeCombo = new ComboBox<>();
            typeCombo.getItems().addAll("Text", "File");
            typeCombo.setValue(type);
            typeCombo.setMinWidth(80);
            typeCombo.setStyle("-fx-background-color: #f8f9fa;");
            typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                this.type.set(newVal);
                updateValueField();
            });
            
            // 删除按钮
            deleteButton = new Button("×");
            deleteButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; -fx-font-size: 16px; -fx-cursor: hand;");
            deleteButton.setMinWidth(28);
            deleteButton.setMaxWidth(28);
            deleteButton.setVisible(false);
            deleteButton.setOnAction(e -> removeRow());
            
            // 鼠标悬停
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
            
            // 输入监听
            keyField.textProperty().addListener((obs, oldVal, newVal) -> checkAddNewRow());
            valueField.textProperty().addListener((obs, oldVal, newVal) -> checkAddNewRow());
            
            getChildren().addAll(checkBox, keyField, valueField, fileButton, typeCombo, deleteButton);
        }
        
        private TextField createTextField(String placeholder, int minWidth) {
            TextField field = new TextField();
            field.setPromptText(placeholder);
            field.setMinWidth(minWidth);
            field.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 4 8; -fx-font-size: 13px;");
            return field;
        }
        
        private void updateValueField() {
            boolean isFile = "File".equals(type.get());
            valueField.setVisible(!isFile);
            valueField.setManaged(!isFile);
            fileButton.setVisible(isFile);
            fileButton.setManaged(isFile);
        }
        
        private void selectFile() {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select File");
            File selectedFile = fileChooser.showOpenDialog(getScene().getWindow());
            if (selectedFile != null) {
                this.file = selectedFile;
                fileButton.setText(selectedFile.getName());
                checkAddNewRow();
            }
        }
        
        private void checkAddNewRow() {
            if (isPlaceholder && !this.key.get().isEmpty()) {
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
        public String getType() { return type.get(); }
        public File getFile() { return file; }
    }
}
