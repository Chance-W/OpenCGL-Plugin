package com.opencgl.curl.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CurlToRequestView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected TextArea curlInputArea;
    @FXML
    protected MFXButton parseButton;
    @FXML
    protected MFXButton sendButton;
    @FXML
    protected MFXButton clearButton;
    @FXML
    protected TextArea parsedResultArea;
    @FXML
    protected TextArea responseArea;
    @FXML
    protected Label statusLabel;
    @FXML
    protected Label curlInputLabel;
    @FXML
    protected Label parsedResultLabel;
    @FXML
    protected Label responseLabel;
}
