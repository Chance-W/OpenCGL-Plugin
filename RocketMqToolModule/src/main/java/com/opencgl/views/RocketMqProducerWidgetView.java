package com.opencgl.views;


import com.opencgl.base.controls.CustomTextArea;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/**
 * @author Chance.W
 */
public class RocketMqProducerWidgetView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected StackPane contentPanel;
    @FXML
    protected MFXButton sendButton;
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
    protected MFXTextField tagsTextField;
    @FXML
    protected MFXTextField countTextField;
    @FXML
    protected CustomTextArea inputTextArea;
    @FXML
    protected CustomTextArea outputTextArea;

}
