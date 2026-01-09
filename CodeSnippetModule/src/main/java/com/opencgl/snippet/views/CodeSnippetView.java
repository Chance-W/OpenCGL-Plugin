package com.opencgl.snippet.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CodeSnippetView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    
    // 左侧面板
    @FXML
    protected MFXTextField searchField;
    @FXML
   protected TreeView<String> languageTree;
    @FXML
    protected ListView<String> snippetList;
    
    // 中间编辑区
    @FXML
    protected MFXTextField titleField;
    @FXML
    protected MFXComboBox<String> languageComboBox;
    @FXML
    protected MFXTextField tagsField;
    @FXML
    protected TextArea descriptionArea;
    @FXML
    protected VBox codeAreaContainer;
    @FXML
    protected CheckBox favoriteCheckBox;
    
    // 按钮
    @FXML
    protected MFXButton newButton;
    @FXML
    protected MFXButton saveButton;
    @FXML
    protected MFXButton deleteButton;
    @FXML
    protected MFXButton copyButton;
    @FXML
    protected MFXButton importButton;
    @FXML
    protected MFXButton exportButton;
    
    @FXML
    protected Label statusLabel;
}
