package com.opencgl.template.views;

import com.opencgl.template.model.Person;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXFilterComboBox;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/**
 * @author Chance.W
 * @version 9.0
 * @className TemplateView
 * @description TODO
 * @date 2022/8/10 8:46
 */
public class TemplateWidgetView {

    @FXML
    public StackPane rootPane;

    @FXML
    public MFXButton testButton4;

    public MFXButton testButton5;

    @FXML
    public Button testButton3;

    @FXML
    public Button testButton2;

    @FXML
    public Button testButton;

    @FXML
    public TextArea testArea;

    @FXML
    public BorderPane contentBorderPane;

    @FXML
    public MFXFilterComboBox<Person>  filterCombo;
}
