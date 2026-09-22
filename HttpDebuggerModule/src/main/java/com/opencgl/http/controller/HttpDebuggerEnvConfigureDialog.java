package com.opencgl.http.controller;

import java.io.IOException;
import java.util.ResourceBundle;

import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.CommitOnBlurTableCell;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.http.i18n.I18N;
import com.opencgl.http.model.KeyValueEntry;
import com.opencgl.http.service.EnvironmentService;
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

        envList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressAutoSave) {
                return;
            }
            // 切走当前环境前，按「表单正在编辑的环境」落库，绝不能用已经变成 newVal 的选中项当旧名
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
            environmentService.deleteEnvironment(selected);
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

        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (KeyValueEntry kv : tableData) {
            if (kv.getKey() != null && !kv.getKey().trim().isEmpty()) {
                map.put(kv.getKey().trim(), kv.getValue() != null ? kv.getValue() : "");
            }
        }

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
                if (owner != null) com.opencgl.base.utils.DialogUtil.centerStageOnOwner(stage, owner);
            });
            stage.setOnHidden(event -> ThemeManager.getInstance().unregisterScene(stage.getScene()));

            init();
        }
        refreshEnvListAndSelect(loadedEnvName);
        stage.showAndWait();
    }
}
