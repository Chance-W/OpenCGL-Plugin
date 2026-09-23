package com.opencgl.http.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;

import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.CommitOnBlurTableCell;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.http.i18n.I18N;
import com.opencgl.http.model.KeyValueEntry;
import com.opencgl.http.service.EnvironmentService;
import com.opencgl.http.ui.EnvironmentWindowPlacement;
import com.opencgl.base.view.CustomConfirmDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * HTTP 调试器环境配置弹框
 * 布局与交互完全参考 DubboServiceTestPlugin 的 DubboEnvConfigureDialog。
 */
public class HttpDebuggerEnvConfigureDialog {

    private static final String FXML_FILE = "/com/opencgl/http/views/HttpDebuggerEnvConfigureView.fxml";

    @FXML
    private StackPane envConfigRoot;
    @FXML
    private javafx.scene.control.SplitPane splitPane;
    @FXML
    private javafx.scene.control.Label envListLabel;
    @FXML
    private javafx.scene.control.ListView<String> envList;
    @FXML
    private Button newButton;
    @FXML
    private Button deleteButton;
    @FXML private Button copyEnvButton;
    @FXML private Button exportEnvButton;
    @FXML private Button importEnvButton;
    @FXML
    private javafx.scene.control.Label envConfigLabel;
    @FXML
    private javafx.scene.control.Label envNameLabel;
    @FXML
    private TextField envNameField;
    @FXML
    private Button addVarBtn;
    @FXML
    private TableView<KeyValueEntry> varTable;
    @FXML
    private TableColumn<KeyValueEntry, String> keyCol;
    @FXML
    private TableColumn<KeyValueEntry, String> valueCol;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;

    private Stage stage;
    private final EnvironmentService environmentService = new EnvironmentService();
    private final ObservableList<KeyValueEntry> tableData = FXCollections.observableArrayList();

    /** 当前表单对应的环境名；新建未保存时为 null。切列表时必须用它做身份，不能用 ListView 的新选中项。 */
    private String loadedEnvName;

    /** 防止 refreshEnvList/select 引发的选择事件递归触发 persist */
    private boolean suppressAutoSave = false;

