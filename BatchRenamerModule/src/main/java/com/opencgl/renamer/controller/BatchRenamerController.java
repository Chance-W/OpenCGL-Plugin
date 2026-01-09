package com.opencgl.renamer.controller;

import com.opencgl.renamer.i18n.I18N;
import com.opencgl.renamer.model.FileRenameEntry;
import com.opencgl.renamer.service.FileRenameService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;

public class BatchRenamerController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(BatchRenamerController.class);
    
    public BorderPane rootPane;
    public Button selectFolderButton;
    public Button addFilesButton;
    public Button clearButton;
    public Label fileCountLabel;
    
    // 重命名规则
    public CheckBox regexCheckBox;
    public TextField findField;
    public TextField replaceField;
    public TextField prefixField;
    public TextField suffixField;
    public CheckBox numberingCheckBox;
    public Spinner<Integer> startNumberSpinner;
    public Spinner<Integer> digitsSpinner;
    public Button previewButton;
    
    // 文件列表
    public TableView<FileRenameEntry> fileTable;
    public TableColumn<FileRenameEntry, String> originalNameCol;
    public TableColumn<FileRenameEntry, String> newNameCol;
    public TableColumn<FileRenameEntry, String> statusCol;
    
    public Label summaryLabel;
    public Button executeButton;
    public Button undoButton;
    
    private FileRenameService renameService;
    private ObservableList<FileRenameEntry> fileEntries;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        renameService = new FileRenameService();
        fileEntries = FXCollections.observableArrayList();
        
        setupUI();
        bindEvents();
        initI18n();
    }

    private void initI18n() {
        updateFileCount();
        updateSummary();
    }

    private void setupUI() {
        // 表格
        fileTable.setItems(fileEntries);
        originalNameCol.setCellValueFactory(cellData -> cellData.getValue().originalNameProperty());
        newNameCol.setCellValueFactory(cellData -> cellData.getValue().newNameProperty());
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        
        // 冲突高亮
        newNameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    FileRenameEntry entry = getTableView().getItems().get(getIndex());
                    if (entry.isHasConflict()) {
                        setStyle("-fx-background-color: #ffcccc;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Spinners
        startNumberSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 1));
        digitsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 6, 3));
    }

    private void bindEvents() {
        selectFolderButton.setOnAction(e -> selectFolder());
        addFilesButton.setOnAction(e -> addFiles());
        clearButton.setOnAction(e -> clearAll());
        
        previewButton.setOnAction(e -> previewRenames());
        executeButton.setOnAction(e -> executeRenames());
        undoButton.setOnAction(e -> undoRenames());
        
        // 实时预览
        findField.textProperty().addListener((obs, old, val) -> previewRenames());
        replaceField.textProperty().addListener((obs, old, val) -> previewRenames());
        prefixField.textProperty().addListener((obs, old, val) -> previewRenames());
        suffixField.textProperty().addListener((obs, old, val) -> previewRenames());
        regexCheckBox.selectedProperty().addListener((obs, old, val) -> previewRenames());
        numberingCheckBox.selectedProperty().addListener((obs, old, val) -> previewRenames());
    }

    private void selectFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18N.get("dialog.selectFolder"));
        File dir = chooser.showDialog(rootPane.getScene().getWindow());
        
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileEntries.add(new FileRenameEntry(file));
                    }
                }
                updateFileCount();
                previewRenames();
            }
        }
    }

    private void addFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("dialog.selectFiles"));
        List<File> files = chooser.showOpenMultipleDialog(rootPane.getScene().getWindow());
        
        if (files != null) {
            for (File file : files) {
                fileEntries.add(new FileRenameEntry(file));
            }
            updateFileCount();
            previewRenames();
        }
    }

    private void clearAll() {
        fileEntries.clear();
        updateFileCount();
    }

    private void previewRenames() {
        String prefix = prefixField.getText();
        String suffix = suffixField.getText();
        String find = findField.getText();
        String replace = replaceField.getText();
        boolean useRegex = regexCheckBox.isSelected();
        boolean useNumbering = numberingCheckBox.isSelected();
        int startNum = startNumberSpinner.getValue();
        int digits = useNumbering ? digitsSpinner.getValue() : 0;
        
        // 计算新名称
        for (int i = 0; i < fileEntries.size(); i++) {
            FileRenameEntry entry = fileEntries.get(i);
            entry.computeNewName(prefix, suffix, find, replace, useRegex, startNum + i, digits);
            entry.setStatus(I18N.get("status.pending"));
            entry.setHasConflict(false);
        }
        
        // 检测冲突
        Set<String> newNames = new HashSet<>();
        for (FileRenameEntry entry : fileEntries) {
            if (!newNames.add(entry.getNewName().toLowerCase())) {
                entry.setHasConflict(true);
            }
        }
        
        fileTable.refresh();
        updateSummary();
    }

    private void executeRenames() {
        long needRename = fileEntries.stream().filter(FileRenameEntry::needsRename).count();
        if (needRename == 0) {
            showAlert(I18N.get("msg.noFiles"));
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
            I18N.get("msg.confirmRename", needRename));
        Optional<ButtonType> result = confirm.showAndWait();
        
        if (result.isPresent() && result.get() == ButtonType.OK) {
            renameService.clearHistory();
            
            int success = 0;
            for (FileRenameEntry entry : fileEntries) {
                if (renameService.executeRename(entry)) {
                    success++;
                }
            }
            
            fileTable.refresh();
            undoButton.setDisable(!renameService.hasHistory());
            
            showAlert(I18N.get("msg.done", success, needRename));
        }
    }

    private void undoRenames() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, I18N.get("msg.confirmUndo"));
        Optional<ButtonType> result = confirm.showAndWait();
        
        if (result.isPresent() && result.get() == ButtonType.OK) {
            renameService.undoLastBatch();
            undoButton.setDisable(true);
            
            // 重新加载文件列表
            clearAll();
            showAlert(I18N.get("msg.undoSuccess"));
        }
    }

    private void updateFileCount() {
        fileCountLabel.setText(I18N.get("label.fileCount", fileEntries.size()));
    }

    private void updateSummary() {
        long needRename = fileEntries.stream().filter(FileRenameEntry::needsRename).count();
        long conflicts = fileEntries.stream().filter(FileRenameEntry::isHasConflict).count();
        
        if (conflicts > 0) {
            summaryLabel.setText(I18N.get("label.summaryConflicts", needRename, conflicts));
            summaryLabel.setStyle("-fx-text-fill: red;");
            executeButton.setDisable(true);
        } else {
            summaryLabel.setText(I18N.get("label.summary", needRename));
            summaryLabel.setStyle("");
            executeButton.setDisable(needRename == 0);
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.showAndWait();
    }
}
