package com.opencgl.colorpicker.controller;

import com.opencgl.colorpicker.i18n.I18N;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;

import java.net.URL;
import java.util.ResourceBundle;

public class ColorPickerController implements Initializable {

    @FXML
    private ColorPicker colorPicker;
    @FXML
    private Label hexRgbLabel;
    @FXML
    private javafx.scene.control.Button btnCopyHex;
    @FXML
    private javafx.scene.control.Button btnCopyRgb;

    private String lastHex = "#000000";
    private String lastRgb = "rgb(0, 0, 0)";

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initI18n();
        updateFromColor(colorPicker.getValue());
        colorPicker.valueProperty().addListener((obs, oldVal, newVal) -> updateFromColor(newVal));
    }

    private void initI18n() {
        // Static texts are in FXML with %key; runtime messages use I18N.get()
    }

    private void updateFromColor(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        lastHex = String.format("#%02X%02X%02X", r, g, b);
        lastRgb = String.format("rgb(%d, %d, %d)", r, g, b);
        hexRgbLabel.setText(lastHex + "  |  " + lastRgb);
    }

    @FXML
    private void onCopyHex() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(lastHex);
        clipboard.setContent(content);
    }

    @FXML
    private void onCopyRgb() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(lastRgb);
        clipboard.setContent(content);
    }
}
