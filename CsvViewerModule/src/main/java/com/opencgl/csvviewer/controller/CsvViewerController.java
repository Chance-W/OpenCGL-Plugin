package com.opencgl.csvviewer.controller;

import com.opencgl.csvviewer.i18n.I18N;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class CsvViewerController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(CsvViewerController.class);

    @FXML private Button openButton;
    @FXML private Button pasteButton;
    @FXML private Button exportButton;
    @FXML private Button clearButton;
    @FXML private TextField filterField;
    @FXML private TableView<Map<String, String>> tableView;
    @FXML private Label statusLabel;

    private ObservableList<Map<String, String>> data = FXCollections.observableArrayList();
    private List<String> headers = new ArrayList<>();
    private FilteredList<Map<String, String>> filteredData;
    private SortedList<Map<String, String>> sortedData;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        filteredData = new FilteredList<>(data, p -> true);
        sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedData);

        filterField.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.trim().isEmpty()) {
                filteredData.setPredicate(row -> true);
            } else {
                String lower = val.trim().toLowerCase();
                filteredData.setPredicate(row -> {
                    for (String cell : row.values()) {
                        if (cell != null && cell.toLowerCase().contains(lower)) return true;
                    }
                    return false;
                });
            }
        });

        openButton.setOnAction(e -> openFile());
        pasteButton.setOnAction(e -> pasteFromClipboard());
        exportButton.setOnAction(e -> exportCsv());
        clearButton.setOnAction(e -> clear());
    }

    private void initI18n() {
        if (openButton != null) openButton.textProperty().bind(I18N.getBinding("btn.open"));
        if (pasteButton != null) pasteButton.textProperty().bind(I18N.getBinding("btn.paste"));
        if (exportButton != null) exportButton.textProperty().bind(I18N.getBinding("btn.export"));
        if (clearButton != null) clearButton.textProperty().bind(I18N.getBinding("btn.clear"));
        if (statusLabel != null) statusLabel.setText(I18N.get("label.statusReady"));
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("filechooser.open"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.csv"), "*.csv"));
        File file = chooser.showOpenDialog(tableView.getScene() != null ? tableView.getScene().getWindow() : null);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                loadCsv(content);
                statusLabel.setText(I18N.get("status.loaded", file.getName()));
            } catch (Exception e) {
                logger.error("Read CSV failed", e);
                statusLabel.setText(e.getMessage());
            }
        }
    }

    private void pasteFromClipboard() {
        Clipboard cb = Clipboard.getSystemClipboard();
        if (cb.hasString()) {
            loadCsv(cb.getString());
            statusLabel.setText(I18N.get("status.loaded", "Clipboard"));
        } else {
            statusLabel.setText(I18N.get("status.pleaseLoad"));
        }
    }

    private void loadCsv(String raw) {
        if (raw == null || raw.isEmpty()) return;
        String[] lines = raw.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        if (lines.length == 0) return;
        headers.clear();
        data.clear();
        tableView.getColumns().clear();

        String[] first = parseCsvLine(lines[0]);
        for (int i = 0; i < first.length; i++) {
            headers.add(first[i].isEmpty() ? "Col" + (i + 1) : first[i]);
        }
        for (String h : headers) {
            TableColumn<Map<String, String>, String> col = new TableColumn<>(h);
            final String key = h;
            col.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().get(key)));
            tableView.getColumns().add(col);
        }
        for (int i = 1; i < lines.length; i++) {
            String[] cells = parseCsvLine(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < cells.length ? cells[j] : "");
            }
            data.add(row);
        }
        tableView.sort();
    }

    private static String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (inQuotes) {
                cur.append(c);
            } else if (c == ',') {
                out.add(cur.toString());
                cur = new StringBuilder();
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private void exportCsv() {
        if (data.isEmpty()) {
            statusLabel.setText(I18N.get("status.pleaseLoad"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("filechooser.save"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.csv"), "*.csv"));
        File file = chooser.showSaveDialog(tableView.getScene() != null ? tableView.getScene().getWindow() : null);
        if (file != null) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(String.join(",", headers)).append("\n");
                for (Map<String, String> row : sortedData) {
                    List<String> cells = new ArrayList<>();
                    for (String h : headers) {
                        String v = row.getOrDefault(h, "");
                        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                            v = "\"" + v.replace("\"", "\"\"") + "\"";
                        }
                        cells.add(v);
                    }
                    sb.append(String.join(",", cells)).append("\n");
                }
                Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
                statusLabel.setText(I18N.get("status.exported", file.getName()));
            } catch (Exception e) {
                logger.error("Export CSV failed", e);
                statusLabel.setText(e.getMessage());
            }
        }
    }

    private void clear() {
        data.clear();
        headers.clear();
        tableView.getColumns().clear();
        filterField.clear();
        statusLabel.setText(I18N.get("status.cleared"));
    }
}
