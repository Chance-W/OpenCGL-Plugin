package com.opencgl.uuid.controller;

import com.opencgl.uuid.i18n.I18N;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UuidGeneratorController implements Initializable {

    @FXML private TextField countField;
    @FXML private TextArea resultArea;
    @FXML private javafx.scene.control.Button btnGenerate;
    @FXML private javafx.scene.control.Button btnCopy;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initI18n();
        countField.setText("1");
    }

    private void initI18n() {
        // Static texts are in FXML with %key; runtime messages use I18N.get()
    }

    @FXML
    private void onGenerate() {
        int count = 1;
        try {
            String raw = countField.getText();
            if (raw != null && !raw.isBlank()) {
                count = Integer.parseInt(raw.trim());
            }
            if (count < 1) count = 1;
            if (count > 1000) count = 1000;
        } catch (NumberFormatException e) {
            count = 1;
        }
        String uuids = IntStream.range(0, count)
                .mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining("\n"));
        resultArea.setText(uuids);
    }

    @FXML
    private void onCopy() {
        String text = resultArea.getText();
        if (text != null && !text.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
        }
    }
}
