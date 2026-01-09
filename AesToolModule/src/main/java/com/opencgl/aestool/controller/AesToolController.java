package com.opencgl.aestool.controller;

import com.opencgl.aestool.i18n.I18N;
import com.opencgl.aestool.service.AesService;
import com.opencgl.aestool.views.AesToolView;
import javafx.fxml.Initializable;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * AES工具控制器
 */
public class AesToolController extends AesToolView implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AesToolController.class);
    private final AesService aesService = new AesService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encryptButton.setOnAction(e -> performEncrypt());
        decryptButton.setOnAction(e -> performDecrypt());
        
        // 监听语言切换
        com.opencgl.base.utils.i18n.BaseI18N.localeProperty().addListener((obs, oldLocale, newLocale) -> {
            // 对于已经 Bind 的属性，语言切换时会自动触发刷新，无需手动 updateI18n
        });

        // 绑定文本属性实现实时多语言切换
        titleLabel.textProperty().bind(I18N.getBinding("label.title"));
        aesKeyLabel.textProperty().bind(I18N.getBinding("label.aes_key"));
        aesIvLabel.textProperty().bind(I18N.getBinding("label.iv"));
        plainLabel.textProperty().bind(I18N.getBinding("label.plain"));
        cipherLabel.textProperty().bind(I18N.getBinding("label.cipher"));

        aesKeyField.promptTextProperty().bind(I18N.getBinding("prompt.aes_key"));
        aesIvField.promptTextProperty().bind(I18N.getBinding("prompt.iv"));
        plaintextArea.promptTextProperty().bind(I18N.getBinding("prompt.plain"));
        ciphertextArea.promptTextProperty().bind(I18N.getBinding("prompt.cipher"));

        encryptButton.textProperty().bind(I18N.getBinding("button.encrypt"));
        decryptButton.textProperty().bind(I18N.getBinding("button.decrypt"));
    }

    private void updateI18n() {
        // 由于使用了 bind()，此方法不再需要手动调用来刷新文本
    }

    private void performEncrypt() {
        String plaintext = plaintextArea.getText();
        if (plaintext == null || plaintext.trim().isEmpty()) {
            showWarning(I18N.get("msg.enter_plain"));
            return;
        }
        String key = aesKeyField.getText();
        if (key == null || key.trim().isEmpty()) {
            showWarning(I18N.get("msg.enter_key"));
            return;
        }
        String iv = aesIvField.getText();

        try {
            String encrypted = aesService.encrypt(plaintext, key, iv);
            ciphertextArea.setText(encrypted);
            showToast(I18N.get("msg.encrypt_success"), "#4CAF50");
        } catch (Exception e) {
            showError(I18N.get("msg.encrypt_failed", e.getMessage()));
        }
    }

    private void performDecrypt() {
        String ciphertext = ciphertextArea.getText();
        if (ciphertext == null || ciphertext.trim().isEmpty()) {
            showWarning(I18N.get("msg.enter_cipher"));
            return;
        }
        String key = aesKeyField.getText();
        if (key == null || key.trim().isEmpty()) {
            showWarning(I18N.get("msg.enter_key"));
            return;
        }
        String iv = aesIvField.getText();

        try {
            String decrypted = aesService.decrypt(ciphertext.trim(), key, iv);
            plaintextArea.setText(decrypted);
            showToast(I18N.get("msg.decrypt_success"), "#4CAF50");
        } catch (Exception e) {
            showError(I18N.get("msg.decrypt_failed", e.getMessage()));
        }
    }

    private void copyToClipboard(String content, String name) {
        if (content == null || content.isEmpty()) {
            showWarning(I18N.get("msg.no_copy_content"));
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
        label.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-padding: 12 24; -fx-background-radius: 8; -fx-font-weight: bold;");
        StackPane.setAlignment(label, javafx.geometry.Pos.TOP_CENTER);
        StackPane.setMargin(label, new javafx.geometry.Insets(20, 0, 0, 0));
        label.setOpacity(0);
        rootPane.getChildren().add(label);

        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300),
                label);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300),
                label);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> rootPane.getChildren().remove(label));
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> fadeOut.play());
        fadeIn.setOnFinished(e -> pause.play());
        fadeIn.play();
    }

    private void applyCurrentTheme(javafx.scene.control.Dialog<?> dialog) {
        if (dialog == null) return;
        try {
            java.util.List<String> sheets = com.opencgl.base.theme.ThemeManager.getInstance().getCurrentThemeStylesheets();
            javafx.scene.layout.Region root = (javafx.scene.layout.Region) dialog.getDialogPane().getContent();
            if (root != null) {
                root.getStylesheets().setAll(sheets);
            }
            dialog.getDialogPane().getStylesheets().setAll(sheets);
        } catch (Exception e) {
            // ignore
        }
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18N.get("title.warning"));
        alert.setHeaderText(null);
        alert.setContentText(msg);
        applyCurrentTheme(alert);
        alert.showAndWait();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18N.get("title.error"));
        alert.setHeaderText(null);
        alert.setContentText(msg);
        applyCurrentTheme(alert);
        alert.showAndWait();
    }
}
