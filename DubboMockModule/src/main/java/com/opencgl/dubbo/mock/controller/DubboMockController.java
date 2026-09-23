package com.opencgl.dubbo.mock.controller;

import com.opencgl.base.view.CustomInfoDialog;
import com.opencgl.dubbo.mock.engine.DubboMockProcessManager;
import com.opencgl.dubbo.mock.i18n.I18N;
import com.opencgl.dubbo.mock.model.DubboMockStoreManager;
import com.opencgl.dubbo.mock.model.MethodMockConfig;
import com.opencgl.dubbo.mock.model.ProviderNodeModel;
import com.opencgl.dubbo.mock.model.RegistryNodeModel;
import com.opencgl.dubbo.mock.service.DubboMockService;
import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.base.utils.LoadingUtil;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class DubboMockController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(DubboMockController.class);
    private volatile boolean disposed;
    private final java.util.Set<Thread> workerThreads = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // 拖拽 MIME 类型标识
    private static final DataFormat TREE_ITEM_FORMAT = new DataFormat("application/x-dubbo-mock-tree-item");

    @FXML private StackPane mainStackPane;
    @FXML private TreeView<Object> mockTreeView;

    @FXML private Button addRegistryBtn;
    @FXML private Button refreshTreeBtn;

    @FXML private StackPane contentArea;
    @FXML private VBox emptyState;
    @FXML private VBox registryConfigPane;
    @FXML private VBox providerConfigPane;
    @FXML private VBox methodRulePane;

    // Registry Form
    @FXML private TextField registryNameField;
    @FXML private ComboBox<String> registryTypeCombo;
    @FXML private TextField registryAddressField;
    @FXML private TextField registryZkGroupField;
    @FXML private TextField registryProtocolField;
    @FXML private TextField registryPortField;
    @FXML private TextField registryAppField;
    @FXML private TextField providerHostField;
    @FXML private CheckBox noRegistryCheck;
    @FXML private Button saveRegistryBtn;
    @FXML private Button deleteRegistryBtn;

    // Provider Form
    @FXML private TextField providerInterfaceField;
    @FXML private TextField providerVersionField;
    @FXML private TextField providerGroupField;
    @FXML private Button startProviderBtn;
    @FXML private Button stopProviderBtn;
    @FXML private Button saveProviderBtn;
    @FXML private Button deleteProviderBtn;
    @FXML private CustomTextArea providerLogArea;

    // Method Form
    @FXML private TextField methodNameField;
    @FXML private TextField methodDelayField;
    @FXML private CustomTextArea methodResponseArea;
    @FXML private Button saveMethodBtn;
    @FXML private Button deleteMethodBtn;

    private DubboMockStoreManager storeManager;
    private Object currentSelectedData = null;

    // 拖拽时暂存的源节点
    private TreeItem<Object> dragSourceItem = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        com.opencgl.base.utils.tree.TreeViewPresentation.install(mockTreeView);
        log.info("DubboMockController initialized.");
        storeManager = new DubboMockStoreManager();

        registryTypeCombo.getItems().addAll("zookeeper", "nacos");

        setupTreeView();
        setupActions();

        reloadTree();
        initI18n();
    }

    private void initI18n() {
        addRegistryBtn.textProperty().bind(I18N.getBinding("button.add_registry"));
        refreshTreeBtn.textProperty().bind(I18N.getBinding("button.refresh"));
    }

    // ==================== TreeView 设置 ====================

    private void setupTreeView() {
        mockTreeView.setShowRoot(false);
        TreeItem<Object> root = new TreeItem<>("ROOT");
        mockTreeView.setRoot(root);
        mockTreeView.setEditable(true);

        mockTreeView.setCellFactory(tv -> new DubboMockTreeCell());

        mockTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                handleTreeSelection(newVal.getValue());
            } else {
                handleTreeSelection(null);
            }
        });
    }

    // ==================== 自定义 TreeCell（含双击重命名 + 拖拽） ====================

    private class DubboMockTreeCell extends TreeCell<Object> {
        private TextField editField;
        private boolean explicitCancel;

        DubboMockTreeCell() {
            setupDragAndDrop();
        }

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
            } else {
                if (isEditing()) {
                    if (editField != null) {
                        editField.setText(getEditableText(item));
                    }
                    setText(null);
                    setGraphic(editField);
                } else {
                    setText(getDisplayText(item));
                    setGraphic(null);
                    setContextMenu(createContextMenu(item));
                }
            }
        }

        @Override
        public void startEdit() {
            super.startEdit();
            Object item = getItem();
            if (item == null) return;
            explicitCancel = false;

            editField = new TextField(getEditableText(item));
            editField.setOnAction(e -> commitEditAction());
            editField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused && !explicitCancel) {
                    commitEditAction();
                }
            });
            // 按 ESC 取消
            editField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    explicitCancel = true;
                    cancelEdit();
                    e.consume();
                }
            });

            setText(null);
            setGraphic(editField);
            editField.selectAll();
            editField.requestFocus();
        }

        @Override
        public void cancelEdit() {
            if (isEditing() && !explicitCancel) {
                commitEditAction();
                return;
            }
            super.cancelEdit();
            explicitCancel = false;
            setText(getDisplayText(getItem()));
            setGraphic(null);
        }

        private void commitEditAction() {
            Object item = getItem();
            if (item == null || editField == null) return;
            String newText = editField.getText().trim();
            if (newText.isEmpty()) {
                explicitCancel = true;
                cancelEdit();
                return;
            }

            // 将新名字写入模型并持久化
            if (item instanceof RegistryNodeModel) {
                ((RegistryNodeModel) item).setName(newText);
                storeManager.updateRegistry((RegistryNodeModel) item);
            } else if (item instanceof ProviderNodeModel) {
                ((ProviderNodeModel) item).setInterfaceName(newText);
                storeManager.updateProvider((ProviderNodeModel) item);
            } else if (item instanceof MethodMockConfig) {
                ((MethodMockConfig) item).setMethodName(newText);
                storeManager.updateMethod((MethodMockConfig) item);
            }

            commitEdit(item);
            // 同步右侧面板
            handleTreeSelection(item);
        }

        // ---------- 拖拽支持 ----------

        private void setupDragAndDrop() {
            setOnDragDetected(event -> {
                Object item = getItem();
                if (item == null) return;
                // 仅允许拖拽 Provider 和 Method 节点
                if (item instanceof ProviderNodeModel || item instanceof MethodMockConfig) {
                    Dragboard db = startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.put(TREE_ITEM_FORMAT, "drag");
                    db.setContent(content);
                    dragSourceItem = getTreeItem();
                    event.consume();
                }
            });

            setOnDragOver(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasContent(TREE_ITEM_FORMAT)) {
                    TreeItem<Object> targetItem = getTreeItem();
                    if (targetItem != null && dragSourceItem != null && canDrop(dragSourceItem, targetItem)) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                }
                event.consume();
            });

            setOnDragEntered(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasContent(TREE_ITEM_FORMAT)) {
                    setStyle("-fx-border-color: #3498db; -fx-border-width: 0 0 2 0;");
                }
            });

            setOnDragExited(event -> {
                setStyle("");
            });

            setOnDragDropped(event -> {
                boolean success = false;
                if (dragSourceItem != null) {
                    TreeItem<Object> targetItem = getTreeItem();
                    if (targetItem != null) {
                        performDrop(dragSourceItem, targetItem);
                        success = true;
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });

            setOnDragDone(event -> {
                dragSourceItem = null;
                event.consume();
            });
        }
    }

    /** 判断源是否可以拖放到目标位置 */
    private boolean canDrop(TreeItem<Object> source, TreeItem<Object> target) {
        Object srcData = source.getValue();
        Object tgtData = target.getValue();

        if (srcData instanceof ProviderNodeModel) {
            // Provider 可以拖到另一个 Provider 旁边（同级排序）或拖到一个 Registry 下
            return tgtData instanceof ProviderNodeModel || tgtData instanceof RegistryNodeModel;
        } else if (srcData instanceof MethodMockConfig) {
            // Method 可以拖到另一个 Method 旁边（同级排序）或拖到一个 Provider 下
            return tgtData instanceof MethodMockConfig || tgtData instanceof ProviderNodeModel;
        }

        return false;
    }

    /** 执行拖放操作 */
    private void performDrop(TreeItem<Object> source, TreeItem<Object> target) {
        Object srcData = source.getValue();
        Object tgtData = target.getValue();

        if (srcData instanceof ProviderNodeModel) {
            ProviderNodeModel srcProv = (ProviderNodeModel) srcData;
            TreeItem<Object> srcParent = source.getParent();
            srcParent.getChildren().remove(source);

            if (tgtData instanceof RegistryNodeModel) {
                // 将 Provider 拖到另一个 Registry 下
                RegistryNodeModel targetReg = (RegistryNodeModel) tgtData;
                removeProviderFromMemory(srcProv);
                targetReg.getProviders().add(srcProv);
                storeManager.moveProvider(srcProv.getId(), targetReg.getId());
            } else if (tgtData instanceof ProviderNodeModel) {
                // 同层排序：在目标 Provider 前面插入
                TreeItem<Object> tgtParent = target.getParent();
                RegistryNodeModel parentReg = (RegistryNodeModel) tgtParent.getValue();

                removeProviderFromMemory(srcProv);
                int targetIdx = parentReg.getProviders().indexOf(tgtData);
                if (targetIdx < 0) targetIdx = parentReg.getProviders().size();
                parentReg.getProviders().add(targetIdx, srcProv);

                // 更新 registry_id 和排序
                storeManager.moveProvider(srcProv.getId(), parentReg.getId());
                List<String> orderedIds = parentReg.getProviders().stream()
                    .map(ProviderNodeModel::getId).collect(Collectors.toList());
                storeManager.updateSortOrders("dubbo_mock_provider", orderedIds);
            }
        } else if (srcData instanceof MethodMockConfig) {
            MethodMockConfig srcMethod = (MethodMockConfig) srcData;
            TreeItem<Object> srcParent = source.getParent();
            srcParent.getChildren().remove(source);

            if (tgtData instanceof ProviderNodeModel) {
                // 将 Method 拖到另一个 Provider 下
                ProviderNodeModel targetProv = (ProviderNodeModel) tgtData;
                removeMethodFromMemory(srcMethod);
                targetProv.getMethods().add(srcMethod);
                storeManager.moveMethod(srcMethod.getId(), targetProv.getId());
            } else if (tgtData instanceof MethodMockConfig) {
                // 同层排序：在目标 Method 前面插入
                TreeItem<Object> tgtParent = target.getParent();
                ProviderNodeModel parentProv = (ProviderNodeModel) tgtParent.getValue();

                removeMethodFromMemory(srcMethod);
                int targetIdx = parentProv.getMethods().indexOf(tgtData);
                if (targetIdx < 0) targetIdx = parentProv.getMethods().size();
                parentProv.getMethods().add(targetIdx, srcMethod);

                storeManager.moveMethod(srcMethod.getId(), parentProv.getId());
                List<String> orderedIds = parentProv.getMethods().stream()
                    .map(MethodMockConfig::getId).collect(Collectors.toList());
                storeManager.updateSortOrders("dubbo_mock_method", orderedIds);
            }
        }

        reloadTree();
    }

    // ==================== 辅助方法 ====================

    private String getDisplayText(Object item) {
        if (item instanceof RegistryNodeModel) {
            return "🌍 " + ((RegistryNodeModel) item).getName();
        } else if (item instanceof ProviderNodeModel) {
            ProviderNodeModel p = (ProviderNodeModel) item;
            String activeMark = p.isRunning() ? "🟢 " : "🔴 ";
            return activeMark + p.getDisplayName();
        } else if (item instanceof MethodMockConfig) {
            return "⚡ " + ((MethodMockConfig) item).getDisplayName();
        }
        return item.toString();
    }

    private String getEditableText(Object item) {
        if (item instanceof RegistryNodeModel) {
            return ((RegistryNodeModel) item).getName();
        } else if (item instanceof ProviderNodeModel) {
            return ((ProviderNodeModel) item).getInterfaceName();
        } else if (item instanceof MethodMockConfig) {
            return ((MethodMockConfig) item).getMethodName();
        }
        return item.toString();
    }

    private void removeProviderFromMemory(ProviderNodeModel provider) {
        for (RegistryNodeModel r : storeManager.getRegistryNodes()) {
            r.getProviders().remove(provider);
        }
    }

    private void removeMethodFromMemory(MethodMockConfig method) {
        for (RegistryNodeModel r : storeManager.getRegistryNodes()) {
            for (ProviderNodeModel p : r.getProviders()) {
                p.getMethods().remove(method);
            }
        }
    }

    // ==================== 树重载 ====================

    private void reloadTree() {
        // 由于改为容器引擎化，原有的 DubboMockService.syncRunningState 废弃。
        // 子进程被杀死或存活状态在 ProcessManager 维护。这里仅刷新 UI。

        TreeItem<Object> root = mockTreeView.getRoot();
        root.getChildren().clear();

        for (RegistryNodeModel reg : storeManager.getRegistryNodes()) {
            TreeItem<Object> regItem = new TreeItem<>(reg);
            regItem.setExpanded(true);
            for (ProviderNodeModel prov : reg.getProviders()) {
                TreeItem<Object> provItem = new TreeItem<>(prov);
                provItem.setExpanded(true);
                for (MethodMockConfig method : prov.getMethods()) {
                    provItem.getChildren().add(new TreeItem<>(method));
                }
                regItem.getChildren().add(provItem);
            }
            root.getChildren().add(regItem);
        }
    }

    // ==================== 面板切换 ====================

    private void switchPane(VBox targetPane) {
        // 隐藏所有面板
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        registryConfigPane.setVisible(false);
        registryConfigPane.setManaged(false);
        providerConfigPane.setVisible(false);
        providerConfigPane.setManaged(false);
        methodRulePane.setVisible(false);
        methodRulePane.setManaged(false);

        if (targetPane == null) {
            emptyState.setVisible(true);
            emptyState.setManaged(true);
        } else {
            targetPane.setVisible(true);
            targetPane.setManaged(true);
        }
    }

    // ==================== 树选择处理 ====================

    private void handleTreeSelection(Object data) {
        this.currentSelectedData = data;
        if (data == null) {
            switchPane(null);
            return;
        }

        if (data instanceof RegistryNodeModel) {
            switchPane(registryConfigPane);
            RegistryNodeModel r = (RegistryNodeModel) data;
            registryNameField.setText(r.getName());
            registryTypeCombo.setValue(r.getRegistryType());
            registryAddressField.setText(r.getRegistryAddress());
            registryZkGroupField.setText(r.getZkGroup() != null ? r.getZkGroup() : "");
            registryProtocolField.setText(r.getProtocol());
            registryPortField.setText(String.valueOf(r.getPort()));
            registryAppField.setText(r.getApplicationName());
            providerHostField.setText(r.getProviderHost() != null ? r.getProviderHost() : "");
            noRegistryCheck.setSelected(r.isNoRegistry());
        }
        else if (data instanceof ProviderNodeModel) {
            switchPane(providerConfigPane);
            ProviderNodeModel p = (ProviderNodeModel) data;
            providerInterfaceField.setText(p.getInterfaceName());
            providerVersionField.setText(p.getVersion());
            providerGroupField.setText(p.getGroup());

            // 挂载日志回调
            p.setLogListener(msg -> {
                runOnUi(() -> {
                    if (currentSelectedData == p) {
                        providerLogArea.insertText(providerLogArea.getLength(), msg + "\n");
                    }
                });
            });

            updateProviderButtons(p);
        }
        else if (data instanceof MethodMockConfig) {
            switchPane(methodRulePane);
            MethodMockConfig m = (MethodMockConfig) data;
            methodNameField.setText(m.getMethodName());
            methodDelayField.setText(String.valueOf(m.getDelayMs()));
            methodResponseArea.setText(m.getResponseJson());
        }
    }

    /** 仅刷新启动/停止按钮状态，不重新赋值表单 */
    private void updateProviderButtons(ProviderNodeModel p) {
        startProviderBtn.setDisable(p.isRunning());
        stopProviderBtn.setDisable(!p.isRunning());
    }

    // ==================== 右键菜单 ====================

    private ContextMenu createContextMenu(Object item) {
        ContextMenu menu = new ContextMenu();

        if (item instanceof RegistryNodeModel) {
            MenuItem addProv = new MenuItem(I18N.get("menu.add_provider"));
            addProv.setOnAction(e -> {
                RegistryNodeModel r = (RegistryNodeModel) item;
                ProviderNodeModel p = new ProviderNodeModel();
                p.setInterfaceName(I18N.get("default.new_service"));
                r.getProviders().add(p);
                storeManager.insertProvider(r.getId(), p);
                reloadTree();
            });
            menu.getItems().add(addProv);
        }

        if (item instanceof ProviderNodeModel) {
            MenuItem addMethod = new MenuItem(I18N.get("menu.add_method"));
            addMethod.setOnAction(e -> {
                ProviderNodeModel p = (ProviderNodeModel) item;
                MethodMockConfig m = new MethodMockConfig();
                p.getMethods().add(m);
                // 找到所属 registry
                RegistryNodeModel parentReg = findParentRegistry(p);
                storeManager.insertMethod(p.getId(), m);
                reloadTree();
            });
            menu.getItems().add(addMethod);
        }

        return menu.getItems().isEmpty() ? null : menu;
    }

    // ==================== 按钮事件绑定 ====================

    private void setupActions() {
        // 添加注册中心
        addRegistryBtn.setOnAction(e -> {
            RegistryNodeModel r = new RegistryNodeModel();
            r.setName(I18N.get("default.new_registry"));
            storeManager.insertRegistry(r);
            storeManager.getRegistryNodes().add(r);
            reloadTree();
        });

        // 刷新
        refreshTreeBtn.setOnAction(e -> {
            storeManager.loadAll();
            reloadTree();
        });

        // ---- Registry 保存 ----
        saveRegistryBtn.setOnAction(e -> {
            if (currentSelectedData instanceof RegistryNodeModel) {
                RegistryNodeModel r = (RegistryNodeModel) currentSelectedData;
                r.setName(registryNameField.getText());
                r.setRegistryType(registryTypeCombo.getValue());
                r.setRegistryAddress(registryAddressField.getText());
                r.setZkGroup(registryZkGroupField.getText());
                r.setProtocol(registryProtocolField.getText());
                try {
                    r.setPort(Integer.parseInt(registryPortField.getText()));
                } catch (NumberFormatException ex) {
                    log.error("Port must be integer");
                }
                r.setApplicationName(registryAppField.getText());
                r.setProviderHost(providerHostField.getText());
                r.setNoRegistry(noRegistryCheck.isSelected());
                storeManager.updateRegistry(r);
                mockTreeView.refresh();
            }
        });

        // ---- Registry 删除 ----
        deleteRegistryBtn.setOnAction(e -> {
            if (currentSelectedData instanceof RegistryNodeModel) {
                RegistryNodeModel r = (RegistryNodeModel) currentSelectedData;
                storeManager.deleteRegistry(r.getId());
                storeManager.getRegistryNodes().remove(r);
                reloadTree();
            }
        });

        // ---- Provider 保存 ----
        saveProviderBtn.setOnAction(e -> {
            if (currentSelectedData instanceof ProviderNodeModel) {
                ProviderNodeModel p = (ProviderNodeModel) currentSelectedData;
                p.setInterfaceName(providerInterfaceField.getText());
                p.setVersion(providerVersionField.getText());
                p.setGroup(providerGroupField.getText());
                storeManager.updateProvider(p);
                mockTreeView.refresh();
            }
        });

        // ---- Provider 删除 ----
        deleteProviderBtn.setOnAction(e -> {
            if (currentSelectedData instanceof ProviderNodeModel) {
                ProviderNodeModel p = (ProviderNodeModel) currentSelectedData;
                storeManager.deleteProvider(p.getId());
                removeProviderFromMemory(p);
                reloadTree();
            }
        });

        // ---- Method 保存 ----
        saveMethodBtn.setOnAction(e -> {
            if (currentSelectedData instanceof MethodMockConfig) {
                MethodMockConfig m = (MethodMockConfig) currentSelectedData;
                m.setMethodName(methodNameField.getText());
                try {
                    m.setDelayMs(Integer.parseInt(methodDelayField.getText()));
                } catch (NumberFormatException ex) {
                    log.error("Delay must be integer");
                }
                m.setResponseJson(methodResponseArea.getText());
                storeManager.updateMethod(m);
                mockTreeView.refresh();
            }
        });

        // ---- Method 删除 ----
        deleteMethodBtn.setOnAction(e -> {
            if (currentSelectedData instanceof MethodMockConfig) {
                MethodMockConfig m = (MethodMockConfig) currentSelectedData;
                storeManager.deleteMethod(m.getId());
                removeMethodFromMemory(m);
                reloadTree();
            }
        });

        // ---- 启动 Mock Provider (带 Loading) ----
        startProviderBtn.setOnAction(e -> {
            if (currentSelectedData instanceof ProviderNodeModel) {
                ProviderNodeModel p = (ProviderNodeModel) currentSelectedData;
                RegistryNodeModel parentReg = findParentRegistry(p);
                if (parentReg == null) {
                    log.error(I18N.get("msg.registry_not_found"));
                    return;
                }

                // 显示 Loading 遮罩
                LoadingUtil.show(mainStackPane);
                startProviderBtn.setDisable(true);

                Thread startThread = new Thread(() -> {
                    try {
                        if (disposed) return;
                        DubboMockProcessManager.getInstance().startOrUpdateMock(
                            parentReg,
                            msg -> {
                                if (currentSelectedData == p) {
                                    providerLogArea.insertText(providerLogArea.getLength(), msg + "\n");
                                }
                            }
                        );
                        if (disposed) {
                            DubboMockProcessManager.getInstance().stopMock(parentReg.getId());
                            return;
                        }
                        runOnUi(() -> {
                            LoadingUtil.remove(mainStackPane);
                            // 仅刷新按钮状态，不重置表单
                            updateProviderButtons(p);
                            mockTreeView.refresh();
                        });
                    } catch (Exception ex) {
                        log.error("启动 Mock 引擎失败", ex);
                        runOnUi(() -> {
                            LoadingUtil.remove(mainStackPane);
                            startProviderBtn.setDisable(false);
                            CustomInfoDialog dialog = new CustomInfoDialog();
                            dialog.setLabelText(I18N.get("msg.engine_failed", ex.getMessage()));
                            dialog.showAndWait();
                        });
                    }
                });
                startThread.setDaemon(true);
                startThread.setName("dubbo-mock-process-start-" + p.getInterfaceName());
                workerThreads.add(startThread);
                startThread.start();
            }
        });

        // ---- 停止 Mock Provider (带 Loading) ----
        stopProviderBtn.setOnAction(e -> {
            if (currentSelectedData instanceof ProviderNodeModel) {
                ProviderNodeModel p = (ProviderNodeModel) currentSelectedData;
                RegistryNodeModel parentReg = findParentRegistry(p);

                LoadingUtil.show(mainStackPane);
                stopProviderBtn.setDisable(true);

                Thread stopThread = new Thread(() -> {
                    if (parentReg != null) {
                        DubboMockProcessManager.getInstance().stopMock(parentReg.getId());
                        // 标记该 Registry 下的所有 Provider 为停止状态
                        parentReg.getProviders().forEach(prov -> prov.setRunning(false));
                    }
                    runOnUi(() -> {
                        LoadingUtil.remove(mainStackPane);
                        updateProviderButtons(p);
                        mockTreeView.refresh();
                    });
                });
                stopThread.setDaemon(true);
                stopThread.setName("dubbo-mock-process-stop-" + p.getInterfaceName());
                workerThreads.add(stopThread);
                stopThread.start();
            }
        });
    }

    // ==================== 查找 Provider 所属 Registry ====================

    private RegistryNodeModel findParentRegistry(ProviderNodeModel provider) {
        if (storeManager == null || storeManager.getRegistryNodes() == null) return null;
        for (RegistryNodeModel r : storeManager.getRegistryNodes()) {
            if (r.getProviders() != null) {
                for (ProviderNodeModel p : r.getProviders()) {
                    if (p.getId().equals(provider.getId())) {
                        return r;
                    }
                }
            }
        }
        return null;
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Thread thread : new ArrayList<>(workerThreads)) {
            try { thread.interrupt(); } catch (RuntimeException e) { log.warn("中断 Dubbo Mock 工作线程失败", e); }
        }
        workerThreads.clear();
        DubboMockProcessManager.getInstance().stopAll();
        try { new DubboMockService().close(); } catch (RuntimeException e) { log.error("关闭 Dubbo Mock 服务失败", e); }
    }

    private void runOnUi(Runnable action) {
        if (!disposed) Platform.runLater(() -> { if (!disposed) action.run(); });
    }
}
