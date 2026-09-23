package com.opencgl.gitlite.controller;

import com.opencgl.gitlite.i18n.I18N;
import com.opencgl.gitlite.service.GitIgnoreService;
import com.opencgl.gitlite.service.GitCommandService;
import com.opencgl.gitlite.service.GitCommandService.*;
import com.opencgl.gitlite.views.GitLiteView;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Git 简易工具控制器
 */
public class GitLiteController extends GitLiteView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(GitLiteController.class);

    private final GitIgnoreService gitIgnoreService = new GitIgnoreService();
    private final GitCommandService gitCommandService = new GitCommandService();

    private final List<String> selectedTemplates = new ArrayList<>();
    private final ObservableList<CommitInfo> commitsList = FXCollections.observableArrayList();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-git-lite");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        com.opencgl.base.utils.tree.TreeViewPresentation.install(commandsTree);
        setupGitignoreTab();
        setupCommitsTab();
        setupCheatsheetTab();
        initI18n();
        setStatus(I18N.get("label.status_ready"));
    }

    private void initI18n() {
        if (titleLabel != null) titleLabel.textProperty().bind(I18N.getBinding("label.title"));
        if (mainTabPane != null && mainTabPane.getTabs().size() >= 3) {
            mainTabPane.getTabs().get(0).textProperty().bind(I18N.getBinding("tab.gitignore"));
            mainTabPane.getTabs().get(1).textProperty().bind(I18N.getBinding("tab.commits"));
            mainTabPane.getTabs().get(2).textProperty().bind(I18N.getBinding("tab.cheatsheet"));
        }
        if (selectLanguageLabel != null) selectLanguageLabel.textProperty().bind(I18N.getBinding("label.select_language"));
        if (gitignoreContentLabel != null) gitignoreContentLabel.textProperty().bind(I18N.getBinding("label.gitignore_content"));
        if (addTemplateButton != null) addTemplateButton.textProperty().bind(I18N.getBinding("btn.add"));
        if (clearTemplateButton != null) clearTemplateButton.textProperty().bind(I18N.getBinding("btn.clear"));
        if (copyGitignoreButton != null) copyGitignoreButton.textProperty().bind(I18N.getBinding("btn.copy"));
        if (saveGitignoreButton != null) saveGitignoreButton.textProperty().bind(I18N.getBinding("btn.save"));
        if (gitignoreArea != null) gitignoreArea.promptTextProperty().bind(I18N.getBinding("prompt.gitignore_edit"));
        if (repoPathLabel != null) repoPathLabel.textProperty().bind(I18N.getBinding("label.repo_path"));
        if (repoPathField != null) repoPathField.promptTextProperty().bind(I18N.getBinding("prompt.select_repo"));
        if (selectRepoButton != null) selectRepoButton.textProperty().bind(I18N.getBinding("btn.select"));
        if (loadCommitsButton != null) loadCommitsButton.textProperty().bind(I18N.getBinding("btn.load"));
        if (colHash != null) colHash.textProperty().bind(I18N.getBinding("col.hash"));
        if (colMessage != null) colMessage.textProperty().bind(I18N.getBinding("col.message"));
        if (colAuthor != null) colAuthor.textProperty().bind(I18N.getBinding("col.author"));
        if (colDate != null) colDate.textProperty().bind(I18N.getBinding("col.date"));
        if (searchCommandField != null) searchCommandField.promptTextProperty().bind(I18N.getBinding("prompt.search_command"));
        if (commandDetailLabel != null) commandDetailLabel.textProperty().bind(I18N.getBinding("label.command_detail"));
    }

    private void setupGitignoreTab() {
        // 填充语言选择框
        languageComboBox.getItems().addAll(gitIgnoreService.getAvailableTemplates());
        languageComboBox.selectFirst();

        // 绑定事件
        addTemplateButton.setOnAction(e -> onAddTemplate());
        clearTemplateButton.setOnAction(e -> onClearTemplate());
        copyGitignoreButton.setOnAction(e -> onCopyGitignore());
        saveGitignoreButton.setOnAction(e -> onSaveGitignore());
    }

    @SuppressWarnings("unchecked")
    private void setupCommitsTab() {
        // 设置表格列
        TableView<CommitInfo> table = (TableView<CommitInfo>) commitsTable;
        table.setItems(commitsList);

        TableColumn<CommitInfo, String> hashCol = (TableColumn<CommitInfo, String>) table.getColumns().get(0);
        hashCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().shortHash()));

        TableColumn<CommitInfo, String> msgCol = (TableColumn<CommitInfo, String>) table.getColumns().get(1);
        msgCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().message()));

        TableColumn<CommitInfo, String> authorCol = (TableColumn<CommitInfo, String>) table.getColumns().get(2);
        authorCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().author()));

        TableColumn<CommitInfo, String> dateCol = (TableColumn<CommitInfo, String>) table.getColumns().get(3);
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date()));

        // 绑定事件
        selectRepoButton.setOnAction(e -> onSelectRepo());
        loadCommitsButton.setOnAction(e -> onLoadCommits());
    }

    @SuppressWarnings("unchecked")
    private void setupCheatsheetTab() {
        TreeView<String> tree = (TreeView<String>) commandsTree;
        TreeItem<String> root = new TreeItem<>(I18N.get("cheatsheet.root"));
        root.setExpanded(true);

        for (CommandCategory category : GitCommandService.COMMAND_CHEATSHEET) {
            TreeItem<String> categoryItem = new TreeItem<>(I18N.get(category.name()));
            categoryItem.setExpanded(true);

            for (GitCommand cmd : category.commands()) {
                TreeItem<String> cmdItem = new TreeItem<>(I18N.get(cmd.name()));
                categoryItem.getChildren().add(cmdItem);
            }
            root.getChildren().add(categoryItem);
        }

        tree.setRoot(root);
        tree.setShowRoot(false);

        // 选择命令时显示详情
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.getParent() != null && newVal.getParent() != tree.getRoot()) {
                // Find category string
                String catKey = "";
                for (CommandCategory cat : GitCommandService.COMMAND_CHEATSHEET) {
                    for (GitCommand gc : cat.commands()) {
                        if (I18N.get(gc.name()).equals(newVal.getValue())) {
                            catKey = cat.name();
                            break;
                        }
                    }
                }
                showCommandDetail(newVal.getValue(), I18N.get(catKey));
            }
        });

        // 搜索过滤
        searchCommandField.textProperty().addListener((obs, old, newVal) -> {
            // 简单搜索逻辑
            if (newVal == null || newVal.isEmpty()) {
                tree.setRoot(root);
            }
        });
    }

    private void showCommandDetail(String commandI18nName, String categoryI18nName) {
        for (CommandCategory category : GitCommandService.COMMAND_CHEATSHEET) {
            for (GitCommand cmd : category.commands()) {
                if (I18N.get(cmd.name()).equals(commandI18nName)) {
                    String detail = I18N.get("template.command_detail",
                            I18N.get(cmd.name()),
                            cmd.command(),
                            I18N.get(cmd.description()),
                            categoryI18nName);
                    commandDetailArea.setText(detail);
                    return;
                }
            }
        }
    }

    private void onAddTemplate() {
        String selected = languageComboBox.getValue();
        if (selected != null && !selectedTemplates.contains(selected)) {
            selectedTemplates.add(selected);
            updateGitignoreContent();
            setStatus(I18N.get("label.status_added", selected));
        }
    }

    private void onClearTemplate() {
        selectedTemplates.clear();
        gitignoreArea.clear();
        setStatus(I18N.get("label.status_cleared"));
    }

    private void updateGitignoreContent() {
        String content = gitIgnoreService.combineTemplates(selectedTemplates);
        gitignoreArea.setText(content);
    }

    private void onCopyGitignore() {
        String content = gitignoreArea.getText();
        if (!content.isEmpty()) {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(content);
            Clipboard.getSystemClipboard().setContent(cc);
            setStatus(I18N.get("label.status_copied"));
        }
    }

    private void onSaveGitignore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("dialog.save_gitignore"));
        chooser.setInitialFileName(".gitignore");
        File file = chooser.showSaveDialog(mainStackPane.getScene().getWindow());

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(gitignoreArea.getText());
                setStatus(I18N.get("label.status_saved", file.getName()));
            } catch (Exception e) {
                setStatus(I18N.get("label.status_save_failed", e.getMessage()));
            }
        }
    }

    private void onSelectRepo() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18N.get("dialog.select_repo"));
        File dir = chooser.showDialog(mainStackPane.getScene().getWindow());

        if (dir != null) {
            if (gitCommandService.isGitRepo(dir.getAbsolutePath())) {
                repoPathField.setText(dir.getAbsolutePath());
                setStatus(I18N.get("label.status_repo_selected"));
            } else {
                setStatus(I18N.get("label.status_not_repo"));
            }
        }
    }

    private void onLoadCommits() {
        String path = repoPathField.getText().trim();
        if (path.isEmpty()) {
            setStatus(I18N.get("label.status_select_repo_first"));
            return;
        }

        setStatus(I18N.get("label.status_loading"));
        commitsList.clear();

        CompletableFuture.supplyAsync(() -> gitCommandService.getRecentCommits(path, 50), executor)
                .thenAccept(commits -> Platform.runLater(() -> {
                    if (disposed) return;
                    commitsList.addAll(commits);
                    setStatus(I18N.get("label.status_loaded", commits.size()));
                }));
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        gitCommandService.dispose();
        executor.shutdownNow();
    }
}
