package com.opencgl.sshjedi.controller;

import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.sshjedi.i18n.I18N;
import com.opencgl.sshjedi.lifecycle.LifecycleDisposer;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.sshjedi.dao.SshConnectionDao;
import com.opencgl.sshjedi.model.SshConnectionDto;
import com.opencgl.sshjedi.session.SshJediTermSessionTab;
import com.opencgl.sshjedi.views.SshConfigDialog;
import com.opencgl.sshjedi.views.SshJediTermView;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * SSH终端控制器 - TabPane多标签版本
 * 支持同时打开多个SSH连接会话
 */
public class SshJediTermController extends SshJediTermView
        implements Initializable, TreeOperateService<SshConnectionDto> {

    private static final Logger logger = LoggerFactory.getLogger(SshJediTermController.class);
    private final SshConnectionDao dao = new SshConnectionDao();
    private final LifecycleDisposer lifecycle = new LifecycleDisposer();
    private TreeView<SshConnectionDto> treeView;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTree();
        initTabPane();
        initTreeToggle();
        initI18n();
    }

    private void initI18n() {
        // connectionListLabel.textProperty().bind(I18N.getBinding("label.connection_list"));
        // hintClickConnectLabel.textProperty().bind(I18N.getBinding("hint.click_connect"));
        // sshJediTermLabel.textProperty().bind(I18N.getBinding("toolbar.ssh_terminal"));
    }

    private void initTree() {
        // 使用TreeViewBuilder构建树
        VBox treeViewVbox = new TreeViewBuilder<SshConnectionDto>()
                .enableSearch(true)
                .onTreeCreated(tree -> this.treeView = tree)
                .service(this)
                .dataType(SshConnectionDto.class)
                .enableDragDrop(true)
                .showRoot(true)
                .rootExpanded(true)
                .contextMenuFactory(this::createCustomContextMenu)
                .build();

        // 单击事件：纯粹用于选中节点，不再强行覆盖右侧当前的 Tab
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // 仅选中记录即可，不需要联动右侧影响用户正在操作的界面
            if (newVal != null && Boolean.TRUE.equals(newVal.getValue().getIsLeaf())) {
                this.changeToDisplay(newVal.getValue());
            }
        });

        // 双击事件：打开Tab
        treeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                // 获取点击位置的节点
                TreeItem<SshConnectionDto> clickedItem = getTreeItemAt(treeView, event.getX(), event.getY());
                if (clickedItem != null && Boolean.TRUE.equals(clickedItem.getValue().getIsLeaf())) {
                    SshConnectionDto dto = clickedItem.getValue();
                    // 统一通过 openTabForConnection 处理，根据 host 前缀自动识别
                    boolean isLocal = dto.getHost() != null && dto.getHost().startsWith("LOCAL:");
                    boolean canAutoConnect = isLocal || (dto.getHost() != null && !dto.getHost().trim().isEmpty()
                            && dto.getUsername() != null && !dto.getUsername().trim().isEmpty());
                    openTabForConnection(dto, canAutoConnect);
                    event.consume();
                }
            }
        });

        treeBorderPane.setCenter(treeViewVbox);
    }
    

    /**
     * 自定义 SSH 树节点的右键菜单
     */
    private ContextMenu createCustomContextMenu(SshConnectionDto dto) {
        ContextMenu menu = new ContextMenu();

        if (dto != null) {
            if (Boolean.TRUE.equals(dto.getIsLeaf())) {
                // 叶子节点 (连接项)
                MenuItem connectItem = new MenuItem();
                connectItem.textProperty()
                        .bind(javafx.beans.binding.Bindings.concat("🔗 ", I18N.getBinding("action.connect")));
                connectItem.setOnAction(e -> performAction(dto));

                MenuItem editConfigItem = new MenuItem();
                editConfigItem.textProperty()
                        .bind(BaseI18N.getBinding("opencgl.base.tree.modSubMenuItem"));
                editConfigItem.setOnAction(e -> SshConfigDialog.showDialog(dto, updatedDto -> {
                    // 更新树节点的数据和显示
                    TreeItem<SshConnectionDto> currentItem = treeView.getSelectionModel().getSelectedItem();
                    if (currentItem != null) {
                        try {
                            dao.update(updatedDto);
                            currentItem.setValue(updatedDto);
                            treeView.refresh();
                        } catch (Exception ex) {
                            logger.error("更新树节点配置失败", ex);
                        }
                    }
                }));

                MenuItem deleteItem = new MenuItem();
                deleteItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.delCurrentSubMenuItem"));
                deleteItem.setOnAction(e -> deleteNode(dto));

                menu.getItems().addAll(connectItem, new SeparatorMenuItem(), editConfigItem, deleteItem);
            } else {
                // 目录节点
                MenuItem addDirItem = new MenuItem();
                addDirItem.textProperty()
                        .bind(BaseI18N.getBinding("opencgl.base.tree.addSecondDirMenuItem"));
                addDirItem.setOnAction(e -> addNode(false));

                MenuItem addLeafItem = new MenuItem();
                addLeafItem.textProperty()
                        .bind(BaseI18N.getBinding("opencgl.base.tree.addSecondSubMenuItem"));
                addLeafItem.setOnAction(e -> addNode(true));

                // 添加本机终端菜单
                javafx.scene.control.Menu addLocalShellMenu = new javafx.scene.control.Menu("💻 添加本机终端");
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    addShellItem(addLocalShellMenu, "Command Prompt", "cmd.exe");
                    addShellItem(addLocalShellMenu, "PowerShell", "powershell.exe");
                    addShellItem(addLocalShellMenu, "WSL (Ubuntu)", "wsl.exe");
                } else if (os.contains("mac")) {
                    addShellItem(addLocalShellMenu, "Zsh", "/bin/zsh");
                    addShellItem(addLocalShellMenu, "Bash", "/bin/bash");
                } else {
                    addShellItem(addLocalShellMenu, "Bash", "/bin/bash");
                    addShellItem(addLocalShellMenu, "Zsh", "/bin/zsh");
                }

                menu.getItems().addAll(addDirItem, addLeafItem, addLocalShellMenu);

                if (dto.getId() != null && dto.getId() != 0L) {
                    menu.getItems().add(new SeparatorMenuItem());
                    MenuItem renameItem = new MenuItem();
                    renameItem.textProperty().bind(
                            BaseI18N.getBinding("opencgl.base.tree.modDirectoryMenuItem"));
                    renameItem.setOnAction(e -> modifyNodeName(dto));

                    MenuItem deleteItem = new MenuItem();
                    deleteItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.delCurrentDirMenuItem"));
                    deleteItem.setOnAction(e -> deleteNode(dto));

                    menu.getItems().addAll(renameItem, deleteItem);
                }
            }
        } else {
            // 空白处右键（根节点之外，如果有必要）
            MenuItem addDirItem = new MenuItem();
            addDirItem.textProperty().bind(BaseI18N.getBinding("opencgl.base.tree.addFirstDirMenuItem"));
            // ... 处理逻辑
        }

        return menu;
    }

    private void addNode(boolean isLeaf) {
        TreeItem<SshConnectionDto> parent = treeView.getSelectionModel().getSelectedItem();
        if (parent == null)
            return;
        String name = DialogUtil.show();
        if (name == null || name.isEmpty())
            return;
        SshConnectionDto newData = new SshConnectionDto();
        newData.setParentId(parent.getValue().getId());
        newData.setName(name);
        newData.setIsLeaf(isLeaf);
        newData.setSortOrder(parent.getChildren().size());
        CustomizeTreeItem<SshConnectionDto> newItem = add(newData);
        if (newItem != null) {
            parent.getChildren().add(newItem);
            parent.setExpanded(true);
        }
    }

    private void modifyNodeName(SshConnectionDto data) {
        String newName = DialogUtil.show(data.getName());
        if (newName != null && !newName.isEmpty()) {
            data.setName(newName);
            update(data);
            treeView.refresh();
        }
    }

    private void deleteNode(SshConnectionDto data) {
        Boolean confirm = DialogUtil.deleteConfirm();
        if (confirm != null && confirm) {
            delete(data);
            TreeItem<SshConnectionDto> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getParent() != null) {
                selected.getParent().getChildren().remove(selected);
            }
        }
    }

    private void addShellItem(javafx.scene.control.Menu menu, String defaultName, String cmd) {
        MenuItem item = new MenuItem(defaultName);
        item.setOnAction(e -> {
            TreeItem<SshConnectionDto> parent = treeView.getSelectionModel().getSelectedItem();
            if (parent == null) return;
            
            // 弹出对话框让用户输入名称，不提供默认值
            String name = com.opencgl.base.utils.DialogUtil.show();
            if (name == null || name.trim().isEmpty()) return;

            SshConnectionDto newData = new SshConnectionDto();
            newData.setParentId(parent.getValue().getId());
            newData.setName(name);
            newData.setHost("LOCAL:" + cmd);
            newData.setIsLeaf(true);
            newData.setSortOrder(parent.getChildren().size());
            
            CustomizeTreeItem<SshConnectionDto> newItem = add(newData);
            if (newItem != null) {
                parent.getChildren().add(newItem);
                parent.setExpanded(true);
            }
        });
        menu.getItems().add(item);
    }

    /**
     * 获取鼠标位置的TreeItem
     */
    private TreeItem<SshConnectionDto> getTreeItemAt(TreeView<SshConnectionDto> tree, double x, double y) {
        try {
            // 通过点击坐标查找对应节点
            for (TreeItem<SshConnectionDto> item : getAllTreeItems(tree.getRoot())) {
                // 这里简化处理，直接使用选中的节点
                return tree.getSelectionModel().getSelectedItem();
            }
        } catch (Exception e) {
            logger.debug("获取TreeItem失败", e);
        }
        return tree.getSelectionModel().getSelectedItem();
    }

    /**
     * 获取所有TreeItem
     */
    private java.util.List<TreeItem<SshConnectionDto>> getAllTreeItems(TreeItem<SshConnectionDto> root) {
        java.util.List<TreeItem<SshConnectionDto>> items = new java.util.ArrayList<>();
        if (root != null) {
            items.add(root);
            for (TreeItem<SshConnectionDto> child : root.getChildren()) {
                items.addAll(getAllTreeItems(child));
            }
        }
        return items;
    }

    private boolean isTreeCollapsed = false; // 跟踪树的折叠状态

    private void initTreeToggle() {
        final double defaultDividerPosition = 0.22;

        // 配置工具栏按钮样式
        // toggleTreeButton.setStyle(
        // // "-fx-background-color: #00d4aa;" +
        // // "-fx-text-fill: white;" +
        // "-fx-font-size: 11px;" +
        // "-fx-font-weight: bold;" +
        // "-fx-background-radius: 4;" +
        // "-fx-cursor: hand;");

        // 添加 Tooltip
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(I18N.getBinding("tooltip.toggle_tree"));
        tooltip.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 11px;");
        // toggleTreeButton.setTooltip(tooltip);

        // toggleTreeButton.setOnAction(e -> {
        // double currentPosition = mainSplitPane.getDividerPositions()[0];
        //
        // if (currentPosition > 0.01) {
        // // 折叠：隐藏左侧面板
        // treePanel.setVisible(false);
        // treePanel.setManaged(false);
        // mainSplitPane.setDividerPositions(0.0);
        // toggleTreeButton.setText("▶");
        // isTreeCollapsed = true;
        // } else {
        // // 展开：显示左侧面板
        // treePanel.setVisible(true);
        // treePanel.setManaged(true);
        // mainSplitPane.setDividerPositions(defaultDividerPosition);
        // toggleTreeButton.setText("◀");
        // isTreeCollapsed = false;
        // }
        // });

        // 监听窗口大小变化，保持折叠状态
        Platform.runLater(() -> {
            mainSplitPane.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (isTreeCollapsed) {
                    // 如果处于折叠状态，强制保持折叠
                    Platform.runLater(() -> {
                        treePanel.setVisible(false);
                        treePanel.setManaged(false);
                        mainSplitPane.setDividerPositions(0.0);
                    });
                }
            });
        });
    }

    /**
     * 处理节点点击：智能创建或更新Tab
     */
    private void handleNodeClick(SshConnectionDto dto) {
        Tab currentTab = sessionTabPane.getSelectionModel().getSelectedItem();

        // 如果当前Tab是未连接的SshJediTermSessionTab，直接更新其数据
        if (currentTab instanceof SshJediTermSessionTab sessionTab && !sessionTab.isConnected()) {
            sessionTab.fillConnectionFields(dto);
            return;
        }

        // 允许重复打开同一连接，直接创建新Tab
        openTabForConnection(dto, false);
    }

    private void initTabPane() {
        // 设置TabPane样式
        // sessionTabPane.setStyle("-fx-background-color: #1e1e1e;");

        Tab welcomeTab = new Tab();
        welcomeTab.textProperty().bind(I18N.getBinding("tab.welcome"));
        welcomeTab.getProperties().put("isWelcomeTab", true);
        welcomeTab.setClosable(false);
        welcomeTab.setContent(createWelcomeContent());
        sessionTabPane.getTabs().add(welcomeTab);
        sessionTabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                for (Tab removed : change.getRemoved()) {
                    disposeSession(removed);
                }
            }
        });
    }

    private void disposeSession(Tab tab) {
        if (tab instanceof SshJediTermSessionTab sessionTab) {
            try {
                sessionTab.dispose();
            } catch (RuntimeException error) {
                logger.warn("Failed to dispose SSH JediTerm session tab {}", tab.getText(), error);
            }
        }
    }

    private javafx.scene.Node createWelcomeContent() {
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(25);
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setStyle("-fx-padding: 50;");

        Label icon = new Label("🖥️");
        icon.setStyle("-fx-font-size: 72px;");

        Label title = new Label();
        title.textProperty().bind(I18N.getBinding("welcome.title"));
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -theme-text;");

        Label hint = new Label();
        hint.textProperty().bind(I18N.getBinding("welcome.hint"));
        hint.setStyle("-fx-font-size: 14px; -fx-text-fill: -theme-text-secondary; -fx-line-spacing: 8px;");
        hint.setTextAlignment(TextAlignment.CENTER);
        hint.setWrapText(true);

        content.getChildren().addAll(icon, title, hint);
        return content;
    }

    /**
     * 为连接创建Tab
     *
     * @param dto         连接数据
     * @param autoConnect 是否自动连接
     */
    private void openTabForConnection(SshConnectionDto dto, boolean autoConnect) {
        SshJediTermSessionTab newTab;
        if (dto.getHost() != null && dto.getHost().startsWith("LOCAL:")) {
            com.opencgl.sshjedi.service.LocalShellServiceImpl localService = new com.opencgl.sshjedi.service.LocalShellServiceImpl();
            newTab = new SshJediTermSessionTab("💻 " + dto.getName(), localService);
            newTab.fillConnectionFields(dto);
        } else {
            newTab = new SshJediTermSessionTab(dto);
        }
        sessionTabPane.getTabs().add(newTab);
        sessionTabPane.getSelectionModel().select(newTab);

        // 添加双击和右键关闭功能
        setupTabCloseGestures(newTab);

        if (autoConnect) {
            Platform.runLater(() -> newTab.connect());
        }

        logger.info("创建SSH会话Tab: {}", dto.getName());
    }

    /**
     * 设置Tab的双击和右键关闭功能
     */
    private void setupTabCloseGestures(Tab tab) {
        // 生成唯一样式类以便精确定位渲染节点，防止同名 tab 匹配错乱
        String uniqueId = "ssh-tab-uuid-" + UUID.randomUUID().toString();
        tab.getStyleClass().add(uniqueId);
        tab.getProperties().put("uniqueNodeId", uniqueId);

        // 多次延迟尝试，确保 Tab 渲染完成
        Platform.runLater(() -> {
            Platform.runLater(() -> {
                Platform.runLater(() -> {
                    javafx.scene.Node tabNode = findTabNode(tab);
                    if (tabNode != null) {
                        setupTabNodeEvents(tabNode, tab);
                    }
                });
            });
        });
    }

    private javafx.scene.Node findTabNode(Tab tab) {
        String uniqueId = (String) tab.getProperties().get("uniqueNodeId");
        if (uniqueId != null) {
            for (javafx.scene.Node node : sessionTabPane.lookupAll("." + uniqueId)) {
                return node;
            }
        }
        return null;
    }

    /**
     * 为Tab节点设置事件
     */
    private void setupTabNodeEvents(javafx.scene.Node tabNode, Tab tab) {
        // 双击关闭
        tabNode.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (!tab.getProperties().containsKey("isWelcomeTab")) {
                    sessionTabPane.getTabs().remove(tab);
                }
                event.consume();
            }
        });

        // 右键菜单
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.setStyle(
                "-fx-border-color: #666666;" +
                        "-fx-border-width: 1;" +
                        "-fx-background-radius: 4;" +
                        "-fx-border-radius: 4;");

        if (tab.getProperties().containsKey("isWelcomeTab")) {
            return;
        }

        MenuItem closeCurrentItem = new MenuItem();
        closeCurrentItem.textProperty().bind(I18N.getBinding("menu.close_current"));
        closeCurrentItem.setStyle("-fx-text-fill: #e0e0e0; -fx-padding: 6 12 6 12;");
        closeCurrentItem.setOnAction(e -> {
            if (!tab.getProperties().containsKey("isWelcomeTab")) {
                sessionTabPane.getTabs().remove(tab);
            }
        });

        MenuItem copyItem = new MenuItem();
        copyItem.textProperty().bind(I18N.getBinding("menu.copy_tab"));
        copyItem.setStyle("-fx-text-fill: #e0e0e0; -fx-padding: 6 12 6 12;");
        copyItem.setOnAction(e -> {
            if (tab instanceof SshJediTermSessionTab sessionTab) {
                // 复制当前连接并重新打开一页（自动连接）
                openTabForConnection(sessionTab.getConnectionData(), true);
            }
        });

        MenuItem closeLeftItem = new MenuItem();
        closeLeftItem.textProperty().bind(I18N.getBinding("menu.close_left"));
        closeLeftItem.setStyle("-fx-text-fill: #e0e0e0; -fx-padding: 6 12 6 12;");
        closeLeftItem.setOnAction(e -> {
            int currentIndex = sessionTabPane.getTabs().indexOf(tab);
            if (currentIndex > 0) {
                java.util.List<Tab> toRemove = new java.util.ArrayList<>();
                for (int i = 0; i < currentIndex; i++) {
                    Tab t = sessionTabPane.getTabs().get(i);
                    if (!t.getProperties().containsKey("isWelcomeTab")) {
                        toRemove.add(t);
                    }
                }
                sessionTabPane.getTabs().removeAll(toRemove);
            }
        });

        MenuItem closeRightItem = new MenuItem();
        closeRightItem.textProperty().bind(I18N.getBinding("menu.close_right"));
        closeRightItem.setStyle("-fx-text-fill: #e0e0e0; -fx-padding: 6 12 6 12;");
        closeRightItem.setOnAction(e -> {
            int currentIndex = sessionTabPane.getTabs().indexOf(tab);
            if (currentIndex >= 0 && currentIndex < sessionTabPane.getTabs().size() - 1) {
                java.util.List<Tab> toRemove = new java.util.ArrayList<>();
                for (int i = currentIndex + 1; i < sessionTabPane.getTabs().size(); i++) {
                    Tab t = sessionTabPane.getTabs().get(i);
                    if (!t.getProperties().containsKey("isWelcomeTab")) {
                        toRemove.add(t);
                    }
                }
                sessionTabPane.getTabs().removeAll(toRemove);
            }
        });

        MenuItem closeAllItem = new MenuItem();
        closeAllItem.textProperty().bind(I18N.getBinding("menu.close_all"));
        closeAllItem.setStyle("-fx-text-fill: #e0e0e0; -fx-padding: 6 12 6 12;");
        closeAllItem.setOnAction(e -> {
            sessionTabPane.getTabs().removeIf(t -> !t.getProperties().containsKey("isWelcomeTab"));
        });

        contextMenu.getItems().addAll(closeCurrentItem, copyItem, closeLeftItem, closeRightItem, closeAllItem);
        
        // 关键改动：强制向渲染节点注册右键以防止事件被意外消费
        tabNode.setOnContextMenuRequested(event -> {
            contextMenu.show(tabNode, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    // ============ TreeOperateService 实现 ============

    @Override
    public CustomizeTreeItem<SshConnectionDto> add(SshConnectionDto dto) {
        try {
            Long newId = dao.insert(dto);
            dto.setId(newId);

            // 如果是叶子节点，也创建对应的Tab
            // 如果是叶子节点，也创建对应的Tab
            if (Boolean.TRUE.equals(dto.getIsLeaf())) {
                // 如果是本机终端，创建后默认自动连接
                boolean autoConnect = (dto.getHost() != null && dto.getHost().startsWith("LOCAL:"));
                Platform.runLater(() -> openTabForConnection(dto, autoConnect));
            }

            return new CustomizeTreeItem<>(dto);
        } catch (Exception e) {
            logger.error("添加节点失败", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<SshConnectionDto> importData(SshConnectionDto dto) {
        return add(dto);
    }

    @Override
    public CustomizeTreeItem<SshConnectionDto> delete(SshConnectionDto dto) {
        try {
            dao.delete(dto);
            return new CustomizeTreeItem<>(dto);
        } catch (Exception e) {
            logger.error("删除节点失败", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<SshConnectionDto> update(SshConnectionDto dto) {
        try {
            dao.update(dto);
            logger.info("更新节点: {}", dto);
            return new CustomizeTreeItem<>(dto);
        } catch (Exception e) {
            logger.error("更新节点失败", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void notifyDataChange(Long oldParentId, Long newParentId) {
        // SSH终端不需要特殊处理数据变更
    }

    @Override
    public boolean supportAction() {
        return true; // SSH终端支持"连接"操作
    }

    @Override
    public String getActionName() {
        return I18N.get("action.connect");
    }

    @Override
    public void performAction(SshConnectionDto data) {
        // 右键连接：创建新Tab并自动连接
        Platform.runLater(() -> openTabForConnection(data, true));
    }

    @Override
    public void changeToDisplay(SshConnectionDto dto) {
        // 单击只显示信息，不打开连接
        logger.debug("选中节点: {}", dto.getName());
    }

    @Override
    public List<SshConnectionDto> queryAll() {
        return dao.queryAllData();
    }

    @Override
    public boolean supportDragDrop() {
        return true;
    }

    @Override
    public void updatePositionOnly(SshConnectionDto dto) {
        try {
            dao.updatePositionOnly(dto);
        } catch (Exception e) {
            logger.error("更新位置失败", e);
        }
    }

    public void dispose() {
        lifecycle.dispose(error -> logger.warn("Failed to dispose SSH JediTerm controller resource", error),
                () -> {
                    for (Tab tab : List.copyOf(sessionTabPane.getTabs())) {
                        disposeSession(tab);
                    }
                },
                () -> sessionTabPane.getTabs().clear());
    }
}
