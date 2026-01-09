package com.opencgl.aestool.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

/**
 * AES工具视图
 */
public class AesToolView {
    @FXML public StackPane rootPane;
    @FXML public Label titleLabel;
    @FXML public Label aesKeyLabel;
    @FXML public Label aesIvLabel;
    @FXML public Label plainLabel;
    @FXML public Label cipherLabel;
    @FXML public TextField aesKeyField;
    @FXML public TextField aesIvField;
    @FXML public TextArea plaintextArea;
    @FXML public TextArea ciphertextArea;
    @FXML public MFXButton encryptButton;
    @FXML public MFXButton decryptButton;
  //  @FXML public MFXButton copyButton;
}
