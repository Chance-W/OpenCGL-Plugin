package com.opencgl.jsonyaml.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class JsonYamlFormatView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected Label titleLabel;
    @FXML
    protected TabPane typeTabPane;
    @FXML
    protected Tab jsonTab;
    @FXML
    protected Tab yamlTab;
    @FXML
    protected Label inputLabel;
    @FXML
    protected Label outputLabel;
    @FXML
    protected TextArea inputArea;
    @FXML
    protected TextArea outputArea;
    @FXML
    protected MFXButton validateButton;
    @FXML
    protected MFXButton formatButton;
    @FXML
    protected MFXButton compressButton;
    @FXML
    protected MFXButton clearButton;
    @FXML
    protected Label statusLabel;
}
