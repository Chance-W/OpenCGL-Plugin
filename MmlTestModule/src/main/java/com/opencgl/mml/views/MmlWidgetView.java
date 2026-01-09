package com.opencgl.mml.views;

import com.opencgl.base.controls.CustomTextArea;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class MmlWidgetView {
    @FXML
    protected StackPane mainStackPane;

    @FXML
    protected StackPane contentStackPane;

    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected MFXButton sendButton;
    @FXML
    protected MFXButton refreshButton;
    @FXML
    protected MFXButton saveButton;
    @FXML
    protected MFXButton copyButton;
    @FXML
    protected MFXTextField userTextField;
    @FXML
    protected MFXTextField passTextField;
    @FXML
    protected MFXTextField ipTextField;
    @FXML
    protected MFXTextField portTextField;
    @FXML
    protected CustomTextArea outputTextArea;
    @FXML
    protected CustomTextArea inputTextArea;
    @FXML
    protected VBox operateTopVbox;
}
