package com.opencgl.views;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextArea;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class SoapWidgetView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected VBox operateTopVbox;
    @FXML
    protected JFXButton sendButton;
    @FXML
    protected JFXButton refreshButton;
    @FXML
    protected JFXButton copyButton;
    @FXML
    protected JFXButton saveButton;
    @FXML
    protected JFXButton settingButton;
    @FXML
    protected JFXComboBox<Object> chooseIntComboBox;
    @FXML
    protected JFXTextArea inputTextArea;
    @FXML
    protected JFXTextArea outputTextArea;
}
