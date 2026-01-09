package com.opencgl.processport.controller;

import com.opencgl.processport.i18n.I18N;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.lang.ProcessHandle;

public class ProcessPortController implements Initializable {

    @FXML private TableView<ProcessRow> processTable;
    @FXML private TableColumn<ProcessRow, String> colPid;
    @FXML private TableColumn<ProcessRow, String> colName;
    @FXML private TableColumn<ProcessRow, String> colPort;
    @FXML private javafx.scene.control.Button btnRefresh;
    @FXML private javafx.scene.control.Button btnKill;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initI18n();
        colPid.setCellValueFactory(c -> c.getValue().pidProperty());
        colName.setCellValueFactory(c -> c.getValue().nameProperty());
        colPort.setCellValueFactory(c -> c.getValue().portProperty());
        onRefresh();
    }

    private void initI18n() {
        // Static texts are in FXML with %key
    }

    @FXML
    private void onRefresh() {
        List<ProcessRow> rows = new ArrayList<>();
        ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .forEach(ph -> {
                    String pid = String.valueOf(ph.pid());
                    String name = ph.info().command().orElse(ph.info().commandLine().orElse(""));
                    if (name == null) name = "";
                    rows.add(new ProcessRow(pid, name, "-"));
                });
        processTable.getItems().clear();
        processTable.getItems().addAll(rows);
    }

    @FXML
    private void onKillProcess() {
        ProcessRow row = processTable.getSelectionModel().getSelectedItem();
        if (row == null) return;
        String msg = I18N.get("msg.killConfirm", row.getName(), row.getPid());
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                long pid = Long.parseLong(row.getPid());
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
                processTable.getItems().remove(row);
                new Alert(Alert.AlertType.INFORMATION, I18N.get("msg.killSuccess")).show();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, I18N.get("msg.killFailed")).show();
            }
        }
    }

    public static class ProcessRow {
        private final SimpleStringProperty pid = new SimpleStringProperty();
        private final SimpleStringProperty name = new SimpleStringProperty();
        private final SimpleStringProperty port = new SimpleStringProperty();

        public ProcessRow(String pid, String name, String port) {
            this.pid.set(pid);
            this.name.set(name);
            this.port.set(port);
        }

        public SimpleStringProperty pidProperty() { return pid; }
        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty portProperty() { return port; }
        public String getPid() { return pid.get(); }
        public String getName() { return name.get(); }
        public String getPort() { return port.get(); }
    }
}
