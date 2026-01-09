package com.opencgl.checksum.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

public class FileChecksumView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;

    @FXML
    protected MFXComboBox<String> algorithmComboBox;
    @FXML
    protected MFXButton selectFileButton;
    @FXML
    protected MFXButton selectFolderButton;
    @FXML
    protected MFXButton clearButton;
    @FXML
    protected MFXButton exportButton;
    @FXML
    protected TableView<?> resultsTable;

    // 文件对比区
    @FXML
    protected MFXTextField file1HashField;
    @FXML
    protected MFXTextField file2HashField;
    @FXML
    protected MFXButton compareButton;
    @FXML
    protected Label compareResultLabel;

    @FXML
    protected Label titleLabel;
    @FXML
    protected Label algoLabel;
    @FXML
    protected Label resultsLabel;
    @FXML
    protected Label dragDropLabel;
    @FXML
    protected Label compareLabel;
    @FXML
    protected Label hash1Label;
    @FXML
    protected Label hash2Label;

    @FXML
    protected TableColumn<?, ?> filenameColumn;
    @FXML
    protected TableColumn<?, ?> sizeColumn;
    @FXML
    protected TableColumn<?, ?> algoColumn;
    @FXML
    protected TableColumn<?, ?> hashColumn;

    @FXML
    protected Label statusLabel;
}
