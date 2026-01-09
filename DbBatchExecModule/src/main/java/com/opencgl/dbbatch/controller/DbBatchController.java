package com.opencgl.dbbatch.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.dbbatch.dao.DbTaskDao;
import com.opencgl.dbbatch.i18n.I18N;
import com.opencgl.dbbatch.model.DbTaskDto;
import com.opencgl.dbbatch.service.DbExecutorService;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXPasswordField;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class DbBatchController implements Initializable {

    @FXML
    private TreeView<DbTaskDto> taskTree;
    @FXML
    private MFXTextField nameField;
    @FXML
    private MFXComboBox<String> dbTypeCombo;
    @FXML
    private MFXTextField hostField;
    @FXML
    private MFXTextField portField;
    @FXML
    private MFXTextField userField;
    @FXML
    private MFXPasswordField passField;
    @FXML
    private MFXTextField dbNameField;
    @FXML
    private TextArea sqlArea;
    @FXML
    private TextArea replaceArea; // JSON 编辑
    @FXML
    private TextArea logArea;

    @FXML
    private MFXButton saveBtn;
    @FXML
    private MFXButton executeBtn;
    @FXML
    private MFXButton addGroupBtn;
    @FXML
    private MFXButton addTaskBtn;
    @FXML
    private MFXButton deleteBtn;

    private final DbTaskDao dao = new DbTaskDao();
    private final DbExecutorService executor = new DbExecutorService();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "db-batch-executor");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> executionFuture;
    private volatile boolean disposed;
    private DbTaskDto currentSelection;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initUI();
        loadTree();
        initI18n();
    }

    private void initI18n() {
    }

    private void initUI() {
        try {
            dao.checkTable();
        }
        catch (Exception e) {
            logError(I18N.get("msg.initDbFailed", e.getMessage()));
        }

        dbTypeCombo.setItems(FXCollections.observableArrayList("MYSQL", "ORACLE", "POSTGRESQL", "SQLSERVER", "SQLITE"));
        Platform.runLater(() -> {
            if (!disposed) dbTypeCombo.selectItem("MYSQL");
        });

        // TreeView Setup
        taskTree.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                currentSelection = newVal.getValue();
                loadToForm(currentSelection);
            }
            else {
                currentSelection = null;
            }
        });

        taskTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(DbTaskDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                }
                else {
                    setText(item.getName() + (item.isGroup() ? I18N.get("tree.group") : ""));
                    if (item.isGroup()) {
                        setStyle("-fx-font-weight: bold;");
                    }
                    else {
                        setStyle("");
                    }
                }
            }
        });

        // Events
        MenuItem runItem = new MenuItem(I18N.get("menu.execute"));
        runItem.setOnAction(e -> handleExecute());
        addGroupBtn.setOnAction(e -> addNode(true));
        addTaskBtn.setOnAction(e -> addNode(false));
        deleteBtn.setOnAction(e -> deleteCurrent());
    }

    private void loadTree() {
        try {
            List<DbTaskDto> all = dao.queryAll();
            TreeItem<DbTaskDto> root = new TreeItem<>(new DbTaskDto()); // Dummy root
            root.getValue().setName("Root");
            // display name for root is not shown (showRoot=false)

            // Build tree mapping
            Map<String, TreeItem<DbTaskDto>> map = new HashMap<>();
            List<DbTaskDto> roots = new ArrayList<>();
            Map<String, List<DbTaskDto>> groupChildren = new HashMap<>();

            for (DbTaskDto dto : all) {
                map.put(dto.getId(), new TreeItem<>(dto));
                if (dto.getParentId() == null || dto.getParentId().isEmpty()) {
                    roots.add(dto);
                }
                else {
                    groupChildren.computeIfAbsent(dto.getParentId(), k -> new ArrayList<>()).add(dto);
                }
            }

            // Assemble
            for (DbTaskDto r : roots) {
                TreeItem<DbTaskDto> item = map.get(r.getId());
                root.getChildren().add(item);
                buildChildren(item, groupChildren, map);
            }

            taskTree.setRoot(root);
            taskTree.setShowRoot(false);

        }
        catch (Exception e) {
            logError(I18N.get("msg.loadTreeFailed", e.getMessage()));
        }
    }

    private void buildChildren(TreeItem<DbTaskDto> parent, Map<String, List<DbTaskDto>> groupChildren, Map<String, TreeItem<DbTaskDto>> map) {
        List<DbTaskDto> children = groupChildren.get(parent.getValue().getId());
        if (children != null) {
            for (DbTaskDto child : children) {
                TreeItem<DbTaskDto> item = map.get(child.getId());
                parent.getChildren().add(item);
                buildChildren(item, groupChildren, map);
            }
        }
    }

    private void loadToForm(DbTaskDto dto) {
        nameField.setText(dto.getName());
        dbTypeCombo.selectItem(dto.getDbType() == null ? "MYSQL" : dto.getDbType());
        hostField.setText(dto.getHost());
        portField.setText(dto.getPort());
        userField.setText(dto.getUsername());
        passField.setText(dto.getPassword());
        dbNameField.setText(dto.getDatabase());
        sqlArea.setText(dto.getSqlContent());

        // Pretty Print JSON
        try {
            if (dto.getReplaceConfig() != null && !dto.getReplaceConfig().isEmpty()) {
                JSONObject json = JSON.parseObject(dto.getReplaceConfig());
                replaceArea.setText(JSON.toJSONString(json, SerializerFeature.PrettyFormat));
            }
            else {
                replaceArea.setText("{\n  \"key\": \"value\"\n}");
            }
        }
        catch (Exception e) {
            replaceArea.setText(dto.getReplaceConfig());
        }
    }

    // 从表单更新对象
    private void updateFromForm(DbTaskDto dto) {
        dto.setName(nameField.getText());
        dto.setDbType(dbTypeCombo.getValue());
        dto.setHost(hostField.getText());
        dto.setPort(portField.getText());
        dto.setUsername(userField.getText());
        dto.setPassword(passField.getText());
        dto.setDatabase(dbNameField.getText());
        dto.setSqlContent(sqlArea.getText());
        dto.setReplaceConfig(replaceArea.getText());
    }

    private void saveCurrent() {
        if (currentSelection == null) return;

        updateFromForm(currentSelection);

        try {
            if (dao.queryAll().stream().anyMatch(d -> d.getId().equals(currentSelection.getId()))) {
                dao.update(currentSelection);
            }
            else {
                dao.insert(currentSelection);
            }
            logInfo(I18N.get("msg.saveSuccess", currentSelection.getName()));

            // 刷新当前节点显示
            taskTree.refresh();

        }
        catch (Exception e) {
            logError(I18N.get("msg.saveFailed", e.getMessage()));
        }
    }

    private void addNode(boolean isGroup) {
        TextInputDialog dialog = new TextInputDialog(I18N.get("dialog.defaultTaskName"));
        dialog.setTitle(isGroup ? I18N.get("dialog.newGroup") : I18N.get("dialog.newTask"));
        dialog.setHeaderText(I18N.get("dialog.enterName"));

        dialog.showAndWait().ifPresent(name -> {
            DbTaskDto newItem = new DbTaskDto();
            newItem.setId(UUID.randomUUID().toString());
            newItem.setName(name);
            newItem.setGroup(isGroup); // isGroup

            // Parent logic
            TreeItem<DbTaskDto> selectedItem = taskTree.getSelectionModel().getSelectedItem();
            if (selectedItem != null && selectedItem.getValue().isGroup()) {
                newItem.setParentId(selectedItem.getValue().getId());
                try {
                    dao.insert(newItem);
                    TreeItem<DbTaskDto> newTreeItem = new TreeItem<>(newItem);
                    selectedItem.getChildren().add(newTreeItem);
                    selectedItem.setExpanded(true);
                }
                catch (Exception e) {
                    logError(I18N.get("msg.createFailed", e.getMessage()));
                }
            }
            else {
                try {
                    dao.insert(newItem);
                    taskTree.getRoot().getChildren().add(new TreeItem<>(newItem));
                }
                catch (Exception e) {
                    logError(I18N.get("msg.createFailed", e.getMessage()));
                }
            }
        });
    }

    private void deleteCurrent() {
        TreeItem<DbTaskDto> selectedItem = taskTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18N.get("msg.confirmDelete"));
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    dao.delete(selectedItem.getValue().getId());
                    selectedItem.getParent().getChildren().remove(selectedItem);
                    logInfo(I18N.get("msg.deleteSuccess"));
                }
                catch (Exception e) {
                    logError(I18N.get("msg.deleteFailed", e.getMessage()));
                }
            }
        });
    }

    @FXML
    private void handlePreview() {
        if (currentSelection == null || currentSelection.isGroup()) {
            DialogUtil.showErrorInfo(I18N.get("msg.selectTask"));
            return;
        }

        saveCurrent();

        try {
            List<String> sqls = executor.generateSqls(currentSelection);
            logArea.clear();
            logArea.appendText("==== SQL PREVIEW (" + sqls.size() + " statements) ====\n\n");
            for (int i = 0; i < sqls.size(); i++) {
                logArea.appendText("--- Statement " + (i + 1) + " ---\n");
                logArea.appendText(sqls.get(i) + "\n\n");
            }
            logArea.appendText("==== END PREVIEW ====\n");
        }
        catch (Exception e) {
            logError(I18N.get("msg.previewFailed", e.getMessage()));
            e.printStackTrace();
        }
    }

    @FXML
    private void handleHelp() {
        String helpText = I18N.get("help.content");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18N.get("title.help"));
        alert.setHeaderText(I18N.get("header.help"));

        TextArea textArea = new TextArea(helpText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        // Arrange neatly
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(textArea, 0, 0);

        alert.getDialogPane().setContent(expContent);
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(600, 500); // 增加尺寸以容纳更多内容

        alert.showAndWait();
    }

    @FXML
    private void handleExecute() {
        if (currentSelection == null || currentSelection.isGroup()) {
            DialogUtil.showErrorInfo(I18N.get("msg.selectTask"));
            return;
        }

        saveCurrent();

        logArea.clear();
        logInfo(I18N.get("msg.execStart", currentSelection.getName()));

        executionFuture = backgroundExecutor.submit(() -> {
            try {
                executor.executeTask(currentSelection, (msg) -> {
                    Platform.runLater(() -> {
                        if (!disposed) logInfo(msg);
                    });
                });
                Platform.runLater(() -> {
                    if (disposed) return;
                    logInfo(I18N.get("msg.execSuccess"));
                    DialogUtil.showSuccessInfo(I18N.get("msg.execSuccess"));
                });
            }
            catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    logError(I18N.get("msg.execFailed", e.getMessage()));
                    e.printStackTrace();
                });
            }
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (executionFuture != null) executionFuture.cancel(true);
        executor.close();
        backgroundExecutor.shutdownNow();
    }

    private void logInfo(String msg) {
        logArea.appendText("[INFO] " + msg + "\n");
    }

    private void logError(String msg) {
        logArea.appendText("[ERROR] " + msg + "\n");
    }
}
    
