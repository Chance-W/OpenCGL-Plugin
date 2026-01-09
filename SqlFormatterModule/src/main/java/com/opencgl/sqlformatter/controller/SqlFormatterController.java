package com.opencgl.sqlformatter.controller;

import com.opencgl.sqlformatter.i18n.I18N;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class SqlFormatterController implements Initializable {

    @FXML private ComboBox<String> dialectCombo;
    @FXML private Button formatButton;
    @FXML private Button compressButton;
    @FXML private Button copyButton;
    @FXML private TextArea inputArea;
    @FXML private TextArea outputArea;
    @FXML private Label statusLabel;

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final String[] KEYWORDS = {
            "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER BY", "GROUP BY", "HAVING",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "JOIN", "LEFT", "RIGHT",
            "INNER", "OUTER", "ON", "AS", "IN", "NOT", "NULL", "IS", "BETWEEN", "LIKE",
            "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "CREATE", "TABLE", "INDEX",
            "DROP", "ALTER", "ADD", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "DEFAULT"
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        dialectCombo.getItems().setAll(
                I18N.get("dialect.generic"),
                I18N.get("dialect.mysql"),
                I18N.get("dialect.postgresql"),
                I18N.get("dialect.oracle"),
                I18N.get("dialect.sqlserver")
        );
        dialectCombo.getSelectionModel().selectFirst();

        formatButton.setOnAction(e -> format());
        compressButton.setOnAction(e -> compress());
        copyButton.setOnAction(e -> copyResult());
    }

    private void initI18n() {
        if (formatButton != null) formatButton.textProperty().bind(I18N.getBinding("btn.format"));
        if (compressButton != null) compressButton.textProperty().bind(I18N.getBinding("btn.compress"));
        if (copyButton != null) copyButton.textProperty().bind(I18N.getBinding("btn.copy"));
        if (statusLabel != null) statusLabel.setText(I18N.get("label.statusReady"));
    }

    private void format() {
        String sql = inputArea.getText();
        if (sql == null || sql.trim().isEmpty()) {
            statusLabel.setText(I18N.get("status.pleaseInput"));
            return;
        }
        String normalized = SPACES.matcher(sql.trim()).replaceAll(" ");
        String upper = normalized.toUpperCase();
        for (String kw : KEYWORDS) {
            String kwLower = kw.toLowerCase();
            upper = replaceWordBoundary(upper, kw, " " + kw + " ");
        }
        String formatted = upper.trim();
        formatted = formatted.replace(" ,", ",").replace(" (", "(").replace(" )", ")");
        formatted = formatted.replace("SELECT ", "SELECT\n  ").replace(" FROM ", "\nFROM ");
        formatted = formatted.replace(" WHERE ", "\nWHERE ");
        formatted = formatted.replace(" AND ", "\n  AND ").replace(" OR ", "\n  OR ");
        formatted = formatted.replace(" ORDER BY ", "\nORDER BY ").replace(" GROUP BY ", "\nGROUP BY ");
        formatted = formatted.replace(" LIMIT ", "\nLIMIT ").replace(" OFFSET ", "\nOFFSET ");
        formatted = formatted.replace(" JOIN ", "\nJOIN ").replace(" LEFT ", "\nLEFT ").replace(" RIGHT ", "\nRIGHT ");
        formatted = formatted.replace(" INNER ", "\nINNER ").replace(" ON ", "\n  ON ");
        formatted = formatted.replace(" INSERT ", "\nINSERT ").replace(" INTO ", "\nINTO ");
        formatted = formatted.replace(" VALUES ", "\nVALUES ").replace(" UPDATE ", "\nUPDATE ");
        formatted = formatted.replace(" SET ", "\nSET ").replace(" DELETE ", "\nDELETE ");
        outputArea.setText(formatted);
        statusLabel.setText(I18N.get("status.formatted"));
    }

    private static String replaceWordBoundary(String text, String keyword, String replacement) {
        StringBuilder sb = new StringBuilder();
        String lower = text.toLowerCase();
        String kw = keyword.toUpperCase();
        int i = 0;
        while (i < text.length()) {
            int idx = lower.indexOf(kw.toLowerCase(), i);
            if (idx < 0) {
                sb.append(text.substring(i));
                break;
            }
            boolean startOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            int end = idx + keyword.length();
            boolean endOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) {
                sb.append(text, i, idx).append(replacement);
                i = end;
            } else {
                sb.append(text, i, idx + 1);
                i = idx + 1;
            }
        }
        return sb.toString();
    }

    private void compress() {
        String sql = inputArea.getText();
        if (sql == null || sql.trim().isEmpty()) {
            statusLabel.setText(I18N.get("status.pleaseInput"));
            return;
        }
        String compressed = SPACES.matcher(sql.trim()).replaceAll(" ").replaceAll("\\s*,\\s*", ", ");
        outputArea.setText(compressed);
        statusLabel.setText(I18N.get("status.compressed"));
    }

    private void copyResult() {
        String out = outputArea.getText();
        if (out != null && !out.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(out);
            Clipboard.getSystemClipboard().setContent(content);
            statusLabel.setText(I18N.get("status.copied"));
        }
    }
}
