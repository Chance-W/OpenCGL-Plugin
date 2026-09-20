package com.opencgl.redis.components;

import com.opencgl.redis.model.RedisKeyInfo;
import com.opencgl.redis.service.RedisConnectionManager;
import com.opencgl.redis.i18n.I18N;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Redis Key 浏览器
 * 支持树形展示和数据编辑
 */
public class RedisKeyBrowser extends SplitPane {

    private final RedisConnectionManager connectionManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-redis-browser");
        thread.setDaemon(true);
        return thread;
    });
    private final TreeView<String> keyTree;
    private final VBox valuePane;
    private final TextArea valueArea;
    private final Label keyInfoLabel;
    private final TextField searchField;

    private String currentKey;
    private RedisKeyInfo.KeyType currentKeyType; // 当前选中 key 的类型
    private final Map<String, TreeItem<String>> keyNodes = new HashMap<>();
    private long loadGeneration;
    private boolean disposed;

    public RedisKeyBrowser(RedisConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        setDividerPositions(0.35);

        // 左侧: Key 树
        VBox leftPane = new VBox(8);
        leftPane.setPadding(new Insets(12));
        leftPane.setStyle("-fx-background-color: -theme-bg-secondary;");

        // 搜索框
        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText(I18N.get("prompt.search_key"));
        searchField.setStyle("-fx-background-color: -theme-bg-primary; -fx-border-color: -theme-border; -fx-text-inner-color: -theme-text; -fx-border-radius: 4;");
        searchField.setOnAction(e -> loadKeys(searchField.getText())); // 支持回车搜索
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button(I18N.get("button.search"));
        searchBtn.setStyle("-fx-background-color: -theme-accent; -fx-text-fill: -theme-text-inverse;");
        searchBtn.setOnAction(e -> loadKeys(searchField.getText()));

        Button refreshBtn = new Button(I18N.get("button.refresh"));
        refreshBtn.setStyle("-fx-background-color: -theme-secondary; -fx-text-fill: -theme-text-inverse;");
        refreshBtn.setOnAction(e -> {
            searchField.clear();
            loadKeys("*");
        });

        searchBox.getChildren().addAll(searchField, searchBtn, refreshBtn);

        // Key 树
        keyTree = new TreeView<>();
        keyTree.setRoot(new TreeItem<>("Keys"));
        keyTree.setShowRoot(false);
        keyTree.setCellFactory(param -> new KeyTreeCell());
        MenuItem createKey = new MenuItem(I18N.get("newkey.title"));
        createKey.setOnAction(e -> showNewKeyDialog());
        keyTree.setContextMenu(new ContextMenu(createKey));
        keyTree.setOnContextMenuRequested(e -> {
            javafx.scene.Node node = e.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof TreeCell<?>)) node = node.getParent();
            if (node instanceof TreeCell<?> cell && !cell.isEmpty()) {
                keyTree.getSelectionModel().select(cell.getIndex());
            } else keyTree.getSelectionModel().clearSelection();
        });
        VBox.setVgrow(keyTree, Priority.ALWAYS);

        keyTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                // 点击 "加载更多" 虚拟节点
                if (newVal.getValue().startsWith(LOAD_MORE_MARKER)) {
                    loadNextPage();
                    return;
                }
                if (newVal.isLeaf()) {
                    loadKeyValue(getFullKey(newVal));
                }
            }
        });

        leftPane.getChildren().addAll(searchBox, keyTree);

        // 右侧: 值编辑器
        valuePane = new VBox(12);
        valuePane.setPadding(new Insets(12));
        valuePane.setStyle("-fx-background-color: -theme-bg-primary;");

        // Key 信息
        keyInfoLabel = new Label(I18N.get("message.select_key"));
        keyInfoLabel.setStyle("-fx-text-fill: -theme-text-secondary;");

        // 值编辑区 (先初始化，因为按钮事件引用它)
        valueArea = new TextArea();
        valueArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 13px;");
        valueArea.setWrapText(true);
        VBox.setVgrow(valueArea, Priority.ALWAYS);

        // 操作按钮
        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button(I18N.get("button.save"));
        saveBtn.setStyle("-fx-background-color: -theme-success; -fx-text-fill: -theme-text-inverse;");
        saveBtn.setOnAction(e -> saveValue());

        Button deleteBtn = new Button(I18N.get("button.delete"));
        deleteBtn.setStyle("-fx-background-color: -theme-danger; -fx-text-fill: -theme-text-inverse;");
        deleteBtn.setOnAction(e -> deleteKey());

        Button copyBtn = new Button(I18N.get("button.copy_value"));
        copyBtn.setStyle("-fx-background-color: -theme-info; -fx-text-fill: -theme-text-inverse;");
        copyBtn.setOnAction(e -> {
            if (valueArea.getText() != null) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(valueArea.getText());
                clipboard.setContent(content);
            }
        });

        buttonBox.getChildren().addAll(saveBtn, deleteBtn, copyBtn);

        valuePane.getChildren().addAll(keyInfoLabel, buttonBox, new Separator(), valueArea);

        getItems().addAll(leftPane, valuePane);
    }

    // ========== 懒加载状态 ==========
    private static final String LOAD_MORE_MARKER = "⏬ "; // 加载更多标记前缀
    private String lastPattern;
    private String lastCursor;
    private int lastClusterNodeIndex;
    private int totalLoaded;
    private Map<String, TreeItem<String>> nodeMap = new HashMap<>(); // 复用树节点缓存
    private TreeItem<String> loadMoreItem;  // "加载更多" 虚拟节点

    /**
     * 加载 Keys（首次加载，重置状态）
     */
    public void loadKeys(String pattern) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            return;
        }

        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = "*";
        } else {
            pattern = pattern.trim();
            if (!pattern.contains("*") && !pattern.contains("?")) {
                pattern = "*" + pattern + "*";
            }
        }

        // 重置分页状态
        lastPattern = pattern;
        loadGeneration++;
        lastCursor = null;
        lastClusterNodeIndex = 0;
        totalLoaded = 0;
        nodeMap.clear();
        keyNodes.clear();
        loadMoreItem = null;

        // 创建新的根节点
        TreeItem<String> root = new TreeItem<>(I18N.get("label.keys_count", 0));
        root.setExpanded(true);
        keyTree.setRoot(root);

        // 加载第一页
        loadNextPage();
    }

    /**
     * 加载下一页 Keys（懒加载核心）
     */
    private void loadNextPage() {
        final long generation = loadGeneration;
        final String pattern = lastPattern;
        final String cursor = lastCursor;
        final int clusterIdx = lastClusterNodeIndex;

        executor.execute(() -> {
            RedisConnectionManager.ScanKeysResult result =
                connectionManager.scanKeys(pattern, cursor, clusterIdx);

            Platform.runLater(() -> {
                if (disposed || generation != loadGeneration) return;
                TreeItem<String> root = keyTree.getRoot();

                // 移除旧的 "加载更多" 节点
                if (loadMoreItem != null) {
                    root.getChildren().remove(loadMoreItem);
                    loadMoreItem = null;
                }

                // 将新 keys 插入树
                for (String key : result.getKeys()) {
                    insertKeyToTree(root, key);
                }
                totalLoaded = keyNodes.size();

                // 更新根节点文字
                String suffix = result.hasMore() ? "+" : "";
                root.setValue(I18N.get("label.keys_count", totalLoaded) + suffix);

                // 如果还有更多数据，追加 "加载更多" 虚拟节点
                if (result.hasMore()) {
                    loadMoreItem = new TreeItem<>(LOAD_MORE_MARKER + I18N.get("button.load_more", "加载更多..."));
                    root.getChildren().add(loadMoreItem);

                    // 保存游标状态
                    lastCursor = result.getCursor();
                    lastClusterNodeIndex = result.getClusterNodeIndex();
                }
            });
        });
    }

    /**
     * 将一个 key 插入到树中（按 : 分隔构建层级）
     */
    private void insertKeyToTree(TreeItem<String> root, String key) {
        if (keyNodes.containsKey(key)) return;
        String[] parts = key.split(":", -1);
        TreeItem<String> parent = root;

        for (int i = 0; i < parts.length; i++) {
            String path = String.join(":", Arrays.copyOfRange(parts, 0, i + 1));

            if (i == parts.length - 1) {
                // 真正的叶子节点（即 Key 本身）
                TreeItem<String> leaf = new TreeItem<>(parts[i]);
                // 设置一个特殊属性或标志，以便 CellFactory 识别它是真正的 Key
                parent.getChildren().add(leaf);
                keyNodes.put(key, leaf);
            } else {
                // 中间目录节点
                TreeItem<String> node = nodeMap.get(path);
                if (node == null) {
                    node = new TreeItem<>(parts[i]);
                    node.setExpanded(false);
                    nodeMap.put(path, node);
                    parent.getChildren().add(node);
                }
                parent = node;
            }
        }
    }

    private String getFullKey(TreeItem<String> item) {
        List<String> parts = new ArrayList<>();
        TreeItem<String> current = item;

        while (current != null && current.getParent() != null) {
            parts.add(0, current.getValue());
            current = current.getParent();
        }

        return String.join(":", parts);
    }

    private void loadKeyValue(String key) {
        this.currentKey = key;
        this.currentKeyType = null;

        if (connectionManager == null || !connectionManager.isConnected()) {
            return;
        }

        executor.execute(() -> {
            RedisKeyInfo info = connectionManager.getKeyInfo(key);
            Object value = connectionManager.getValue(key);

            Platform.runLater(() -> {
                if (disposed || !Objects.equals(currentKey, key)) return;
                if (info != null) {
                    currentKeyType = info.getType();
                    keyInfoLabel.setText(I18N.get("label.key_info_format",
                            key, info.getTypeDisplay(), info.getTtlDisplay()));
                    keyInfoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -theme-text;");
                }

                // 格式化显示值
                valueArea.setText(formatValue(value, info != null ? info.getType() : null));
                if (info != null && info.getType() == RedisKeyInfo.KeyType.STREAM)
                    keyInfoLabel.setText(keyInfoLabel.getText() + " · " + I18N.get("newkey.stream.limit"));
            });
        });
    }

    private String formatValue(Object value, RedisKeyInfo.KeyType type) {
        if (value == null) {
            return I18N.get("label.nil");
        }

        if (value instanceof String) {
            return (String) value;
        }

        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder();
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append(I18N.get("format.map", entry.getKey(), entry.getValue())).append("\n");
            }
            return sb.toString();
        }

        if (value instanceof List) {
            StringBuilder sb = new StringBuilder();
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                sb.append(I18N.get("format.list", i, list.get(i))).append("\n");
            }
            return sb.toString();
        }

        if (value instanceof Set) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Object item : (Set<?>) value) {
                sb.append(I18N.get("format.list", i++, item)).append("\n");
            }
            return sb.toString();
        }

        return value.toString();
    }

    private void saveValue() {
        if (currentKey == null || currentKey.isEmpty()) {
            return;
        }
        if (connectionManager == null || !connectionManager.isConnected()) {
            return;
        }

        String newValue = valueArea.getText();
        if (newValue == null) {
            return;
        }

        if (currentKeyType == RedisKeyInfo.KeyType.STRING) {
            // STRING 类型直接 SET
            executor.execute(() -> {
                boolean ok = connectionManager.setStringValue(currentKey, newValue);
                Platform.runLater(() -> {
                    if (ok) {
                        keyInfoLabel.setText(I18N.get("message.value_saved", "已保存"));
                    }
                });
            });
        } else {
            // 非 STRING 类型暂不支持编辑保存
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(I18N.get("dialog.tip.title"));
            alert.setContentText(I18N.get("message.save_string_only", "目前仅支持 STRING 类型的保存"));
            alert.showAndWait();
        }
    }

    public void dispose() {
        disposed = true;
        executor.shutdownNow();
    }

    private void showNewKeyDialog() {
        if (connectionManager == null || !connectionManager.isConnected()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, I18N.get("newkey.disconnected"), ButtonType.OK);
            if (getScene() != null) alert.initOwner(getScene().getWindow());
            alert.show(); return;
        }
        TreeItem<String> selected = keyTree.getSelectionModel().getSelectedItem();
        String prefix = "";
        if (selected != null && selected != keyTree.getRoot() && selected != loadMoreItem) {
            TreeItem<String> directory = keyNodes.containsValue(selected) ? selected.getParent() : selected;
            if (directory != keyTree.getRoot()) prefix = getFullKey(directory) + ":";
        }
        NewRedisKeyDialog dialog = new NewRedisKeyDialog(connectionManager, executor, prefix);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.resultProperty().addListener((o, old, key) -> {
            if (key == null || disposed) return;
            searchField.clear(); loadKeys("*");
            insertKeyToTree(keyTree.getRoot(), key);
            TreeItem<String> item = keyNodes.get(key);
            for (TreeItem<String> p = item.getParent(); p != null; p = p.getParent()) p.setExpanded(true);
            keyTree.getSelectionModel().select(item);
            keyTree.scrollTo(keyTree.getRow(item));
            keyInfoLabel.setText(I18N.get("newkey.success"));
        });
        dialog.show();
    }

    private void deleteKey() {
        if (currentKey == null || currentKey.isEmpty()) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(I18N.get("dialog.delete_confirm.title"));
        confirm.setContentText(I18N.get("message.delete_confirm", currentKey));

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                boolean deleted = connectionManager.deleteKey(currentKey);
                if (deleted) {
                    keyNodes.remove(currentKey);
                    // 从树中移除当前选中节点（不重新加载整棵树）
                    TreeItem<String> selected = keyTree.getSelectionModel().getSelectedItem();
                    if (selected != null && selected.getParent() != null) {
                        TreeItem<String> parent = selected.getParent();
                        parent.getChildren().remove(selected);
                        // 递归清理空的父目录节点
                        cleanEmptyParents(parent);
                    }
                    // 更新计数
                    if (totalLoaded > 0) {
                        totalLoaded--;
                    }
                    TreeItem<String> root = keyTree.getRoot();
                    if (root != null) {
                        String suffix = (loadMoreItem != null) ? "+" : "";
                        root.setValue(I18N.get("label.keys_count", totalLoaded) + suffix);
                    }
                    valueArea.clear();
                    keyInfoLabel.setText(I18N.get("message.key_deleted"));
                    currentKey = null;
                    currentKeyType = null;
                }
            }
        });
    }

    /**
     * 递归清理空的父目录节点（没有子节点的中间节点）
     */
    private void cleanEmptyParents(TreeItem<String> node) {
        if (node == null || node == keyTree.getRoot()) {
            return;
        }
        if (node.getChildren().isEmpty()) {
            TreeItem<String> parent = node.getParent();
            if (parent != null) {
                // 从 nodeMap 中也移除
                nodeMap.values().remove(node);
                parent.getChildren().remove(node);
                cleanEmptyParents(parent);
            }
        }
    }

    /**
     * Key 树单元格
     */
    private static class KeyTreeCell extends TreeCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            setText(item);

            if (getTreeItem() != null && getTreeItem().isLeaf()) {
                setStyle("-fx-text-fill: -theme-accent;");
            } else {
                setStyle("-fx-text-fill: -theme-text; -fx-font-weight: bold;");
            }
        }
    }
}
