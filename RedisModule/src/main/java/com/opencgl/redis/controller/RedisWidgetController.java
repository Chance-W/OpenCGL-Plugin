package com.opencgl.redis.controller;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.redis.components.RedisConnectionDialog;
import com.opencgl.redis.components.RedisSessionTab;
import com.opencgl.redis.dao.RedisWidgetDao;
import com.opencgl.redis.model.RedisWidgetDto;
import com.opencgl.redis.views.RedisWidgetView;
import com.opencgl.redis.i18n.I18N;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis 连接管理控制器
 *
 * @author Chance.W
 */
public class RedisWidgetController extends RedisWidgetView
        implements Initializable, TreeOperateService<RedisWidgetDto> {

    private static final Logger log = LoggerFactory.getLogger(RedisWidgetController.class);
    private final RedisWidgetDao redisWidgetDao = new RedisWidgetDao();

    // 使用 TreeViewBuilder 构建的 TreeView
    private TreeView<RedisWidgetDto> redisTreeView;
    private TreeViewBuilder<RedisWidgetDto> connectionTreeBuilder;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            redisWidgetDao.checkTable();
        } catch (Exception e) {
            log.error("Failed to initialize database", e);
        }
        initUI();
        initI18n();
        loadConnections();
    }

    private void initI18n() {
        // Tooltips
        addConnectionBtn.setTooltip(new Tooltip());
        addConnectionBtn.getTooltip().textProperty().bind(I18N.getBinding("tooltip.add_connection"));
        editConnectionBtn.setTooltip(new Tooltip());
        editConnectionBtn.getTooltip().textProperty().bind(I18N.getBinding("tooltip.edit_connection"));
        refreshTreeBtn.setTooltip(new Tooltip());
        refreshTreeBtn.getTooltip().textProperty().bind(I18N.getBinding("tooltip.refresh"));

        // Buttons & Messages
        createFirstConnectionBtn.textProperty().bind(I18N.getBinding("button.create_connection"));
        if (noConnectionLabel != null) noConnectionLabel.textProperty().bind(I18N.getBinding("message.select_connection"));
    }

    private void initUI() {
        // 初始化连接按钮事件
        addConnectionBtn.setOnAction(e -> showAddConnectionDialog());
        editConnectionBtn.setOnAction(e -> showEditConnectionDialog());
        refreshTreeBtn.setOnAction(e -> loadConnections());
        createFirstConnectionBtn.setOnAction(e -> showAddConnectionDialog());

        // 初始化 TabPane 监听
        sessionTabPane.getTabs().addListener((javafx.collections.ListChangeListener.Change<? extends Tab> c) -> {
            if (sessionTabPane.getTabs().isEmpty()) {
                showNoConnectionPane();
            } else {
                showConnectedPane();
            }
        });

        // 默认显示未连接状态
        showNoConnectionPane();
    }

    private void loadConnections() {
        if (connectionTreeBuilder != null) {
            connectionTreeBuilder.refresh();
            return;
        }
        // 使用 TreeViewBuilder 构建 TreeView（参考 DubboWidgetController）
        connectionTreeBuilder = new TreeViewBuilder<RedisWidgetDto>()
                .onTreeCreated(tree -> this.redisTreeView = tree)
                .service(this)
                .dataType(RedisWidgetDto.class)
                .enableDragDrop(true)
                .showRoot(false)
                .contextMenuFactory(dto -> buildContextMenu(dto));
        VBox redisVbox = connectionTreeBuilder.build();

        // 替换 FXML 中的 connectionTree
        if (connectionTree.getParent() instanceof VBox parentVBox) {
            int index = parentVBox.getChildren().indexOf(connectionTree);
            if (index >= 0) {
                parentVBox.getChildren().set(index, redisTreeView);
                javafx.scene.layout.VBox.setVgrow(redisTreeView, javafx.scene.layout.Priority.ALWAYS);
            }
        }

        // 双击连接事件
        if (redisTreeView != null) {
            redisTreeView.setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && event.getClickCount() == 2) {
                    TreeItem<RedisWidgetDto> selected = redisTreeView.getSelectionModel().getSelectedItem();
                    if (selected != null && selected.getValue() != null && selected.getValue().getIsLeaf()) {
                        connectToRedis(selected.getValue());
                    }
                }
            });
        }
    }

    /**
     * 构建右键菜单（每个节点独立）
     */
    private ContextMenu buildContextMenu(RedisWidgetDto dto) {
        ContextMenu menu = new ContextMenu();
        boolean isLeaf = dto != null && Boolean.TRUE.equals(dto.getIsLeaf());

        if (isLeaf) {
            MenuItem connectItem = new MenuItem(I18N.get("menu.connect", "连接"));
            connectItem.setOnAction(e -> connectToRedis(dto));
            menu.getItems().add(connectItem);
        }

        MenuItem editItem = new MenuItem(I18N.get("tooltip.edit_connection"));
        editItem.setOnAction(e -> showEditConnectionDialog());
        menu.getItems().add(editItem);

        menu.getItems().add(new SeparatorMenuItem());

        MenuItem deleteItem = new MenuItem(I18N.get("tooltip.delete_connection"));
        deleteItem.setOnAction(e -> deleteSelectedConnection());
        menu.getItems().add(deleteItem);

        return menu;
    }

    private void showAddConnectionDialog() {
        javafx.stage.Window owner = mainAnchorPane.getScene() != null ? mainAnchorPane.getScene().getWindow() : null;
        RedisWidgetDto newConfig = RedisConnectionDialog.showDialog(null, owner);
        if (newConfig != null) {
            try {
                CustomizeTreeItem<RedisWidgetDto> newItem = add(newConfig);
                if (newItem != null && redisTreeView != null && redisTreeView.getRoot() != null) {
                    // TreeViewBuilder root is usually invisible. Add it directly under root
                    TreeItem<RedisWidgetDto> treeNode = (TreeItem<RedisWidgetDto>) (TreeItem<?>) newItem;
                    redisTreeView.getRoot().getChildren().add(treeNode);
                    redisTreeView.getSelectionModel().select(treeNode);
                }
                TooltipUtil.showToast(mainAnchorPane, I18N.get("message.connection_saved"));
            } catch (Exception e) {
                log.error("Failed to save connection", e);
                DialogUtil.showErrorInfo(I18N.get("message.error.save_failed") + ": " + e.getMessage(), mainAnchorPane);
            }
        }
    }

    private void showEditConnectionDialog() {
        TreeItem<?> selected = redisTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getValue() instanceof RedisWidgetDto)) {
            TooltipUtil.showToast(mainAnchorPane, I18N.get("message.error.select_first"));
            return;
        }

        RedisWidgetDto dto = (RedisWidgetDto) selected.getValue();
        javafx.stage.Window owner = mainAnchorPane.getScene() != null ? mainAnchorPane.getScene().getWindow() : null;
        RedisWidgetDto updated = RedisConnectionDialog.showDialog(dto, owner);
        if (updated != null) {
            updated.setId(dto.getId());
            update(updated);
            // 局部刷新当前选中的节点对象，促使界面更新
            ((TreeItem<RedisWidgetDto>) selected).setValue(null);
            ((TreeItem<RedisWidgetDto>) selected).setValue(updated);
            
            TooltipUtil.showToast(mainAnchorPane, I18N.get("message.connection_updated"));
        }
    }

    private void deleteSelectedConnection() {
        TreeItem<?> selected = redisTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getValue() instanceof RedisWidgetDto)) {
            TooltipUtil.showToast(mainAnchorPane, I18N.get("message.error.select_first"));
            return;
        }

        RedisWidgetDto dto = (RedisWidgetDto) selected.getValue();
        delete(dto);
        // 从当前树的父节点中移除，不全局重新加载
        if (selected.getParent() != null) {
            selected.getParent().getChildren().remove(selected);
        }
        TooltipUtil.showToast(mainAnchorPane, I18N.get("message.connection_deleted"));
    }

    private void connectToRedis(RedisWidgetDto dto) {
        // 判断是否已经有同一个连接打开
        for (Tab tab : sessionTabPane.getTabs()) {
            if (tab instanceof RedisSessionTab) {
                RedisWidgetDto existingData = ((RedisSessionTab) tab).getConnectionData();
                if (existingData != null && existingData.getId() != null && existingData.getId().equals(dto.getId())) {
                    // 已打开，直接选中
                    sessionTabPane.getSelectionModel().select(tab);
                    return;
                }
            }
        }

        // 没有打开过，创建一个新的会话 Tab
        RedisSessionTab newSessionTab = new RedisSessionTab(dto);
        sessionTabPane.getTabs().add(newSessionTab);
        sessionTabPane.getSelectionModel().select(newSessionTab);
    }

    private void showConnectedPane() {
        noConnectionPane.setVisible(false);
        noConnectionPane.setManaged(false);
        sessionTabPane.setVisible(true);
        sessionTabPane.setManaged(true);
    }

    private void showNoConnectionPane() {
        noConnectionPane.setVisible(true);
        noConnectionPane.setManaged(true);
        sessionTabPane.setVisible(false);
        sessionTabPane.setManaged(false);
    }

    // TreeOperateService 实现

    @Override
    public CustomizeTreeItem<RedisWidgetDto> add(RedisWidgetDto dto) {
        try {
            Long id = redisWidgetDao.insert(dto);
            // 将数据库自增 ID 回写到 dto，确保后续删除/编辑可以正确引用
            if (id != null) {
                dto.setId(id);
            }
            return new CustomizeTreeItem<>(dto);
        } catch (Exception e) {
            log.error("Failed to add connection", e);
            return null;
        }
    }

    @Override
    public CustomizeTreeItem<RedisWidgetDto> importData(RedisWidgetDto dto) {
        return add(dto);
    }

    @Override
    public CustomizeTreeItem<RedisWidgetDto> delete(RedisWidgetDto dto) {
        try {
            redisWidgetDao.delete(dto.getId());
            return new CustomizeTreeItem<>(dto);
        } catch (Exception e) {
            log.error("Failed to delete connection", e);
            return null;
        }
    }

    @Override
    public CustomizeTreeItem<RedisWidgetDto> update(RedisWidgetDto dto) {
        try {
            redisWidgetDao.update(dto);
            return new CustomizeTreeItem<>(dto);
        } catch (Exception e) {
            log.error("Failed to update connection", e);
            return null;
        }
    }

    @Override
    public void changeToDisplay(RedisWidgetDto dto) {
        // 不再在单击/选中时自动连接 Redis，改为在 loadConnections() 监听双击事件
    }

    @Override
    public List<RedisWidgetDto> queryAll() {
        try {
            return redisWidgetDao.queryAll();
        } catch (Exception e) {
            log.error("Failed to query connections", e);
            return null;
        }
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }

    public void dispose() {
        for (Tab tab : List.copyOf(sessionTabPane.getTabs())) {
            if (tab instanceof RedisSessionTab sessionTab) {
                sessionTab.dispose();
            }
        }
        sessionTabPane.getTabs().clear();
    }

}
