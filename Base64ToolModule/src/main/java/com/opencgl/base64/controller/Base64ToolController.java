package com.opencgl.base64.controller;

import com.opencgl.base64.i18n.I18N;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.Base64;

public class Base64ToolController implements Initializable {

    @FXML private TextArea inputArea;
    @FXML private TextArea outputArea;
    @FXML private Button btnEncodeBase64;
    @FXML private Button btnDecodeBase64;
    @FXML private Button btnUrlEncode;
    @FXML private Button btnUrlDecode;
    @FXML private Button btnToggleMultiline;

    private boolean multiLine = true;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initI18n();
    }

    private void initI18n() {
        // Static texts are in FXML with %key; runtime messages use I18N.get()
    }

    @FXML
    private void onEncodeBase64() {
        String text = inputArea.getText();
        try {
            String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            if (!multiLine && encoded.length() > 76) {
                encoded = encoded.replaceAll("(?<=\\G.{76})", "\n");
            }
            outputArea.setText(encoded);
        } catch (Exception e) {
            outputArea.setText(I18N.getOrDefault("msg.error", "Error: ") + e.getMessage());
        }
    }

    @FXML
    private void onDecodeBase64() {
        String text = inputArea.getText().replaceAll("\\s+", "");
        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            outputArea.setText(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            outputArea.setText(I18N.getOrDefault("msg.error", "Error: ") + e.getMessage());
        }
    }

    @FXML
    private void onUrlEncode() {
        String text = inputArea.getText();
        try {
            String encoded = java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
            outputArea.setText(encoded);
        } catch (Exception e) {
            outputArea.setText(I18N.getOrDefault("msg.error", "Error: ") + e.getMessage());
        }
    }

    @FXML
    private void onUrlDecode() {
        String text = inputArea.getText();
        try {
            String decoded = java.net.URLDecoder.decode(text, StandardCharsets.UTF_8);
            outputArea.setText(decoded);
        } catch (Exception e) {
            outputArea.setText(I18N.getOrDefault("msg.error", "Error: ") + e.getMessage());
        }
    }

    @FXML
    private void onToggleMultiline() {
        multiLine = !multiLine;
        inputArea.setWrapText(multiLine);
        outputArea.setWrapText(multiLine);
    }
}
