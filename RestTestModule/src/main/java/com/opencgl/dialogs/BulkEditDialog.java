package com.opencgl.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量编辑对话框
 * 支持类Postman的批量编辑模式，格式：Key:Value
 */
public class BulkEditDialog extends Stage {
    
    private final TextArea textArea;
    private List<Map<String, Object>> result;
    private boolean confirmed = false;
    
    public BulkEditDialog() {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Bulk Edit");
        setWidth(500);
        setHeight(400);
        
        // 主布局
        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: white;");
        
        // 说明标签
        Label hintLabel = new Label("每行一个键值对，格式: Key:Value");
        hintLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
        
        // 文本区域
        textArea = new TextArea();
        textArea.setPromptText("Content-Type:application/json\nAuthorization:Bearer your-token\nX-Custom-Header:custom-value");
        textArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 13px;");
        VBox.setVgrow(textArea, Priority.ALWAYS);
        
        // 按钮区域
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-background-color: #f1f3f4; -fx-text-fill: #5f6368; -fx-padding: 8 16;");
        cancelButton.setOnAction(e -> close());
        
        Button saveButton = new Button("Save");
        saveButton.setStyle("-fx-background-color: #1a73e8; -fx-text-fill: white; -fx-padding: 8 24; -fx-font-weight: bold;");
        saveButton.setOnAction(e -> {
            confirmed = true;
            result = parseText();
            close();
        });
        
        buttonBox.getChildren().addAll(cancelButton, saveButton);
        
        root.getChildren().addAll(hintLabel, textArea, buttonBox);
        
        Scene scene = new Scene(root);
        setScene(scene);
    }
    
    /**
     * 设置初始数据
     */
    public void setData(List<Map<String, Object>> data) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : data) {
            String key = (String) item.getOrDefault("key", "");
            String value = (String) item.getOrDefault("value", "");
            if (!key.isEmpty() || !value.isEmpty()) {
                sb.append(key).append(":").append(value).append("\n");
            }
        }
        textArea.setText(sb.toString());
    }
    
    /**
     * 解析文本
     */
    private List<Map<String, Object>> parseText() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] lines = textArea.getText().split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                
                Map<String, Object> item = new HashMap<>();
                item.put("key", key);
                item.put("value", value);
                item.put("description", "");
                item.put("enabled", true);
                list.add(item);
            }
        }
        
        return list;
    }
    
    /**
     * 是否确认
     */
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * 获取结果
     */
    public List<Map<String, Object>> getResult() {
        return result;
    }
    
    /**
     * 显示对话框并返回结果
     */
    public static List<Map<String, Object>> showAndWait(List<Map<String, Object>> currentData) {
        BulkEditDialog dialog = new BulkEditDialog();
        if (currentData != null) {
            dialog.setData(currentData);
        }
        dialog.showAndWait();
        
        if (dialog.isConfirmed()) {
            return dialog.getResult();
        }
        return null;
    }
}
