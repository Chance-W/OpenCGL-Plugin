package com.opencgl.plugin.zookeeper.service;

import com.opencgl.plugin.zookeeper.controller.ZookeeperToolController;
import com.opencgl.plugin.zookeeper.i18n.I18N;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.plugin.zookeeper.utils.AlertUtil;
import com.opencgl.base.utils.LoadingMask;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.TreeItem;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Chance.W
 */
@Getter
@Setter
@Slf4j
public class ZookeeperToolService {
    private static final Logger log = LoggerFactory.getLogger(ZookeeperToolService.class);
    public ZookeeperToolController zookeeperToolController;

    private volatile ZkClient zkClient = null;
    private long selectionRequest;
    private long connectionRequest;

    private Map<String, IZkChildListener> childListeners = new HashMap<>();
    private Map<String, IZkDataListener> dataListeners = new HashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-zookeeper-worker");
        thread.setDaemon(true);
        return thread;
    });

    // 辅助类，用于在后台线程抓取数据
    private static class ZkNode {
        String name;
        boolean hasChildren;

        ZkNode(String name, boolean hasChildren) {
            this.name = name;
            this.hasChildren = hasChildren;
        }
    }

    public ZookeeperToolService(ZookeeperToolController zookeeperToolController) {
        this.zookeeperToolController = zookeeperToolController;
    }

    public void connectOnAction() {
        final String servers = zookeeperToolController.getZkServersTextField().getText().trim();
        final int timeout = zookeeperToolController.getConnectionTimeoutSpinner().getValue();
        if (servers.isEmpty() || timeout <= 0) {
            com.opencgl.base.utils.DialogUtil.showErrorInfo(I18N.get("connection.invalid")); return;
        }
        final long request = ++connectionRequest;
        final ZkClient existing = zkClient;
        zookeeperToolController.getNodeSearchController().reset();
        LoadingMask mask = new LoadingMask();
        mask.show(zookeeperToolController.getMainAnchorPane());
        record Connected(ZkClient client, List<ZkNode> nodes) {}
        Task<Connected> task = new Task<>() {
            @Override
            protected Connected call() throws Exception {
                ZkClient client = existing;
                try {
                    if (client == null) {
                        client = new ZkClient(servers, timeout);
                        client.setZkSerializer(new ZkSerializer() {
                            @Override
                            public byte[] serialize(Object data) throws ZkMarshallingError {
                                return String.valueOf(data).getBytes(StandardCharsets.UTF_8);
                            }

                            @Override
                            public Object deserialize(byte[] bytes) throws ZkMarshallingError {
                                return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
                            }
                        });
                    }
                    return new Connected(client, fetchZkNodes(client, "/"));
                } catch (Exception e) {
                    if (client != null && client != existing) client.close();
                    throw e;
                }
            }
        };

        task.setOnSucceeded(e -> {
            mask.hide();
            if (request != connectionRequest) {
                if (task.getValue().client() != existing) task.getValue().client().close();
                return;
            }
            zkClient = task.getValue().client();
            TreeItem<String> root = zookeeperToolController.getNodeTreeView().getRoot();
            root.getChildren().clear();
            populateTreeItems(root, "/", task.getValue().nodes());
            root.setExpanded(true);
        });

        task.setOnFailed(e -> {
            mask.hide();
            if (request != connectionRequest) return;
            Throwable ex = task.getException();
            log.error("Connect failed", ex);
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.connect_failed"));
            closeClient();
        });

        executor.execute(task);
    }

    private List<ZkNode> fetchZkNodes(String path) {
        return fetchZkNodes(zkClient, path);
    }

    private List<ZkNode> fetchZkNodes(ZkClient client, String path) {
        List<String> names = client.getChildren(path);
        List<ZkNode> nodes = new java.util.ArrayList<>();
        for (String name : names) {
            String childPath = StringUtils.appendIfMissing(path, "/", "/") + name;
            Stat stat = client.getAcl(childPath).getValue();
            boolean hasChildren = stat != null && stat.getNumChildren() > 0;
            nodes.add(new ZkNode(name, hasChildren));
        }
        return nodes;
    }

    private void populateTreeItems(TreeItem<String> parentItem, String parentPath, List<ZkNode> nodes) {
        for (ZkNode node : nodes) {
            TreeItem<String> childItem = new TreeItem<>(node.name);
            parentItem.getChildren().add(childItem);

            childItem.expandedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    loadNodeChildren(childItem);
                    nodeSelectionChanged(childItem);
                }
            });

            if (node.hasChildren) {
                childItem.getChildren().add(new TreeItem<>(I18N.get("label.loading")));
            }
        }
    }

    public void loadNodeChildren(TreeItem<String> item) {
        if (item == null || zkClient == null || zookeeperToolController.getNodeSearchController().isSearching())
            return;

        // 如果已经加载过（首个子节点不是占位符且不为空），则跳过加载
        if (!item.getChildren().isEmpty()) {
            TreeItem<String> firstChild = item.getChildren().get(0);
            if (!I18N.get("label.loading").equals(firstChild.getValue())) {
                return;
            }
        }

        LoadingMask mask = new LoadingMask();
        mask.show(zookeeperToolController.getMainAnchorPane());
        String path = getNodePath(item);
        final ZkClient client = zkClient;

        Task<List<ZkNode>> task = new Task<List<ZkNode>>() {
            @Override
            protected List<ZkNode> call() throws Exception {
                // 如果是占位符模式加载，此时 children 确实是 ["label.loading"]，需要在这里决定是否加载
                // 实际上调用 loadNodeChildren 的场景都已经确认了需要加载（通过 expandedProperty 或双击）
                return fetchZkNodes(client, path);
            }
        };

        task.setOnSucceeded(e -> {
            if (client != zkClient) { mask.hide(); return; }
            item.getChildren().clear();
            populateTreeItems(item, path, task.getValue());
            item.setExpanded(true);
            mask.hide();
        });

        task.setOnFailed(e -> {
            mask.hide();
            log.error("Load children failed for path: " + path, task.getException());
        });

        executor.execute(task);
    }

    private String getNodePath(TreeItem<String> selectedItem) {
        StringBuilder stringBuffer = new StringBuilder();
        stringBuffer.append(selectedItem.getValue());
        TreeItem<String> indexItem = selectedItem.getParent();
        while (indexItem != null) {
            stringBuffer.insert(0, StringUtils.appendIfMissing(indexItem.getValue(), "/", "/"));
            indexItem = indexItem.getParent();
        }
        return stringBuffer.toString();
    }

    public void nodeSelectionChanged(TreeItem<String> selectedItem) {
        if (selectedItem == null || zkClient == null || I18N.get("label.loading").equals(selectedItem.getValue())) return;
        final ZkClient client = zkClient;
        final long request = ++selectionRequest;
        String nodePath = this.getNodePath(selectedItem);
        LoadingMask mask = new LoadingMask();
        mask.show(zookeeperToolController.getMainAnchorPane());

        Task<Map<String, Object>> task = new Task<Map<String, Object>>() {
            @Override
            protected Map<String, Object> call() throws Exception {
                if (!client.exists(nodePath)) {
                    return null;
                }
                Map<String, Object> result = new HashMap<>();
                result.put("data", client.readData(nodePath));
                result.put("aclEntry", client.getAcl(nodePath));
                return result;
            }
        };

        task.setOnSucceeded(e -> {
            mask.hide();
            var current = zookeeperToolController.getNodeTreeView().getSelectionModel().getSelectedItem();
            if (request != selectionRequest || client != zkClient || current == null
                    || !nodePath.equals(getNodePath(current))) return;
            Map<String, Object> result = task.getValue();
            if (result == null) {
                TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_not_exists"));
                return;
            }

            zookeeperToolController.getNodeDataValueTextArea().setText((String) result.get("data"));
            Map.Entry<List<ACL>, Stat> aclsEntry = (Map.Entry<List<ACL>, Stat>) result.get("aclEntry");
            Stat stat = aclsEntry.getValue();
            zookeeperToolController.getA_VERSIONTextField().setText("" + stat.getAversion());
            zookeeperToolController.getC_TIMETextField()
                    .setText(DateFormatUtils.format(stat.getCtime(), "yyyy-MM-dd'T'HH:mm:ss.SSS z"));
            zookeeperToolController.getC_VERSIONTextField().setText("" + stat.getCversion());
            zookeeperToolController.getCZXIDTextField().setText("0x" + Long.toHexString(stat.getCzxid()));
            zookeeperToolController.getDATA_LENGTHTextField().setText("" + stat.getDataLength());
            zookeeperToolController.getEPHEMERAL_OWNERTextField()
                    .setText("0x" + Long.toHexString(stat.getEphemeralOwner()));
            zookeeperToolController.getM_TIMETextField()
                    .setText(DateFormatUtils.format(stat.getMtime(), "yyyy-MM-dd'T'HH:mm:ss.SSS z"));
            zookeeperToolController.getMZXIDTextField().setText("0x" + Long.toHexString(stat.getMzxid()));
            zookeeperToolController.getNUM_CHILDRENTextField().setText("" + stat.getNumChildren());
            zookeeperToolController.getPZXIDTextField().setText("0x" + Long.toHexString(stat.getPzxid()));
            zookeeperToolController.getVERSIONTextField().setText("" + stat.getVersion());

            List<ACL> acls = aclsEntry.getKey();
            for (ACL acl : acls) {
                zookeeperToolController.getAclSchemeTextField().setText(acl.getId().getScheme());
                zookeeperToolController.getAclIdTextField().setText(acl.getId().getId());
                java.util.List<String> permList = new java.util.ArrayList<>();
                int perms = acl.getPerms();
                if ((perms & Perms.READ) == Perms.READ)
                    permList.add(I18N.get("label.perm.read"));
                if ((perms & Perms.WRITE) == Perms.WRITE)
                    permList.add(I18N.get("label.perm.write"));
                if ((perms & Perms.CREATE) == Perms.CREATE)
                    permList.add(I18N.get("label.perm.create"));
                if ((perms & Perms.DELETE) == Perms.DELETE)
                    permList.add(I18N.get("label.perm.delete"));
                if ((perms & Perms.ADMIN) == Perms.ADMIN)
                    permList.add(I18N.get("label.perm.admin"));
                zookeeperToolController.getAclPermissionsTextField().setText(String.join(", ", permList));
            }
        });

        task.setOnFailed(e -> mask.hide());
        executor.execute(task);
    }

    public void disconnectOnAction() {
        connectionRequest++;
        zookeeperToolController.getNodeSearchController().reset();
        selectionRequest++;
        closeClient();
        zookeeperToolController.getNodeTreeView().getRoot().getChildren().clear();
        zookeeperToolController.getNodeDataValueTextArea().clear();
    }

    public void refreshOnAction() {
        if (zkClient == null) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.zk_not_connected"));
            return;
        }
        connectOnAction();
    }

    public void deleteNodeOnAction() {
        TreeItem<String> selectedItem = zookeeperToolController.getNodeTreeView().getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_not_selected"));
            return;
        }
        String nodePath = this.getNodePath(selectedItem);
        LoadingMask mask = new LoadingMask();
        mask.show(zookeeperToolController.getMainAnchorPane());

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                zkClient.deleteRecursive(nodePath);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            selectedItem.getParent().getChildren().remove(selectedItem);
            mask.hide();
            zookeeperToolController.refreshAfterSearchMutation();
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.delete_success"));
        });

        task.setOnFailed(e -> {
            mask.hide();
            log.error("Delete node failed: " + nodePath, task.getException());
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.delete_failed"));
        });

        executor.execute(task);
    }

    public void addNodeOnAction() {
        TreeItem<String> selectedItem = zookeeperToolController.getNodeTreeView().getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_not_selected"));
            return;
        }
        String nodeName = AlertUtil.showInputAlert(I18N.get("prompt.enter_node_name"));
        if (StringUtils.isEmpty(nodeName)) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_name_empty"));
            return;
        }
        String nodePath = this.getNodePath(selectedItem);
        String newNodePath = StringUtils.appendIfMissing(nodePath, "/", "/") + nodeName;

        LoadingMask mask = new LoadingMask();
        mask.show(zookeeperToolController.getMainAnchorPane());

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                zkClient.createPersistent(newNodePath);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            TreeItem<String> treeItem2 = new TreeItem<>(nodeName);
            selectedItem.getChildren().add(treeItem2);
            mask.hide();
            zookeeperToolController.refreshAfterSearchMutation();
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.add_success"));
        });

        task.setOnFailed(e -> {
            mask.hide();
            log.error("Add node failed: " + newNodePath, task.getException());
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.add_failed"));
        });

        executor.execute(task);
    }

    public void renameNodeOnAction(boolean isCopy) {
        TreeItem<String> selectedItem = zookeeperToolController.getNodeTreeView().getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_not_selected"));
            return;
        }
        String nodeName = AlertUtil.showInputAlert(I18N.get("prompt.enter_new_node_name"));
        if (StringUtils.isEmpty(nodeName)) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_name_empty"));
            return;
        }

        String oldPath = getNodePath(selectedItem);
        String nodeParent = this.getNodePath(selectedItem.getParent());
        String nodeParentPath = StringUtils.appendIfMissing(nodeParent, "/", "/");
        String newPath = nodeParentPath + nodeName;

        LoadingMask mask = new LoadingMask();
        mask.show(zookeeperToolController.getMainAnchorPane());

        Task<List<ZkNode>> task = new Task<List<ZkNode>>() {
            @Override
            protected List<ZkNode> call() throws Exception {
                copyNode(oldPath, newPath);
                if (!isCopy) {
                    zkClient.deleteRecursive(oldPath);
                }
                if (isCopy) {
                    return fetchZkNodes(newPath);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            if (isCopy) {
                TreeItem<String> selectedItem2 = new TreeItem<>(nodeName);
                populateTreeItems(selectedItem2, newPath, task.getValue());
                selectedItem.getParent().getChildren().add(selectedItem2);
            } else {
                selectedItem.setValue(nodeName);
            }
            mask.hide();
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.operation_success"));
            zookeeperToolController.refreshAfterSearchMutation();
        });

        task.setOnFailed(e -> {
            mask.hide();
            log.error("Rename/Copy failed: " + oldPath + " -> " + newPath, task.getException());
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.operation_failed"));
        });

        executor.execute(task);
    }

    private void copyNode(String path, String copyPath) {
        zkClient.createPersistent(copyPath, zkClient.readData(path), zkClient.getAcl(path).getKey());
        List<String> list = zkClient.getChildren(path);
        for (String name : list) {
            copyNode(StringUtils.appendIfMissing(path, "/", "/") + name,
                    StringUtils.appendIfMissing(copyPath, "/", "/") + name);
        }
    }

    public void nodeDataSaveOnAction() {
        TreeItem<String> selectedItem = zookeeperToolController.getNodeTreeView().getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_not_selected"));
            return;
        }
        String nodePath = this.getNodePath(selectedItem);
        String newData = zookeeperToolController.getNodeDataValueTextArea().getText();

        LoadingMask mask = new LoadingMask();
        mask.show(zookeeperToolController.getMainAnchorPane());

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                zkClient.writeData(nodePath, newData);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            mask.hide();
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.save_success"));
        });

        task.setOnFailed(e -> {
            mask.hide();
            log.error("Save node data failed: " + nodePath, task.getException());
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.save_failed"));
        });

        executor.execute(task);
    }

    public void addNodeNotify() {// 添加节点通知
        TreeItem<String> selectedItem = zookeeperToolController.getNodeTreeView().getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_not_selected"));
            return;
        }
        String nodePath = this.getNodePath(selectedItem);
        if (childListeners.containsKey(nodePath)) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(),
                    I18N.get("message.notify_already_added"));
            return;
        }
        IZkChildListener childListener = (parentPath, currentChilds) -> TooltipUtil.showToast(
                zookeeperToolController.getMainAnchorPane(),
                I18N.get("message.node_child_changed") + " " + I18N.get("label.path") + parentPath + "\r\n "
                        + I18N.get("label.child_nodes") + currentChilds.toString());
        zkClient.subscribeChildChanges(nodePath, childListener);
        childListeners.put(nodePath, childListener);
        IZkDataListener dataListener = new IZkDataListener() {
            @Override
            public void handleDataChange(String dataPath, Object data) {
                TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(),
                        I18N.get("message.node_data_changed") + " " + I18N.get("label.path") + dataPath + "\r\n "
                                + I18N.get("label.new_data") + data.toString());
            }

            @Override
            public void handleDataDeleted(String dataPath) {
                TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(),
                        I18N.get("message.node_deleted") + " " + I18N.get("label.path") + dataPath);
            }
        };
        zkClient.subscribeDataChanges(nodePath, dataListener);
        dataListeners.put(nodePath, dataListener);
        TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.notify_add_success"));
    }

    public void removeNodeNotify() {// 移除节点通知
        TreeItem<String> selectedItem = zookeeperToolController.getNodeTreeView().getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.node_not_selected"));
            return;
        }
        String nodePath = this.getNodePath(selectedItem);
        zkClient.unsubscribeChildChanges(nodePath, childListeners.remove(nodePath));
        zkClient.unsubscribeDataChanges(nodePath, dataListeners.remove(nodePath));
        TooltipUtil.showToast(zookeeperToolController.getMainAnchorPane(), I18N.get("message.notify_remove_success"));
    }

    public void close() {
        connectionRequest++;
        closeClient();
        executor.shutdownNow();
    }

    private void closeClient() {
        childListeners.clear();
        dataListeners.clear();
        if (zkClient != null) {
            zkClient.close();
            zkClient = null;
        }
    }
}
