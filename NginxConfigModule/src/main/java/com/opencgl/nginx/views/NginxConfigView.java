package com.opencgl.nginx.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.controls.MFXToggleButton;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class NginxConfigView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    
    // 基础配置
    @FXML
    protected MFXTextField serverNameField;
    @FXML
    protected MFXTextField listenPortField;
    @FXML
    protected MFXTextField rootPathField;
    @FXML
    protected MFXTextField indexField;
    
    // SSL 配置
    @FXML
    protected MFXToggleButton sslToggle;
    @FXML
    protected MFXTextField sslCertField;
    @FXML
    protected MFXTextField sslKeyField;
    @FXML
    protected VBox sslConfigBox;
    
    // 反向代理
    @FXML
    protected MFXTextField proxyPassField;
    @FXML
    protected MFXTextField proxyLocationField;
    @FXML
    protected MFXToggleButton proxyToggle;
    @FXML
    protected VBox proxyConfigBox;
    
    // 负载均衡
    @FXML
    protected MFXToggleButton upstreamToggle;
    @FXML
    protected MFXTextField upstreamNameField;
    @FXML
    protected MFXComboBox<String> loadBalanceComboBox;
    @FXML
    protected TextArea upstreamServersArea;
    @FXML
    protected VBox upstreamConfigBox;
    
    // 预览和操作
    @FXML
    protected TextArea previewArea;
    @FXML
    protected MFXButton generateButton;
    @FXML
    protected MFXButton copyButton;
    @FXML
    protected MFXButton saveButton;
    
    @FXML
    protected Label statusLabel;
}
