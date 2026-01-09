package com.opencgl.logviewer.controller;

import com.opencgl.logviewer.i18n.I18N;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class LogViewerController implements Initializable {

    @FXML private Button openButton;
    @FXML private Button refreshButton;
    @FXML private ComboBox<String> levelCombo;
    @FXML private TextField keywordField;
    @FXML private TextArea contentArea;
    @FXML private Label statusLabel;

    private File currentFile;
    private List<String> allLines = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        levelCombo.getItems().setAll(
                I18N.get("level.all"),
                I18N.get("level.debug"),
                I18N.get("level.info"),
                I18N.get("level.warn"),
                I18N.get("level.error")
        );
        levelCombo.getSelectionModel().selectFirst();

        openButton.setOnAction(e -> openFile());
        refreshButton.setOnAction(e -> refresh());
        levelCombo.getSelectionModel().selectedItemProperty().addListener((o, old, val) -> applyFilter());
        keywordField.textProperty().addListener((o, old, val) -> applyFilter());
    }

    private void initI18n() {
        if (openButton != null) openButton.textProperty().bind(I18N.getBinding("btn.open"));
        if (refreshButton != null) refreshButton.textProperty().bind(I18N.getBinding("btn.refresh"));
        if (statusLabel != null) statusLabel.setText(I18N.get("label.statusReady"));
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("filechooser.open"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.log"), "*.log", "*.txt"));
        File file = chooser.showOpenDialog(contentArea.getScene() != null ? contentArea.getScene().getWindow() : null);
        if (file != null) {
            loadFile(file);
        }
    }

    private void loadFile(File file) {
        try {
            currentFile = file;
            allLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            statusLabel.setText(I18N.get("status.loaded", file.getName(), allLines.size()));
            applyFilter();
        } catch (Exception e) {
            statusLabel.setText(e.getMessage());
            contentArea.setText("");
        }
    }

    private void refresh() {
        if (currentFile != null && currentFile.exists()) {
            loadFile(currentFile);
        }
    }

    private void applyFilter() {
        if (allLines.isEmpty()) {
            contentArea.setText("");
            return;
        }
        String levelSel = levelCombo.getSelectionModel().getSelectedItem();
        String levelKey = levelSel != null ? levelSel : I18N.get("level.all");
        String targetLevel = null;
        if (I18N.get("level.info").equals(levelKey)) targetLevel = "INFO";
        else if (I18N.get("level.warn").equals(levelKey)) targetLevel = "WARN";
        else if (I18N.get("level.error").equals(levelKey)) targetLevel = "ERROR";
        else if (I18N.get("level.debug").equals(levelKey)) targetLevel = "DEBUG";

        String keyword = keywordField.getText();
        keyword = keyword == null ? "" : keyword.trim();
        Pattern keywordPattern = null;
        if (!keyword.isEmpty()) {
            keywordPattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
        }

        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String line : allLines) {
            if (targetLevel != null && !line.toUpperCase().contains(targetLevel)) continue;
            if (keywordPattern != null && !keywordPattern.matcher(line).find()) continue;
            out.append(line).append("\n");
            count++;
        }
        contentArea.setText(out.length() > 0 ? out.toString() : "");
        if (targetLevel != null || keywordPattern != null) {
            statusLabel.setText(I18N.get("status.filtered", count));
        }
    }
}
