package com.opencgl.regex.controller;

import com.opencgl.base.utils.DialogUtil;
import com.opencgl.regex.i18n.I18N;
import com.opencgl.regex.service.RegexService;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class RegexToolController implements Initializable {

    @FXML private MFXComboBox<String> commonRegexCombo;
    @FXML private CheckBox caseInsensitiveCheck;
    @FXML private CheckBox multilineCheck;
    @FXML private CheckBox dotAllCheck;
    
    @FXML private MFXTextField regexField;
    @FXML private TextArea sourceTextArea;
    
    @FXML private Label matchStatusLabel;
    @FXML private TextArea matchResultArea;
    
    @FXML private MFXTextField replaceField;
    @FXML private TextArea replaceResultArea;
    
    @FXML private ListView<String> splitResultList;
    
    private final RegexService regexService = new RegexService();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        commonRegexCombo.setItems(FXCollections.observableArrayList(RegexService.COMMON_PATTERNS.keySet()));
        commonRegexCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String pattern = RegexService.COMMON_PATTERNS.get(newVal);
                regexField.setText(pattern);
            }
        });
        initI18n();
    }

    private void initI18n() {
        matchStatusLabel.setText(I18N.get("status.ready"));
    }
    
    private int getFlags() {
        int flags = 0;
        if (caseInsensitiveCheck.isSelected()) flags |= Pattern.CASE_INSENSITIVE;
        if (multilineCheck.isSelected()) flags |= Pattern.MULTILINE;
        if (dotAllCheck.isSelected()) flags |= Pattern.DOTALL;
        return flags;
    }
    
    @FXML
    private void handleMatch() {
        String regex = regexField.getText();
        String text = sourceTextArea.getText();
        if (regex == null || regex.isEmpty()) return;
        
        RegexService.MatchResult res = regexService.matches(regex, text, getFlags());
        if (res.error != null) {
            matchStatusLabel.setText(I18N.get("status.error", res.error));
            matchResultArea.setText(res.error);
        } else {
            matchStatusLabel.setText(I18N.get("status.found", res.count, res.timeCost));
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < res.groups.size(); i++) {
                sb.append("#").append(i + 1).append(": ").append(res.matches.get(i)).append("\n");
                // Groups
                if (res.groups.get(i).size() > 1) {
                    for (int g = 1; g < res.groups.get(i).size(); g++) {
                         sb.append("   Group ").append(g).append(": ").append(res.groups.get(i).get(g)).append("\n");
                    }
                }
                sb.append("\n");
            }
            if (res.count > 1000) sb.append("... (Matches limited to 1000)");
            
            matchResultArea.setText(sb.toString());
        }
    }
    
    @FXML
    private void handleReplace() {
        String regex = regexField.getText();
        String text = sourceTextArea.getText();
        String replacement = replaceField.getText();
        if (regex == null || regex.isEmpty()) return;
        
        String result = regexService.replace(regex, text, replacement == null ? "" : replacement, getFlags());
        replaceResultArea.setText(result);
    }
    
    @FXML
    private void handleSplit() {
        String regex = regexField.getText();
        String text = sourceTextArea.getText();
        if (regex == null || regex.isEmpty()) return;
        
        String[] result = regexService.split(regex, text, getFlags());
        splitResultList.setItems(FXCollections.observableArrayList(result));
    }
    
    @FXML
    private void generateJavaCode() {
        String regex = regexField.getText();
        if (regex == null || regex.isEmpty()) return;
        
        String javaCode = "String regex = \"" + regexService.toJavaString(regex) + "\";";
        ClipboardContent content = new ClipboardContent();
        content.putString(javaCode);
        Clipboard.getSystemClipboard().setContent(content);
        
        DialogUtil.showSuccessInfo(I18N.get("msg.javaCodeCopied", javaCode));
    }
}
