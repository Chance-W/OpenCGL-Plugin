package com.opencgl.portscan.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.controls.MFXProgressBar;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class PortScannerView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected TabPane mainTabPane;
    
    // 端口测试 Tab
    @FXML
    protected MFXTextField hostField;
    @FXML
    protected MFXTextField portField;
    @FXML
    protected MFXButton testButton;
    @FXML
    protected TextArea resultArea;
    
    // Ping Tab
    @FXML
    protected MFXTextField pingHostField;
    @FXML
    protected MFXButton pingButton;
    @FXML
    protected TextArea pingResultArea;
    
    // 端口扫描 Tab
    @FXML
    protected MFXTextField scanHostField;
    @FXML
    protected MFXTextField startPortField;
    @FXML
    protected MFXTextField endPortField;
    @FXML
    protected MFXButton scanButton;
    @FXML
    protected MFXButton stopScanButton;
    @FXML
    protected MFXProgressBar scanProgress;
    @FXML
    protected TableView<?> scanResultTable;
    
    // 本机端口 Tab
    @FXML
    protected MFXButton refreshLocalButton;
    @FXML
    protected TableView<?> localPortTable;
    
    @FXML
    protected Label statusLabel;
}
