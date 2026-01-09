package com.opencgl.variable.controller;

import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.variable.i18n.I18N;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;

public class VariableGeneratorWidgetController implements Initializable {
    @FXML private StackPane rootPane;
    @FXML private TextArea inputArea;
    @FXML private TextArea outputArea;
    @FXML private MFXButton generateBtn;
    @FXML private MFXButton copyBtn;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        inputArea.setText(
            "${UUID}\n" +
            "${UUID_SIMPLE}\n" +
            "${UUID_UPPER}\n" +
            "${TIMESTAMP}\n" +
            "${TIMESTAMP_S}\n" +
            "${DATE}\n" +
            "${Random}\n" +
            "${Random6}\n" +
            "${Random19}\n" +
            "${yyyy-MM-dd HH:mm:ss}\n" +
            "${yyyyMMddHHmmss}\n" +
            "${yyyyMMdd}\n" +
            "${yyyy-MM-dd}\n" +
            "${HH:mm:ss}\n" +
            "${yyyy/MM/dd}"
        );
    }

    private void initI18n() {
    }

    @FXML
    public void onGenerate() {
        String input = inputArea.getText();
        if (input == null) input = "";
        String result = FormatVariableUtil.format(input);
        outputArea.setText(result);
    }

    @FXML
    public void onCopy() {
        String content = outputArea.getText();
        if (content != null && !content.isEmpty()) {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            clipboard.setContent(clipboardContent);
            TooltipUtil.showToast(rootPane, I18N.get("msg.copySuccess"));
        } else {
             TooltipUtil.showToast(rootPane, I18N.get("msg.noContent"));
        }
    }
}
