package com.opencgl.envvars.controller;

import com.opencgl.envvars.i18n.I18N;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class EnvVarsController implements Initializable {

    @FXML private TableView<KeyValueRow> envTable;
    @FXML private TableColumn<KeyValueRow, String> envColKey;
    @FXML private TableColumn<KeyValueRow, String> envColValue;
    @FXML private TextField envFilter;
    @FXML private javafx.scene.control.Button btnCopyEnv;

    @FXML private TableView<KeyValueRow> propsTable;
    @FXML private TableColumn<KeyValueRow, String> propsColKey;
    @FXML private TableColumn<KeyValueRow, String> propsColValue;
    @FXML private TextField propsFilter;
    @FXML private javafx.scene.control.Button btnCopyProps;

    private final ObservableList<KeyValueRow> envList = FXCollections.observableArrayList();
    private final ObservableList<KeyValueRow> propsList = FXCollections.observableArrayList();
    private FilteredList<KeyValueRow> envFiltered;
    private FilteredList<KeyValueRow> propsFiltered;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initI18n();
        envColKey.setCellValueFactory(c -> c.getValue().keyProperty());
        envColValue.setCellValueFactory(c -> c.getValue().valueProperty());
        propsColKey.setCellValueFactory(c -> c.getValue().keyProperty());
        propsColValue.setCellValueFactory(c -> c.getValue().valueProperty());

        envFiltered = new FilteredList<>(envList, s -> true);
        propsFiltered = new FilteredList<>(propsList, s -> true);
        envTable.setItems(envFiltered);
        propsTable.setItems(propsFiltered);

        envFilter.textProperty().addListener((o, old, v) -> envFiltered.setPredicate(
                r -> v == null || v.isBlank() || r.getKey().toLowerCase().contains(v.toLowerCase()) || r.getValue().toLowerCase().contains(v.toLowerCase())));
        propsFilter.textProperty().addListener((o, old, v) -> propsFiltered.setPredicate(
                r -> v == null || v.isBlank() || r.getKey().toLowerCase().contains(v.toLowerCase()) || r.getValue().toLowerCase().contains(v.toLowerCase())));

        loadEnv();
        loadProps();
    }

    private void initI18n() {
        // Static texts are in FXML with %key
    }

    private void loadEnv() {
        envList.clear();
        new TreeMap<>(System.getenv()).forEach((k, v) -> envList.add(new KeyValueRow(k, v != null ? v : "")));
    }

    private void loadProps() {
        propsList.clear();
        new TreeMap<>(System.getProperties()).forEach((k, v) -> propsList.add(new KeyValueRow(String.valueOf(k), v != null ? String.valueOf(v) : "")));
    }

    @FXML
    private void onCopyEnv() {
        String text = envFiltered.stream()
                .map(r -> r.getKey() + "=" + r.getValue())
                .collect(Collectors.joining("\n"));
        copyToClipboard(text);
    }

    @FXML
    private void onCopyProps() {
        String text = propsFiltered.stream()
                .map(r -> r.getKey() + "=" + r.getValue())
                .collect(Collectors.joining("\n"));
        copyToClipboard(text);
    }

    private void copyToClipboard(String text) {
        if (text != null && !text.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    public static class KeyValueRow {
        private final SimpleStringProperty key = new SimpleStringProperty();
        private final SimpleStringProperty value = new SimpleStringProperty();

        public KeyValueRow(String key, String value) {
            this.key.set(key);
            this.value.set(value);
        }

        public SimpleStringProperty keyProperty() { return key; }
        public SimpleStringProperty valueProperty() { return value; }
        public String getKey() { return key.get(); }
        public String getValue() { return value.get(); }
    }
}
