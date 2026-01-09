package com.opencgl.propertieseditor.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.opencgl.propertieseditor.i18n.I18N;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class PropertiesEditorController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(PropertiesEditorController.class);

    @FXML private Button btnOpen;
    @FXML private Button btnSave;
    @FXML private ComboBox<String> encodingCombo;
    @FXML private Button btnExportYamlJson;
    @FXML private TableView<KeyValueRow> tableView;
    @FXML private TableColumn<KeyValueRow, String> colKey;
    @FXML private TableColumn<KeyValueRow, String> colValue;
    @FXML private Label statusLabel;

    private ObservableList<KeyValueRow> data = FXCollections.observableArrayList();
    private File currentFile;
    private static final String UTF8 = "UTF-8";
    private static final String ISO8859 = "ISO-8859-1";

    public static class KeyValueRow {
        private final SimpleStringProperty key = new SimpleStringProperty("");
        private final SimpleStringProperty value = new SimpleStringProperty("");

        public KeyValueRow() {}
        public KeyValueRow(String k, String v) {
            key.set(k);
            value.set(v != null ? v : "");
        }
        public String getKey() { return key.get(); }
        public void setKey(String k) { key.set(k); }
        public SimpleStringProperty keyProperty() { return key; }
        public String getValue() { return value.get(); }
        public void setValue(String v) { value.set(v); }
        public SimpleStringProperty valueProperty() { return value; }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        encodingCombo.getItems().setAll(
                I18N.get("encoding.utf8"),
                I18N.get("encoding.iso8859")
        );
        encodingCombo.getSelectionModel().select(0);

        colKey.setCellValueFactory(cd -> cd.getValue().keyProperty());
        colValue.setCellValueFactory(cd -> cd.getValue().valueProperty());
        tableView.setItems(data);
        tableView.setEditable(true);
        colKey.setEditable(true);
        colValue.setEditable(true);
    }

    private void initI18n() {
        if (statusLabel != null) statusLabel.setText(I18N.get("status.ready"));
    }

    @FXML
    private void onOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("filechooser.open"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.properties"), "*.properties"));
        File file = chooser.showOpenDialog(tableView.getScene() != null ? tableView.getScene().getWindow() : null);
        if (file != null) loadFile(file);
    }

    private void loadFile(File file) {
        String enc = encodingCombo.getSelectionModel().getSelectedItem();
        if (enc == null) enc = UTF8;
        Charset cs = enc.contains("ISO") ? Charset.forName(ISO8859) : StandardCharsets.UTF_8;
        try {
            Properties props = new Properties();
            try (Reader r = new InputStreamReader(Files.newInputStream(file.toPath()), cs)) {
                props.load(r);
            }
            data.clear();
            for (String k : props.stringPropertyNames()) {
                data.add(new KeyValueRow(k, props.getProperty(k)));
            }
            currentFile = file;
            statusLabel.setText(I18N.get("status.loaded", file.getName()));
        } catch (Exception e) {
            logger.error("Load properties failed", e);
            statusLabel.setText(I18N.get("status.error", e.getMessage()));
        }
    }

    @FXML
    private void onSave() {
        String enc = encodingCombo.getSelectionModel().getSelectedItem();
        if (enc == null) enc = UTF8;
        Charset cs = enc.contains("ISO") ? Charset.forName(ISO8859) : StandardCharsets.UTF_8;
        File file = currentFile;
        if (file == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18N.get("filechooser.save"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.properties"), "*.properties"));
            file = chooser.showSaveDialog(tableView.getScene() != null ? tableView.getScene().getWindow() : null);
            if (file == null) return;
        }
        try {
            Properties props = new Properties();
            for (KeyValueRow row : data) {
                String k = row.getKey();
                if (k != null && !k.isBlank()) props.setProperty(k.trim(), row.getValue() != null ? row.getValue() : "");
            }
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(file.toPath()), cs)) {
                props.store(w, null);
            }
            currentFile = file;
            statusLabel.setText(I18N.get("status.saved", file.getName()));
        } catch (Exception e) {
            logger.error("Save properties failed", e);
            statusLabel.setText(I18N.get("status.error", e.getMessage()));
        }
    }

    @FXML
    private void onExportYamlJson() {
        if (data.isEmpty()) {
            statusLabel.setText(I18N.get("status.ready"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("filechooser.save"));
        FileChooser.ExtensionFilter jsonFilter = new FileChooser.ExtensionFilter("JSON (*.json)", "*.json");
        FileChooser.ExtensionFilter yamlFilter = new FileChooser.ExtensionFilter("YAML (*.yml, *.yaml)", "*.yml", "*.yaml");
        chooser.getExtensionFilters().addAll(jsonFilter, yamlFilter);
        File file = chooser.showSaveDialog(tableView.getScene() != null ? tableView.getScene().getWindow() : null);
        if (file == null) return;
        try {
            Map<String, String> map = new LinkedHashMap<>();
            for (KeyValueRow row : data) {
                String k = row.getKey();
                if (k != null && !k.isBlank()) map.put(k.trim(), row.getValue() != null ? row.getValue() : "");
            }
            String path = file.getAbsolutePath().toLowerCase();
            if (path.endsWith(".json")) {
                String json = JSON.toJSONString(map, JSONWriter.Feature.PrettyFormat);
                Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
            } else {
                StringBuilder yaml = new StringBuilder();
                for (Map.Entry<String, String> e : map.entrySet()) {
                    yaml.append(e.getKey()).append(": ");
                    String v = e.getValue();
                    if (v != null && (v.contains("\n") || v.contains(":") || v.contains("#"))) {
                        yaml.append('"').append(v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"');
                    } else {
                        yaml.append(v != null ? v : "");
                    }
                    yaml.append("\n");
                }
                Files.writeString(file.toPath(), yaml.toString(), StandardCharsets.UTF_8);
            }
            statusLabel.setText(I18N.get("status.exported", file.getName()));
        } catch (Exception e) {
            logger.error("Export failed", e);
            statusLabel.setText(I18N.get("status.error", e.getMessage()));
        }
    }
}
