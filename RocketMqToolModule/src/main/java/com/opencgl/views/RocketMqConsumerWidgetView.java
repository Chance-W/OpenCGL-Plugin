package com.opencgl.views;


import com.opencgl.base.controls.CustomTextArea;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * @author Chance.W
 */
public class RocketMqConsumerWidgetView {

    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected VBox headerVbox;
    @FXML
    protected MFXButton sendButton;
    @FXML
    protected MFXButton closeButton;
    @FXML
    protected MFXButton refreshButton;
    @FXML
    protected MFXButton saveButton;
    @FXML
    protected MFXButton copyButton;
    @FXML
    protected MFXTextField nameServerAddrTextField;
    @FXML
    protected MFXTextField topicTextField;
    @FXML
    protected CustomTextArea outputTextArea;
    @FXML
    protected HBox processBarHBox;
}
