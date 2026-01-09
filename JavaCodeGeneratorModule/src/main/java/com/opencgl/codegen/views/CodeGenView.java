package com.opencgl.codegen.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * 代码生成器视图
 */
public class CodeGenView {
    @FXML public StackPane rootPane;
    
    // 输入
    @FXML public MFXComboBox<String> conversionModeCombo;
    @FXML public MFXComboBox<String> sourceTypeCombo;
    @FXML public VBox dbTypeBox;
    @FXML public MFXComboBox<String> dbTypeCombo;
    @FXML public TextArea inputArea;
    
    // 配置
    @FXML public TextField classNameField;
    @FXML public TextField packageNameField;
    @FXML public MFXComboBox<String> classStyleCombo;
    @FXML public MFXComboBox<String> fieldStyleCombo;
    @FXML public MFXComboBox<String> methodStyleCombo;
    @FXML public CheckBox lombokCheckbox;
    @FXML public VBox annotationTypeBox;
    @FXML public MFXComboBox<String> annotationTypeCombo;
    
    // 操作
    @FXML public MFXButton formatButton;
    @FXML public MFXButton generateButton;
    @FXML public MFXButton copyButton;
    @FXML public MFXButton clearButton;
    
    // 输出
    @FXML public TextArea outputArea;
}
