package com.opencgl.rsatool.controller;

import com.opencgl.rsatool.i18n.I18N;
import com.opencgl.rsatool.service.RsaKeyService;
import com.opencgl.rsatool.views.RsaToolView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * RSA工具控制器
 */
public class RsaToolController extends RsaToolView implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RsaToolController.class);
    
    private final RsaKeyService rsaKeyService = new RsaKeyService();
    private String currentPublicKey;
    private String currentPrivateKey;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rsa-tool-key-generator");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> keyGenerationFuture;
    private volatile boolean disposed;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initComboBoxes();
        bindEvents();
        initI18n();
    }

    private void initI18n() {
    }

    private void initComboBoxes() {
        keySizeCombo.getItems().addAll("1024", "2048", "3072", "4096");
        keySizeCombo.selectFirst();
        
        algorithmCombo.getItems().addAll("RSA-OAEP", "RSA/ECB/PKCS1Padding");
        algorithmCombo.selectFirst();
        
        hashAlgorithmCombo.getItems().addAll("SHA-256", "SHA-384", "SHA-512");
        hashAlgorithmCombo.selectFirst();
    }

    private void bindEvents() {
        generateButton.setOnAction(e -> generateKeyPair());
        copyPublicKeyButton.setOnAction(e -> copyToClipboard(currentPublicKey, I18N.get("name.publicKey")));
        copyPrivateKeyButton.setOnAction(e -> copyToClipboard(currentPrivateKey, I18N.get("name.privateKey")));
        encryptButton.setOnAction(e -> performEncrypt());
        decryptButton.setOnAction(e -> performDecrypt());
        signButton.setOnAction(e -> performSign());
        verifyButton.setOnAction(e -> performVerify());
    }

    private int getSelectedKeySize() {
        String selected = keySizeCombo.getValue();
        return selected != null ? Integer.parseInt(selected) : 2048;
    }

    private void generateKeyPair() {
        int keySize = getSelectedKeySize();
        keyGenerationFuture = backgroundExecutor.submit(() -> {
            try {
                Map<String, String> keyPair = rsaKeyService.generateKeyPair(keySize, 65537);
                currentPublicKey = keyPair.get("publicKey");
                currentPrivateKey = keyPair.get("privateKey");

                Platform.runLater(() -> {
                    if (disposed) return;
                    boolean removePem = removePemHeaderCheckbox != null && removePemHeaderCheckbox.isSelected();
                    if (removePem) {
                        publicKeyArea.setText(removePemHeaders(currentPublicKey));
                        privateKeyArea.setText(removePemHeaders(currentPrivateKey));
                    } else {
                        publicKeyArea.setText(currentPublicKey);
                        privateKeyArea.setText(currentPrivateKey);
                    }
                    showToast(I18N.get("msg.keyGenSuccess"), "#4CAF50");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!disposed) showError(I18N.get("title.error") + ": " + e.getMessage());
                });
            }
        });
    }

    private String removePemHeaders(String pem) {
        if (pem == null) return null;
        return pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                  .replaceAll("-----END [A-Z ]+-----", "")
                  .replaceAll("\\s+", "");
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (keyGenerationFuture != null) keyGenerationFuture.cancel(true);
        backgroundExecutor.shutdownNow();
    }

    private String normalizeKey(String key, String keyType) {
        if (key == null || key.trim().isEmpty()) return null;
        key = key.trim();
        if (key.contains("-----BEGIN")) return key;
        
        String header = "PUBLIC".equals(keyType) ? "-----BEGIN PUBLIC KEY-----" : "-----BEGIN PRIVATE KEY-----";
        String footer = "PUBLIC".equals(keyType) ? "-----END PUBLIC KEY-----" : "-----END PRIVATE KEY-----";
        
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n");
        String base64 = key.replaceAll("\\s+", "");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        sb.append(footer);
        return sb.toString();
    }

    private void performEncrypt() {
        String plaintext = plaintextArea.getText();
        if (plaintext == null || plaintext.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputPlain"));
            return;
        }
        String publicKey = normalizeKey(encryptPublicKeyArea.getText(), "PUBLIC");
        if (publicKey == null) {
            showWarning(I18N.get("msg.pleaseInputPublic"));
            return;
        }
        try {
            String encrypted = rsaKeyService.encrypt(plaintext.trim(), publicKey);
            ciphertextArea.setText(encrypted);
            showToast(I18N.get("msg.encryptSuccess"), "#4CAF50");
        } catch (Exception e) {
            showError(I18N.get("msg.encryptFailed", e.getMessage()));
        }
    }

    private void performDecrypt() {
        String ciphertext = ciphertextArea.getText();
        if (ciphertext == null || ciphertext.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputCipher"));
            return;
        }
        String privateKey = normalizeKey(encryptPrivateKeyArea.getText(), "PRIVATE");
        if (privateKey == null) {
            showWarning(I18N.get("msg.pleaseInputPrivate"));
            return;
        }
        try {
            String decrypted = rsaKeyService.decrypt(ciphertext.trim(), privateKey);
            plaintextArea.setText(decrypted);
            showToast(I18N.get("msg.decryptSuccess"), "#4CAF50");
        } catch (Exception e) {
            showError(I18N.get("msg.decryptFailed", e.getMessage()));
        }
    }

    private void performSign() {
        String data = signDataArea.getText();
        if (data == null || data.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputData"));
            return;
        }
        String privateKey = normalizeKey(signPrivateKeyArea.getText(), "PRIVATE");
        if (privateKey == null) {
            showWarning(I18N.get("msg.pleaseInputPrivate"));
            return;
        }
        try {
            String signature = rsaKeyService.sign(data.trim(), privateKey);
            signatureArea.setText(signature);
            showToast(I18N.get("msg.signSuccess"), "#4CAF50");
        } catch (Exception e) {
            showError(I18N.get("msg.signFailed", e.getMessage()));
        }
    }

    private void performVerify() {
        String data = signDataArea.getText();
        String signature = signatureArea.getText();
        if (data == null || data.trim().isEmpty() || signature == null || signature.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputDataAndSign"));
            return;
        }
        String publicKey = normalizeKey(signPublicKeyArea.getText(), "PUBLIC");
        if (publicKey == null) {
            showWarning(I18N.get("msg.pleaseInputPublic"));
            return;
        }
        try {
            boolean valid = rsaKeyService.verify(data.trim(), signature.trim(), publicKey);
            if (valid) {
                verifyResultField.setText(I18N.get("result.verifyOk"));
                verifyResultField.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                showToast(I18N.get("msg.verifyValid"), "#4CAF50");
            } else {
                verifyResultField.setText(I18N.get("result.verifyFail"));
                verifyResultField.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }
        } catch (Exception e) {
            verifyResultField.setText(I18N.get("result.verifyError"));
            showError(I18N.get("msg.signFailed", e.getMessage()));
        }
    }

    private void copyToClipboard(String content, String name) {
        if (content == null || content.isEmpty()) {
            showWarning(I18N.get("msg.noContentToCopy"));
            return;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent cc = new ClipboardContent();
        cc.putString(content);
        clipboard.setContent(cc);
        showToast(I18N.get("msg.copied", name), "#4CAF50");
    }

    private void showToast(String message, String color) {
        javafx.scene.control.Label label = new javafx.scene.control.Label("✅ " + message);
        label.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-padding: 12 24; -fx-background-radius: 8; -fx-font-weight: bold;");
        StackPane.setAlignment(label, javafx.geometry.Pos.TOP_CENTER);
        StackPane.setMargin(label, new javafx.geometry.Insets(20, 0, 0, 0));
        label.setOpacity(0);
        rootPane.getChildren().add(label);
        
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), label);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), label);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> rootPane.getChildren().remove(label));
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> fadeOut.play());
        fadeIn.setOnFinished(e -> pause.play());
        fadeIn.play();
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18N.get("title.warning")); alert.setHeaderText(null); alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18N.get("title.error")); alert.setHeaderText(null); alert.setContentText(msg);
        alert.showAndWait();
    }
}
