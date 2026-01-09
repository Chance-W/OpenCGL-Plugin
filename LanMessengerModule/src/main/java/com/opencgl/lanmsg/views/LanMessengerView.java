package com.opencgl.lanmsg.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * 局域网通讯视图
 */
public class LanMessengerView {
    @FXML public StackPane rootPane;
    
    // 用户列表
    @FXML public ListView<String> userListView;
    @FXML public Label userCountLabel;
    @FXML public MFXButton refreshButton;
    
    // 聊天区域
    @FXML public Label chatTitleLabel;
    @FXML public ListView<com.opencgl.lanmsg.model.Message> chatListView;
    @FXML public TextField messageInput;
    @FXML public MFXButton sendButton;
    @FXML public MFXButton sendFileButton;
    
    // 状态栏
    @FXML public Label statusLabel;
    @FXML public Label myInfoLabel;
    
    // 设置
    @FXML public Label settingsLabel;
    @FXML public Label usernameLabel;
    @FXML public Label ipLabel;
    @FXML public Label portLabel;
    @FXML public TextField usernameField;
    @FXML public javafx.scene.control.ComboBox<String> ipComboBox;
    @FXML public TextField portField;
    @FXML public MFXButton startServiceButton;
    @FXML public MFXButton stopServiceButton;
}
