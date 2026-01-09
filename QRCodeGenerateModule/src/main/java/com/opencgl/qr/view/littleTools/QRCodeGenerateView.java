package com.opencgl.qr.view.littleTools;

import com.opencgl.qr.utils.CorrectionLevel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

@SuppressWarnings("unused")
public abstract class QRCodeGenerateView implements Initializable {

    @FXML
    protected AnchorPane mainPane;

    @FXML
    protected TitledPane contentTitledPane;
    @FXML
    protected TitledPane basicTitledPane;
    @FXML
    protected TitledPane logoTitledPane;

    @FXML
    protected Label foregroundLabel;
    @FXML
    protected Label backgroundLabel;
    @FXML
    protected Label errorCorrectionLabel;
    @FXML
    protected Label marginLabel;
    @FXML
    protected Label encodingLabel;
    @FXML
    protected Label imageFormatLabel;
    @FXML
    protected Label logoOverlayLabel;

    @FXML
    protected Button builderButton;

    @FXML
    protected TextArea contentTextField;

    @FXML
    protected ChoiceBox<String> codeFormatChoiceBox;

    @FXML
    protected ImageView codeImageView1;

    @FXML
    protected ImageView codeImageView2;

    @FXML
    protected ColorPicker onColorColorPicker;

    @FXML
    protected ColorPicker offColorColorPicker;

    @FXML
    protected ChoiceBox<CorrectionLevel> errorCorrectionLevelChoiceBox;

    @FXML
    protected Button saveButton;

    @FXML
    protected Button logoButton;

    @FXML
    protected ChoiceBox<Integer> marginChoiceBox;

    @FXML
    protected ChoiceBox<String> formatImageChoiceBox;

    @FXML
    protected Slider logoSlider;

    @FXML
    protected HBox codeImageViewHBox;
}
