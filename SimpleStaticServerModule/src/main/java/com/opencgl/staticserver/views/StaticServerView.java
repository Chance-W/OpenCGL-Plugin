package com.opencgl.staticserver.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * 静态服务器视图
 */
public class StaticServerView {
    @FXML public StackPane rootPane;
    
    // 目录选择
    @FXML public ListView<String> directoryListView;
    @FXML public MFXButton selectDirButton;
    // @FXML public MFXButton selectMultiDirButton;
    @FXML public MFXButton removeDirButton;
    
    // IP和端口配置
    @FXML public ComboBox<String> ipComboBox;
    @FXML public TextField portField;
    
    // 控制按钮
    @FXML public MFXButton startButton;
    @FXML public MFXButton stopButton;
    
    // 状态显示
    @FXML public Label statusLabel;
    @FXML public Hyperlink urlLink;
    @FXML public MFXButton copyUrlButton;

    // 二维码
    @FXML public VBox qrCodeBox;
    @FXML public ImageView qrCodeImage;

}