    public void init() {
        if (envConfigRoot != null && !envConfigRoot.getStyleClass().contains("root")) {
            envConfigRoot.getStyleClass().add("root");
        }
        if (splitPane != null)
            splitPane.setDividerPositions(0.3);

        refreshEnvList();
        copyEnvButton.disableProperty().bind(envList.getSelectionModel().selectedItemProperty().isNull());
        exportEnvButton.disableProperty().bind(envList.getSelectionModel().selectedItemProperty().isNull());
        copyEnvButton.setOnAction(e -> transferEnvironment("copy"));
        exportEnvButton.setOnAction(e -> transferEnvironment("export"));
        importEnvButton.setOnAction(e -> transferEnvironment("import"));
        envList.setContextMenu(environmentMenu(false));
        envList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(empty ? null : name);
                setContextMenu(environmentMenu(!empty));
            }
            {
                setOnContextMenuRequested(event -> {
                    varTable.edit(-1, null);
                    if (!isEmpty()) envList.getSelectionModel().select(getItem());
                });
            }
        });

        envList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressAutoSave) {
                return;
            }
            // 切走当前环境前，按「表单正在编辑的环境」落库，绝不能用已经变成 newVal 的选中项当旧名
            varTable.edit(-1, null);
            persistCurrentForm(false);
            if (newVal != null) {
                loadEnvDetails(newVal);
            } else {
                clearForm();
            }
        });

        newButton.setOnAction(e -> {
            persistCurrentForm(false);
            suppressAutoSave = true;
            try {
                envList.getSelectionModel().clearSelection();
            } finally {
                suppressAutoSave = false;
            }
            clearForm();
            envNameField.requestFocus();
        });

        deleteButton.setOnAction(e -> {
            String selected = envList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (EnvironmentService.DEFAULT_ENV_NAME.equals(selected)) {
                DialogUtil.showErrorInfo(I18N.get("dialog.env.error.cannot_delete_default"), stage);
                return;
            }
            try {
                environmentService.deleteEnvironment(selected);
            } catch (Exception failure) {
                DialogUtil.showErrorInfo(I18N.get("msg.environment_delete_failed"), stage);
                return;
            }
            clearForm();
            refreshEnvListAndSelect(null);
        });

        addVarBtn.setOnAction(e -> tableData.add(new KeyValueEntry("", "", true)));

        envNameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                persistCurrentForm(false);
            }
        });

        saveButton.setOnAction(e -> {
            String newName = envNameField.getText();
            if (newName == null || newName.trim().isEmpty()) {
                DialogUtil.showErrorInfo(I18N.get("dialog.env.error.name_required"), stage);
                return;
            }
            if (persistCurrentForm(true)) {
                TooltipUtil.showToast(envConfigRoot, I18N.get("msg.save_success"));
            }
        });

        cancelButton.setOnAction(e -> stage.close());

        varTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        keyCol.setCellValueFactory(cd -> cd.getValue().keyProperty());
        keyCol.setCellFactory(CommitOnBlurTableCell.forStringColumn());
        // 单元格编辑提交后自动保存
        keyCol.setOnEditCommit(ev -> {
            ev.getRowValue().setKey(ev.getNewValue());
            persistCurrentForm(false);
        });
        valueCol.setCellValueFactory(cd -> cd.getValue().valueProperty());
        valueCol.setCellFactory(CommitOnBlurTableCell.forStringColumn());
        valueCol.setOnEditCommit(ev -> {
            ev.getRowValue().setValue(ev.getNewValue());
            persistCurrentForm(false);
        });
        varTable.setItems(tableData);
    }

    private javafx.scene.control.ContextMenu environmentMenu(boolean hasItem) {
        var menu = new javafx.scene.control.ContextMenu();
        for (String action : java.util.List.of("copy", "export", "import")) {
            var item = new javafx.scene.control.MenuItem(I18N.get("dialog.env.button." + action));
            item.setDisable(!hasItem && !"import".equals(action));
            item.setOnAction(event -> transferEnvironment(action));
            menu.getItems().add(item);
        }
        return menu;
    }

    private void transferEnvironment(String action) {
        try {
            varTable.edit(-1, null);
            if ("import".equals(action)) {
                var file = environmentChooser().showOpenDialog(fileDialogOwner());
                if (file == null) return;
                if (Files.size(file.toPath()) > 5 * 1024 * 1024) throw new IllegalArgumentException("File too large");
                String name = environmentService.importEnvironment(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                refreshEnvListAndSelect(name);
                loadEnvDetails(name);
            } else {
                if (envList.getSelectionModel().getSelectedItem() == null) return;
                String source = envList.getSelectionModel().getSelectedItem();
                var snapshot = currentVariables();
                if ("copy".equals(action)) {
                    String name = environmentService.copyEnvironment(source, snapshot);
                    refreshEnvListAndSelect(name);
                    loadEnvDetails(name);
                } else {
                    if (!exportConfirmation().showAndWait().orElse(false)) return;
                    var chooser = environmentChooser();
                    chooser.setInitialFileName("http-environment.json");
                    var file = chooser.showSaveDialog(fileDialogOwner());
                    if (file == null) return;
                    Files.writeString(file.toPath(), environmentService.exportEnvironment(source, snapshot), StandardCharsets.UTF_8);
                }
            }
            TooltipUtil.showToast(envConfigRoot, I18N.get("dialog.env.transfer_success"));
        } catch (Exception failure) {
            DialogUtil.showErrorInfo(I18N.get("dialog.env.transfer_failed"), stage);
        }
    }

    private javafx.stage.FileChooser environmentChooser() {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle(I18N.get("dialog.env.list.title"));
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("OpenCGL HTTP Environment (*.json)", "*.json"));
        return chooser;
    }

    private Window fileDialogOwner() {
        Window owner = envConfigRoot.getScene().getWindow();
        if (owner instanceof Stage current) current.toFront();
        owner.requestFocus();
        return owner;
    }

    private CustomConfirmDialog exportConfirmation() {
        Window owner = fileDialogOwner();
        var dialog = new CustomConfirmDialog();
        dialog.initOwner(owner);
        dialog.setCustomHeaderText(I18N.get("dialog.env.button.export"));
        dialog.setLabelText(I18N.get("dialog.env.export_warning"));
        dialog.setOnShowing(event -> {
            var pane = dialog.getDialogPane();
            double width = Math.max(400, pane.prefWidth(-1));
            double height = Math.max(150, pane.prefHeight(width));
            var point = EnvironmentWindowPlacement.position(owner, width, height);
            dialog.setX(point.getX()); dialog.setY(point.getY());
        });
        // Replace the shared component's active-window guessing, retaining theme registration.
        dialog.setOnShown(event -> {
            ThemeManager.getInstance().registerScene(dialog.getDialogPane().getScene());
            var point = EnvironmentWindowPlacement.position(owner, dialog.getWidth(), dialog.getHeight());
            dialog.setX(point.getX()); dialog.setY(point.getY());
        });
        return dialog;
    }

    /**
     * 将当前表单持久化。身份以 {@link #loadedEnvName} 为准，而不是 ListView 当前选中项。
     *
     * @param syncList 为 true 时刷新左侧列表并选中刚保存的环境（仅保存按钮）；
     *                 切列表 / 失焦时必须为 false，避免 setItems 冲掉用户正在点的那一项。
     * @return 是否实际写入
     */
    private boolean persistCurrentForm(boolean syncList) {
        if (suppressAutoSave) {
            return false;
        }
        String name = envNameField.getText();
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String trimmedName = name.trim();
        if (EnvironmentService.DEFAULT_ENV_NAME.equals(trimmedName)) {
            return false;
        }

        if (!trimmedName.equals(loadedEnvName) && environmentService.getEnvironmentNames().contains(trimmedName)) {
            if (syncList) DialogUtil.showErrorInfo(I18N.get("dialog.env.name_exists"), stage);
            return false;
        }

        java.util.Map<String, String> map = currentVariables();

        String previousName = loadedEnvName;
        if (previousName != null && !previousName.equals(trimmedName)) {
            environmentService.deleteEnvironment(previousName);
        }
        environmentService.updateEnvironment(trimmedName, map);
        loadedEnvName = trimmedName;

        if (syncList) {
            refreshEnvListAndSelect(trimmedName);
        } else {
            syncListItemsInPlace(previousName, trimmedName);
        }
        return true;
    }

    private java.util.Map<String, String> currentVariables() {
        var values = new java.util.LinkedHashMap<String, String>();
        for (KeyValueEntry kv : tableData) {
            if (kv.getKey() != null && !kv.getKey().trim().isEmpty()) {
                values.put(kv.getKey().trim(), kv.getValue() == null ? "" : kv.getValue());
            }
        }
        return values;
    }

    private void syncListItemsInPlace(String previousName, String savedName) {
        ObservableList<String> items = envList.getItems();
        if (items == null || savedName == null) {
            return;
        }
        if (previousName != null && !previousName.equals(savedName) && items.contains(previousName)) {
            int idx = items.indexOf(previousName);
            items.remove(previousName);
            if (!items.contains(savedName)) {
                items.add(Math.min(idx, items.size()), savedName);
            }
            return;
        }
        if (!items.contains(savedName)) {
            items.add(savedName);
        }
    }

    private void refreshEnvList() {
        envList.setItems(FXCollections.observableArrayList(environmentService.getEnvironmentNames()));
    }

    private void refreshEnvListAndSelect(String name) {
        suppressAutoSave = true;
        try {
            refreshEnvList();
            if (name != null) {
                envList.getSelectionModel().select(name);
            } else {
                envList.getSelectionModel().clearSelection();
            }
        } finally {
            suppressAutoSave = false;
        }
    }

    private void loadEnvDetails(String envName) {
        loadedEnvName = envName;
        envNameField.setText(envName);
        tableData.clear();
        java.util.Map<String, String> vars = environmentService.getEnvironment(envName);
        vars.forEach((k, v) -> tableData.add(new KeyValueEntry(k, v, true)));
    }

    private void clearForm() {
        loadedEnvName = null;
        envNameField.clear();
        tableData.clear();
    }

    /**
     * 显示环境配置弹窗，相对 owner 居中（多屏/插件窗口时与当前操作窗口同屏）。
     * @param owner 所属窗口，可为 null（不设置时按系统默认）
     */
    public void showAndWait(Window owner) throws IOException {
        if (stage == null) {
            stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            if (owner != null) stage.initOwner(owner);

            FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_FILE));
            loader.setController(this);
            ResourceBundle bundle = I18N.getBundle(I18N.getLocale());
            loader.setResources(bundle);
            Scene scene = new Scene(loader.load(), 800, 500);
            scene.setFill(Color.TRANSPARENT);

            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                    event.consume();
                }
            });

            stage.setScene(scene);
            stage.setOnShown(event -> {
                ThemeManager.getInstance().registerScene(stage.getScene());
            });
            stage.setOnHidden(event -> ThemeManager.getInstance().unregisterScene(stage.getScene()));

            init();
        }
        refreshEnvListAndSelect(loadedEnvName);
        EnvironmentWindowPlacement.placeBeforeShow(stage, owner);
        stage.showAndWait();
    }
}
