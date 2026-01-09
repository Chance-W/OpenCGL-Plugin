package com.opencgl.jsonxml.views;


import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class JsonXmlFormatWidgetView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected Label titleLabel;
    @FXML
    protected Label inputLabel;
    @FXML
    protected Label outputLabel;
    @FXML
    protected MFXButton formatButton;
    @FXML
    protected MFXButton compressButton;
    @FXML
    protected MFXButton clearButton;
    @FXML
    protected MFXButton swapButton;
    @FXML
    protected MFXButton copyButton;
    @FXML
    protected MFXComboBox<String> formatTypeComboBox;
    @FXML
    protected VBox inputVBox;
    @FXML
    protected VBox outputVBox;
    @FXML
    protected VBox operateTopVbox;
    @FXML
    protected Label statusLabel;
}
