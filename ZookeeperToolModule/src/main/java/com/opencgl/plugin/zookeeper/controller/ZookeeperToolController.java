package com.opencgl.plugin.zookeeper.controller;

import com.opencgl.plugin.zookeeper.i18n.I18N;
import com.opencgl.plugin.zookeeper.utils.JavaFxViewUtil;
import com.opencgl.plugin.zookeeper.service.ZookeeperToolService;
import com.opencgl.plugin.zookeeper.views.ZookeeperToolView;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.input.MouseButton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.ResourceBundle;

@Getter
@Setter
@Slf4j
@SuppressWarnings("unused")
public class ZookeeperToolController extends ZookeeperToolView {
    private final java.nio.file.Path connectionDatabase;
    private ZkConnectionsPane connectionsPane;
    private com.opencgl.plugin.zookeeper.service.ZkConnection selectedConnection;
    public ZookeeperToolController() { this(java.nio.file.Path.of(com.opencgl.base.model.Base.DB_PATH, "data.db")); }
    public ZookeeperToolController(java.nio.file.Path database) { this.connectionDatabase = database; }
    private ZookeeperToolService zookeeperToolService = new ZookeeperToolService(this);
    private NodeSearchController nodeSearchController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        bindI18n();
        initView();
        initEvent();
        initService();
        connectionsPane = new ZkConnectionsPane(new com.opencgl.plugin.zookeeper.service.ZkConnectionRepository(connectionDatabase),
                this::selectConnection, () -> zookeeperToolService.connectOnAction());
        connectionSidebar.getChildren().setAll(connectionsPane);
        javafx.scene.layout.VBox.setVgrow(connectionsPane, javafx.scene.layout.Priority.ALWAYS);
        connectionNameField.promptTextProperty().bind(I18N.getBinding("connection.name"));
        saveConnectionButton.textProperty().bind(I18N.getBinding("connection.save"));
        saveConnectionButton.setDisable(true);
        zkServersTextField.textProperty().addListener((obs, old, value) -> zookeeperToolService.disconnectOnAction());
        connectionTimeoutSpinner.valueProperty().addListener((obs, old, value) -> zookeeperToolService.disconnectOnAction());
    }

    private void selectConnection(com.opencgl.plugin.zookeeper.service.ZkConnection profile) {
        if (profile == null || !Boolean.TRUE.equals(profile.getIsLeaf())) {
            zookeeperToolService.disconnectOnAction();
            selectedConnection = null; saveConnectionButton.setDisable(true); return;
        }
        if (selectedConnection == null || !java.util.Objects.equals(selectedConnection.getId(), profile.getId()))
            zookeeperToolService.disconnectOnAction();
        selectedConnection = profile.snapshot();
        connectionNameField.setText(profile.getName());
        zkServersTextField.setText(profile.getServers());
        connectionTimeoutSpinner.getValueFactory().setValue(profile.getTimeoutMs());
        saveConnectionButton.setDisable(false);
    }

    @FXML private void saveConnectionOnAction(ActionEvent event) {
        if (selectedConnection == null) return;
        var updated = selectedConnection.snapshot();
        updated.setName(connectionNameField.getText()); updated.setServers(zkServersTextField.getText());
        updated.setTimeoutMs(connectionTimeoutSpinner.getValue());
        try {
            connectionsPane.saveProfile(updated);
            com.opencgl.base.utils.TooltipUtil.showToast(getMainAnchorPane(), I18N.get("connection.saved"));
        } catch (RuntimeException e) { com.opencgl.base.utils.DialogUtil.showErrorInfo(e.getMessage()); }
    }

    private void bindI18n() {
        zkServersLabel.textProperty().bind(I18N.getBinding("label.zk_servers"));
        connectionTimeoutLabel.textProperty().bind(I18N.getBinding("label.connection_timeout"));
        connectButton.textProperty().bind(I18N.getBinding("button.connect"));
        disconnectButton.textProperty().bind(I18N.getBinding("button.disconnect"));
        refreshButton.textProperty().bind(I18N.getBinding("button.refresh"));

        nodeDataTab.textProperty().bind(I18N.getBinding("tab.node_data"));
        nodeMetadataTab.textProperty().bind(I18N.getBinding("tab.node_metadata"));
        nodeAclTab.textProperty().bind(I18N.getBinding("tab.node_acl"));

        nodeDataSaveButton.textProperty().bind(I18N.getBinding("button.save"));

        aclVersionLabel.textProperty().bind(I18N.getBinding("label.acl_version"));
        creationTimeLabel.textProperty().bind(I18N.getBinding("label.creation_time"));
        childrenVersionLabel.textProperty().bind(I18N.getBinding("label.children_version"));
        creationIdLabel.textProperty().bind(I18N.getBinding("label.creation_id"));
        dataLengthLabel.textProperty().bind(I18N.getBinding("label.data_length"));
        ephemeralOwnerLabel.textProperty().bind(I18N.getBinding("label.ephemeral_owner"));
        lastModifiedTimeLabel.textProperty().bind(I18N.getBinding("label.last_modified_time"));
        modifiedIdLabel.textProperty().bind(I18N.getBinding("label.modified_id"));
        numChildrenLabel.textProperty().bind(I18N.getBinding("label.num_children"));
        nodeIdLabel.textProperty().bind(I18N.getBinding("label.node_id"));
        dataVersionLabel.textProperty().bind(I18N.getBinding("label.data_version"));

        schemeLabel.textProperty().bind(I18N.getBinding("label.scheme"));
        idLabel.textProperty().bind(I18N.getBinding("label.id"));
        permissionsLabel.textProperty().bind(I18N.getBinding("label.permissions"));
    }

    private void initView() {
        JavaFxViewUtil.setSpinnerValueFactory(connectionTimeoutSpinner, 0, Integer.MAX_VALUE, 5000);
        TreeItem<String> treeItem = new TreeItem<String>("/");
        nodeTreeView.setRoot(treeItem);
        nodeSearchController = new NodeSearchController(nodeTreeView, nodeSearchField, searchAllButton,
                cancelSearchButton, clearSearchButton, searchStatus, searchProgress, () -> {
                    var client = zookeeperToolService.getZkClient();
                    return client == null ? null : client::getChildren;
                }, item -> zookeeperToolService.nodeSelectionChanged(item));
    }

    private void initEvent() {
        nodeTreeView.setOnMouseClicked(event -> {
            TreeItem<String> selectedItem = nodeTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) {
                return;
            }
            if (event.getButton() == MouseButton.PRIMARY) {
                if (event.getClickCount() == 2 && nodeSearchController.isSearching()) {
                    nodeSearchController.expand(selectedItem);
                }
                if (event.getClickCount() == 2 && !nodeSearchController.isSearching()) {
                    // 双击加载子节点内容，并刷新右侧信息
                    zookeeperToolService.loadNodeChildren(selectedItem);
                    zookeeperToolService.nodeSelectionChanged(selectedItem);
                }
            } else if (event.getButton() == MouseButton.SECONDARY) {
                MenuItem menu_UnfoldAll = new MenuItem(I18N.get("menu.unfold_all"));
                menu_UnfoldAll.setOnAction(event1 -> {
                    nodeTreeView.getRoot().setExpanded(true);
                    nodeTreeView.getRoot().getChildren().forEach(stringTreeItem -> {
                        stringTreeItem.setExpanded(true);
                    });
                });
                MenuItem menu_FoldAll = new MenuItem(I18N.get("menu.fold_all"));
                menu_FoldAll.setOnAction(event1 -> {
                    nodeTreeView.getRoot().setExpanded(false);
                    nodeTreeView.getRoot().getChildren().forEach(stringTreeItem -> {
                        stringTreeItem.setExpanded(false);
                    });
                });
                ContextMenu contextMenu = new ContextMenu(menu_UnfoldAll, menu_FoldAll);
                MenuItem menu_AddNode = new MenuItem(I18N.get("menu.add_node"));
                menu_AddNode.setOnAction(event1 -> {
                    zookeeperToolService.addNodeOnAction();
                });
                contextMenu.getItems().add(menu_AddNode);
                MenuItem menu_Rename = new MenuItem(I18N.get("menu.rename"));
                menu_Rename.setOnAction(event1 -> {
                    zookeeperToolService.renameNodeOnAction(false);
                });
                contextMenu.getItems().add(menu_Rename);
                MenuItem menu_Copy = new MenuItem(I18N.get("menu.copy"));
                menu_Copy.setOnAction(event1 -> {
                    zookeeperToolService.renameNodeOnAction(true);
                });
                contextMenu.getItems().add(menu_Copy);
                MenuItem menu_RemoveNode = new MenuItem(I18N.get("menu.remove_node"));
                menu_RemoveNode.setOnAction(event1 -> {
                    zookeeperToolService.deleteNodeOnAction();
                });
                contextMenu.getItems().add(menu_RemoveNode);
                MenuItem menu_AddNodeNotify = new MenuItem(I18N.get("menu.add_node_notify"));
                menu_AddNodeNotify.setOnAction(event1 -> {
                    zookeeperToolService.addNodeNotify();
                });
                contextMenu.getItems().add(menu_AddNodeNotify);
                MenuItem menu_RemoveNodeNotify = new MenuItem(I18N.get("menu.remove_node_notify"));
                menu_RemoveNodeNotify.setOnAction(event1 -> {
                    zookeeperToolService.removeNodeNotify();
                });
                contextMenu.getItems().add(menu_RemoveNodeNotify);
                nodeTreeView.setContextMenu(contextMenu);
            }
        });
    }

    private void initService() {
    }

    @FXML
    private void connectOnAction(ActionEvent event) {
        zookeeperToolService.connectOnAction();
    }

    @FXML
    private void disconnectOnAction(ActionEvent event) {
        zookeeperToolService.disconnectOnAction();
    }

    @FXML
    private void refreshOnAction(ActionEvent event) {
        zookeeperToolService.refreshOnAction();
    }

    @FXML
    private void nodeDataSaveOnAction(ActionEvent event) {
        zookeeperToolService.nodeDataSaveOnAction();
    }

    public void dispose() {
        nodeSearchController.close();
        zookeeperToolService.close();
    }

    public void refreshAfterSearchMutation() {
        if (nodeSearchController.isSearching()) {
            nodeSearchController.reset();
            zookeeperToolService.refreshOnAction();
        }
    }

}
