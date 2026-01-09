package com.opencgl.gitlite.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class GitLiteView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    protected TabPane mainTabPane;
    @FXML
    protected Label titleLabel;

    // .gitignore 生成器 Tab
    @FXML
    protected MFXComboBox<String> languageComboBox;
    @FXML
    protected MFXButton addTemplateButton;
    @FXML
    protected MFXButton clearTemplateButton;
    @FXML
    protected TextArea gitignoreArea;
    @FXML
    protected MFXButton copyGitignoreButton;
    @FXML
    protected MFXButton saveGitignoreButton;
    @FXML
    protected Label selectLanguageLabel;
    @FXML
    protected Label gitignoreContentLabel;

    // 提交记录 Tab
    @FXML
    protected MFXTextField repoPathField;
    @FXML
    protected Label repoPathLabel;
    @FXML
    protected MFXButton selectRepoButton;
    @FXML
    protected MFXButton loadCommitsButton;
    @FXML
    protected TableView<?> commitsTable;
    @FXML
    protected TableColumn<?, ?> colHash;
    @FXML
    protected TableColumn<?, ?> colMessage;
    @FXML
    protected TableColumn<?, ?> colAuthor;
    @FXML
    protected TableColumn<?, ?> colDate;

    // 命令速查 Tab
    @FXML
    protected MFXTextField searchCommandField;
    @FXML
    protected TreeView<?> commandsTree;
    @FXML
    protected Label commandDetailLabel;
    @FXML
    protected TextArea commandDetailArea;

    @FXML
    protected Label statusLabel;
}
