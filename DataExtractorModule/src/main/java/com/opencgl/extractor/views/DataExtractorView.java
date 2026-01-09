package com.opencgl.extractor.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class DataExtractorView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    
    @FXML
    protected Label titleLabel;
    @FXML
    protected Label dataTypeLabel;
    @FXML
    protected MFXComboBox<String> dataTypeComboBox;
    @FXML
    protected VBox inputDataContainer;
    @FXML
    protected Label inputDataLabel;
    @FXML
    protected MFXTextField expressionField;
    @FXML
    protected Label expressionLabel;
    @FXML
    protected MFXButton extractButton;
    @FXML
    protected MFXButton clearButton;
    @FXML
    protected Label resultLabel;
    @FXML
    protected ListView<String> templatesList;
    @FXML
    protected Label templatesLabel;
    @FXML
    protected Label doubleClickLabel;
    @FXML
    protected TextArea resultArea;
    
    @FXML
    protected Label statusLabel;
}
