package com.opencgl.aiqa.views;

import com.opencgl.base.controls.CustomTextArea;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * AI Q&A View - FXML 绑定
 */
public abstract class AiQaView implements Initializable {
    
    @FXML
    protected StackPane mainStackPane;
    
    // 左侧模型列表
    @FXML
    protected Label modelListLabel;
    @FXML
    protected ListView<String> modelListView;
    @FXML
    protected MFXButton addModelBtn;
    @FXML
    protected MFXButton editModelBtn;
    @FXML
    protected MFXButton deleteModelBtn;
    @FXML
    protected MFXButton saveModelBtn;
    
    // 模型配置区
    @FXML
    protected TitledPane configPane;
    @FXML
    protected MFXTextField baseUrlField;
    @FXML
    protected MFXTextField apiKeyField;
    @FXML
    protected MFXTextField modelField;
    @FXML
    protected MFXTextField temperatureField;
    @FXML
    protected MFXButton testConnectionBtn;
    
    // 文档库
    @FXML
    protected TitledPane docPane;
    @FXML
    protected MFXTextField docPathField;
    @FXML
    protected MFXButton browseDocBtn;
    @FXML
    protected Label docCountLabel;
    
    // 聊天区域
    @FXML
    protected ScrollPane chatScrollPane;
    @FXML
    protected VBox chatContainer;
    @FXML
    protected CustomTextArea inputTextArea;
    @FXML
    protected MFXButton sendBtn;
    @FXML
    protected MFXButton clearChatBtn;
}
