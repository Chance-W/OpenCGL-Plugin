package com.opencgl.textescape.controller;

import com.opencgl.textescape.i18n.I18N;
import com.opencgl.textescape.service.TextProcessService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.net.URL;
import java.util.ResourceBundle;

public class TextEscapeController implements Initializable {

    @FXML private TextArea inputArea;
    @FXML private TextArea outputArea;
    @FXML private Label statusLabel;
    
    private final TextProcessService service = new TextProcessService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
    }

    private void initI18n() {
        statusLabel.setText(I18N.get("label.statusReady"));
    }

    @FXML private void onJsonFormat() { process(service::formatDateJson, "action.jsonFormat"); }
    @FXML private void onJsonCompress() { process(service::compressJson, "action.jsonCompress"); }
    @FXML private void onJsonEscape() { process(service::escapeJson, "action.jsonEscape"); }
    @FXML private void onJsonUnescape() { process(service::unescapeJson, "action.jsonUnescape"); }
    @FXML private void onHtmlEscape() { process(service::escapeHtml, "action.htmlEscape"); }
    @FXML private void onHtmlUnescape() { process(service::unescapeHtml, "action.htmlUnescape"); }
    @FXML private void onXmlEscape() { process(service::escapeXml, "action.xmlEscape"); }
    @FXML private void onXmlUnescape() { process(service::unescapeXml, "action.xmlUnescape"); }
    @FXML private void onUrlEncode() { process(service::encodeUrl, "action.urlEncode"); }
    @FXML private void onUrlDecode() { process(service::decodeUrl, "action.urlDecode"); }
    @FXML private void onJavaEscape() { process(service::escapeJava, "action.javaEscape"); }
    @FXML private void onJavaUnescape() { process(service::unescapeJava, "action.javaUnescape"); }
    @FXML private void onEscapeNewlines() { process(service::escapeNewlines, "action.escapeNewlines"); }
    @FXML private void onUnescapeNewlines() { process(service::unescapeNewlines, "action.unescapeNewlines"); }
    @FXML private void onRemoveNewlines() { process(service::removeNewlines, "action.removeNewlines"); }

    @FXML private void onExchange() {
        String out = outputArea.getText();
        if (out != null && !out.isEmpty()) {
            inputArea.setText(out);
            outputArea.clear();
            statusLabel.setText(I18N.get("status.exchanged"));
        }
    }
    
    @FXML private void onCopyResult() {
        String content = outputArea.getText();
        if (content != null && !content.isEmpty()) {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(content);
            Clipboard.getSystemClipboard().setContent(cc);
            statusLabel.setText(I18N.get("status.copied"));
        }
    }
    
    @FXML private void onClear() {
        inputArea.clear();
        outputArea.clear();
        statusLabel.setText(I18N.get("status.cleared"));
    }

    private void process(java.util.function.UnaryOperator<String> func, String actionKey) {
        String input = inputArea.getText();
        if (input == null || input.isEmpty()) {
            statusLabel.setText(I18N.get("status.pleaseInput"));
            return;
        }
        try {
            String result = func.apply(input);
            outputArea.setText(result);
            statusLabel.setText(I18N.get("status.success", I18N.get(actionKey)));
        } catch (Exception e) {
            outputArea.setText(I18N.get("title.error") + ": " + e.getMessage());
            statusLabel.setText(I18N.get("status.failed", I18N.get(actionKey)));
        }
    }
}
