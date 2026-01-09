package com.opencgl.jwt.controller;

import com.opencgl.jwt.i18n.I18N;
import com.opencgl.jwt.service.JWTService;
import com.opencgl.jwt.service.JWTService.JWTDecodeResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * JWT 工具控制器
 *
 * @author OpenCGL
 */
public class JWTToolController implements Initializable {

    @FXML private TextArea tokenInput;
    @FXML private TextField secretField;
    @FXML private ComboBox<String> algorithmCombo;
    @FXML private TextArea headerOutput;
    @FXML private TextArea payloadOutput;
    @FXML private Label statusLabel;
    @FXML private Tab decodeTab;
    @FXML private Tab generateTab;
    
    // 生成 Tab 控件
    @FXML private TextArea claimsInput;
    @FXML private TextField genSecretField;
    @FXML private TextField expireMinutesField;
    @FXML private TextArea generatedTokenArea;

    private final JWTService jwtService = new JWTService();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        algorithmCombo.getItems().addAll("HS256", "HS384", "HS512");
        algorithmCombo.setValue("HS256");
        
        // 实时解码
        tokenInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                decodeOnly();
            }
        });
    }

    @FXML
    private void onDecode() {
        decodeOnly();
    }

    private void decodeOnly() {
        String token = tokenInput.getText();
        if (token == null || token.isEmpty()) {
            headerOutput.clear();
            payloadOutput.clear();
            statusLabel.setText("");
            return;
        }

        try {
            JWTDecodeResult result = jwtService.decode(token.trim());
            headerOutput.setText(formatJson(result.header()));
            payloadOutput.setText(formatJson(result.payload()));
            statusLabel.setText(I18N.get("msg.decoded_ok"));
            statusLabel.setStyle("-fx-text-fill: #ffc107;");
        } catch (Exception e) {
            headerOutput.setText(I18N.get("msg.decode_fail"));
            payloadOutput.setText(e.getMessage());
            statusLabel.setText(I18N.get("msg.error"));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    @FXML
    private void onVerify() {
        String token = tokenInput.getText();
        String secret = secretField.getText();
        
        if (token == null || token.isEmpty()) {
            statusLabel.setText(I18N.get("msg.enter_token"));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
            return;
        }
        
        if (secret == null || secret.isEmpty()) {
            statusLabel.setText(I18N.get("msg.enter_secret"));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
            return;
        }

        try {
            JWTDecodeResult result = jwtService.verifyAndDecode(token.trim(), secret, algorithmCombo.getValue());
            headerOutput.setText(formatJson(result.header()));
            payloadOutput.setText(formatJson(result.payload()));
            
            if (result.status().contains("VALID")) {
                statusLabel.setText("✓ " + result.status());
                statusLabel.setStyle("-fx-text-fill: #4caf50;");
            } else if (result.status().contains("EXPIRED")) {
                statusLabel.setText(I18N.get("msg.token_expired"));
                statusLabel.setStyle("-fx-text-fill: #ff9800;");
            } else {
                statusLabel.setText("✗ " + result.status());
                statusLabel.setStyle("-fx-text-fill: #f44336;");
            }
        } catch (Exception e) {
            statusLabel.setText(I18N.get("msg.verify_fail", e.getMessage()));
            statusLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    @FXML
    private void onGenerate() {
        String secret = genSecretField.getText();
        String claimsText = claimsInput.getText();
        String expireStr = expireMinutesField.getText();
        
        if (secret == null || secret.length() < 32) {
            generatedTokenArea.setText(I18N.get("msg.secret_min_len"));
            return;
        }

        try {
            Map<String, Object> claims = parseClaimsJson(claimsText);
            long expireMinutes = expireStr.isEmpty() ? 60 : Long.parseLong(expireStr);
            
            String token = jwtService.generate(claims, secret, algorithmCombo.getValue(), expireMinutes);
            generatedTokenArea.setText(token);
        } catch (Exception e) {
            generatedTokenArea.setText(I18N.get("msg.gen_fail", e.getMessage()));
        }
    }

    @FXML
    private void onCopyToken() {
        String token = generatedTokenArea.getText();
        if (token != null && !token.isEmpty()) {
            copyToClipboard(token);
        }
    }

    private Map<String, Object> parseClaimsJson(String json) {
        Map<String, Object> claims = new HashMap<>();
        if (json == null || json.isEmpty()) {
            claims.put("sub", "user");
            return claims;
        }
        
        // 简单解析 key: value 格式
        String[] lines = json.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;
            
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0].trim().replaceAll("^\"|\"$", "");
                String value = parts[1].trim().replaceAll("^\"|\"$|,$", "");
                claims.put(key, value);
            }
        }
        return claims;
    }

    private String formatJson(String json) {
        // 简单格式化
        return json.replace(",", ",\n").replace("{", "{\n").replace("}", "\n}");
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }
}
