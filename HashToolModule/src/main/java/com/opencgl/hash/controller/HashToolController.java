package com.opencgl.hash.controller;

import com.opencgl.hash.i18n.I18N;
import com.opencgl.hash.service.HashService;
import com.opencgl.hash.service.HashService.Algorithm;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 哈希工具控制器
 *
 * @author OpenCGL
 */
public class HashToolController implements Initializable {

    @FXML private ComboBox<String> algorithmCombo;
    @FXML private ToggleGroup inputTypeGroup;
    @FXML private RadioButton textRadio;
    @FXML private RadioButton fileRadio;
    @FXML private TextArea inputTextArea;
    @FXML private javafx.scene.layout.HBox fileInputBox;
    @FXML private TextField filePathField;
    @FXML private Button calculateBtn;
    @FXML private TextArea resultArea;
    @FXML private TextField verifyField;
    @FXML private Label verifyResultLabel;
    @FXML private CheckBox upperCaseCheck;

    private final HashService hashService = new HashService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-hash-tool");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // 初始化算法下拉框
        algorithmCombo.getItems().addAll("MD5", "SHA-1", "SHA-256", "SHA-512", "CRC32");
        algorithmCombo.setValue("MD5");

        // 输入类型切换
        textRadio.setSelected(true);
        updateInputMode();
        
        inputTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> updateInputMode());

        // 大写选项变化时重新计算
        upperCaseCheck.selectedProperty().addListener((obs, oldVal, newVal) -> updateResultCase());
    }

    private void updateInputMode() {
        boolean isText = textRadio.isSelected();
        inputTextArea.setVisible(isText);
        inputTextArea.setManaged(isText);
        fileInputBox.setVisible(!isText);
        fileInputBox.setManaged(!isText);
    }

    @FXML
    private void onChooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("title.choose_file"));
        File file = fileChooser.showOpenDialog(fileInputBox.getScene().getWindow());
        if (file != null) {
            filePathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onCalculate() {
        Algorithm algorithm = getSelectedAlgorithm();
        
        calculateBtn.setDisable(true);
        resultArea.setText(I18N.get("msg.calculating"));

        CompletableFuture.supplyAsync(() -> {
            try {
                if (textRadio.isSelected()) {
                    return hashService.hashText(inputTextArea.getText(), algorithm);
                } else {
                    return hashService.hashFile(filePathField.getText(), algorithm);
                }
            } catch (Exception e) {
                return I18N.get("msg.error_prefix") + e.getMessage();
            }
        }, executor).thenAccept(result -> Platform.runLater(() -> {
            if (disposed) return;
            resultArea.setText(upperCaseCheck.isSelected() ? result.toUpperCase() : result.toLowerCase());
            calculateBtn.setDisable(false);
            verifyHash();
        }));
    }

    @FXML
    private void onClear() {
        inputTextArea.clear();
        filePathField.clear();
        resultArea.clear();
        verifyField.clear();
        verifyResultLabel.setText("");
    }

    @FXML
    private void onCopy() {
        String result = resultArea.getText();
        if (result != null && !result.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(result);
            clipboard.setContent(content);
        }
    }

    private void verifyHash() {
        String expected = verifyField.getText();
        String actual = resultArea.getText();
        
        if (expected == null || expected.isEmpty() || actual == null || actual.isEmpty()) {
            verifyResultLabel.setText("");
            return;
        }
        
        if (actual.equalsIgnoreCase(expected)) {
            verifyResultLabel.setText(I18N.get("msg.match"));
            verifyResultLabel.setStyle("-fx-text-fill: green;");
        } else {
            verifyResultLabel.setText(I18N.get("msg.no_match"));
            verifyResultLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void updateResultCase() {
        String result = resultArea.getText();
        if (result != null && !result.isEmpty() && !result.startsWith(I18N.get("msg.error_prefix")) && !result.equals(I18N.get("msg.calculating"))) {
            resultArea.setText(upperCaseCheck.isSelected() ? result.toUpperCase() : result.toLowerCase());
        }
    }

    private Algorithm getSelectedAlgorithm() {
        String selected = algorithmCombo.getValue();
        return switch (selected) {
            case "SHA-1" -> Algorithm.SHA1;
            case "SHA-256" -> Algorithm.SHA256;
            case "SHA-512" -> Algorithm.SHA512;
            case "CRC32" -> Algorithm.CRC32;
            default -> Algorithm.MD5;
        };
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        executor.shutdownNow();
    }
}
