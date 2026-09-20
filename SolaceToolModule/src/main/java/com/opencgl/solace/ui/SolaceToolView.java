package com.opencgl.solace.ui;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.opencgl.solace.SolacePluginContext;
import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceOutboundMessage;
import com.opencgl.solace.model.SolaceReceivedMessage;
import com.opencgl.solace.model.SolaceSendHistoryItem;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import com.opencgl.solace.model.SolaceWorkspaceKind;
import com.opencgl.solace.persistence.SqliteSolaceSendHistoryRepository;
import com.opencgl.solace.service.JcsmpRuntimeFactory;
import com.opencgl.solace.service.SolaceConnectionTester;
import com.opencgl.solace.service.SolaceListenerConfig;
import com.opencgl.solace.service.SolaceListenerService;
import com.opencgl.solace.service.SolacePublishReceipt;
import com.opencgl.solace.service.SolacePublisherService;
import com.opencgl.solace.service.SolaceRequestValidator;
import com.opencgl.solace.tree.SolaceTreeService;
import com.opencgl.solace.variable.SolaceVariableResolver;
import com.opencgl.base.model.Base;
import com.opencgl.base.model.HistoryItem;
import com.opencgl.base.service.HistoryService;
import com.opencgl.base.utils.history.HistoryViewBuilder;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.base.view.RequestManagerView;
import com.solacesystems.jcsmp.DeliveryMode;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.FileChooser;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Shared tree editor; runtime behavior is selected by mode. */
public final class SolaceToolView implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SolaceToolView.class);
    public enum Mode { SEND, LISTEN }

    private final Mode mode;
    private final SolacePluginContext context;
    private final SolaceWorkspace workspace;
    private final SolaceVariableResolver resolver = new SolaceVariableResolver();
    private final SolacePublisherService publisher = new SolacePublisherService(new JcsmpRuntimeFactory());
    private final SolaceConnectionTester connectionTester = new SolaceConnectionTester(new JcsmpRuntimeFactory());
    private final SolaceListenerService listener = new SolaceListenerService(new JcsmpRuntimeFactory(), 2_000);
    private final StackPane root = new StackPane();
    private TreeView<SolaceTreeNode> tree;
    private final TextField treeSearch = new TextField();
    private final SolaceTreeService treeService;
    private final BorderPane editor = new BorderPane();
    private final Timeline listenerRefresh;
    private final ListView<SolaceReceivedMessage> messages = new ListView<>();
    private final List<SolaceReceivedMessage> receivedHistory = new ArrayList<>();
    private final TextField messageFilter = new TextField();
    private final SqliteSolaceSendHistoryRepository sendHistoryRepository;
    private HistoryViewBuilder<SolaceSendHistoryItem> sendHistoryBuilder;
    private SolaceSendHistoryItem selectedHistory;
    private final AutoCloseable workspaceListener;
    private SolaceTreeNode selected;
    private final WorkspaceRefreshGate workspaceRefreshGate = new WorkspaceRefreshGate();

    public SolaceToolView(Mode mode) {
        this.mode = mode;
        this.sendHistoryRepository = mode == Mode.SEND ? createSendHistoryRepository() : null;
        this.context = SolacePluginContext.get(mode == Mode.SEND ? SolaceWorkspaceKind.SEND : SolaceWorkspaceKind.LISTEN);
        this.workspace = context.workspace();
        this.treeService = new SolaceTreeService(workspace, mode, context::save, node -> {
            selected = node;
            showSelected();
        });
        root.getStyleClass().add("solace-root");
        buildLayout();
        rebuildTree();
        workspaceListener = context.addChangeListener(() -> Platform.runLater(() -> {
            if (workspaceRefreshGate.shouldRefresh()) rebuildTree();
        }));
        listenerRefresh = new Timeline(new KeyFrame(Duration.millis(250), event -> drainMessages()));
        listenerRefresh.setCycleCount(Timeline.INDEFINITE);
        if (mode == Mode.LISTEN) listenerRefresh.play();
    }

    public StackPane root() { return root; }

    private void buildLayout() {
        editor.setCenter(emptyState());
        ThemeSwitchUtil.treeStyleSwitch(root, editor, buildNavigation());
    }

    private Node buildNavigation() {
        VBox treeViewContainer = new TreeViewBuilder<SolaceTreeNode>()
            .service(treeService)
            .dataType(SolaceTreeNode.class)
            .data(treeService.queryAll())
            .showRoot(true)
            .rootExpanded(true)
            .enableDragDrop(false)
            .contextMenuFactory(this::createTreeContextMenu)
            .onTreeCreated(value -> this.tree = value)
            .onSelect(value -> {
                if (value.getId() == null || value.getId() == 0L) return;
                selected = value;
                showSelected();
            })
            .build();

        treeSearch.setPromptText("搜索节点。。。");
        treeSearch.getStyleClass().add("tree-search-field");
        treeSearch.setMaxWidth(Double.MAX_VALUE);
        treeSearch.setPrefHeight(30);
        treeSearch.textProperty().addListener((observable, oldValue, newValue) -> rebuildTree());
        VBox collections = new VBox(5, buildTreeToolbar(), treeSearch, treeViewContainer);
        VBox.setVgrow(treeViewContainer, Priority.ALWAYS);
        RequestManagerView requestManager = new RequestManagerView();
        requestManager.setCollectionView(collections);
        if (mode == Mode.SEND) {
            requestManager.setHistoryView(buildSidebarHistory());
            requestManager.getTabPane().getSelectionModel().selectedIndexProperty().addListener((observable, oldIndex, newIndex) -> {
                if (newIndex.intValue() == 0) {
                    showSelected();
                } else {
                    if (sendHistoryBuilder != null) sendHistoryBuilder.refresh();
                    if (selectedHistory == null) editor.setCenter(historyEmptyState());
                    else showSendHistoryDetail(selectedHistory);
                }
            });
        } else requestManager.getTabPane().getTabs().remove(1);
        requestManager.setMinWidth(315);
        return requestManager.getTabPane();
    }

    private Node buildSidebarHistory() {
        sendHistoryBuilder = new HistoryViewBuilder<SolaceSendHistoryItem>()
            .service(new HistoryService() {
                @Override public List<SolaceSendHistoryItem> getHistory() {
                    if (sendHistoryRepository == null) return List.of();
                    try { return sendHistoryRepository.query(); }
                    catch (IOException ignored) { return List.of(); }
                }

                @Override public void clearHistory() {
                    try {
                        if (sendHistoryRepository != null) sendHistoryRepository.clear();
                        selectedHistory = null;
                    }
                    catch (IOException error) { showError(error.getMessage()); }
                }

                @Override public void deleteHistory(HistoryItem item) {
                    try {
                        if (sendHistoryRepository != null) sendHistoryRepository.delete(item.getId());
                        if (selectedHistory != null && Objects.equals(selectedHistory.getId(), item.getId())) {
                            selectedHistory = null;
                        }
                    }
                    catch (IOException error) { showError(error.getMessage()); }
                }
            })
            .cellDisplay(config -> config
                .primaryText(SolaceSendHistoryItem::getDestination)
                .secondaryText(SolaceSendHistoryItem::getDeliveryMode)
                .badgeText(SolaceSendHistoryItem::getStatus)
                .timestampField(SolaceSendHistoryItem::getTimestamp))
            .onSelect(item -> {
                selectedHistory = item;
                showSendHistoryDetail(item);
            })
            .restoreAction(this::restoreSendHistory);
        return sendHistoryBuilder.build();
    }

    private HBox buildTreeToolbar() {
        Button add = iconButton("fas-plus", "新增", event -> showAddMenu(addSource(event)));
        Button copy = iconButton("fas-copy", "复制", event -> copySelected());
        Button delete = iconButton("fas-trash", "删除", event -> deleteSelected());
        Button locate = iconButton("fas-crosshairs", "定位当前配置", event -> select(selected == null ? null : selected.getId()));
        Button collapse = iconButton("fas-compress", "全部折叠", event -> setExpanded(tree.getRoot(), false));
        Button expand = iconButton("fas-expand", "全部展开", event -> setExpanded(tree.getRoot(), true));
        HBox toolbar = new HBox(5, add, copy, delete, locate, collapse, expand);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(2, 5, 2, 5));
        return toolbar;
    }

    private Button iconButton(String icon, String tooltip, javafx.event.EventHandler<javafx.scene.input.MouseEvent> action) {
        Button button = new Button();
        button.setGraphic(new MFXFontIcon(icon, 16));
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("icon-button");
        button.setOnMouseClicked(action);
        return button;
    }

    private Button addSource(javafx.scene.input.MouseEvent event) { return (Button) event.getSource(); }

    private void showAddMenu(Button owner) {
        ContextMenu menu = new ContextMenu();
        MenuItem group = new MenuItem("新建组");
        MenuItem connection = new MenuItem("新建连接");
        MenuItem operation = new MenuItem(mode == Mode.SEND ? "新建发送" : "新建监听");
        group.setOnAction(event -> createTreeNode(SolaceNodeType.DIRECTORY));
        connection.setOnAction(event -> createTreeNode(SolaceNodeType.CONNECTION));
        operation.setOnAction(event -> createTreeNode(mode == Mode.SEND ? SolaceNodeType.SEND : SolaceNodeType.LISTEN));
        menu.getItems().addAll(group, connection, operation);
        menu.show(owner, Side.BOTTOM, 0, 0);
    }

    private ContextMenu createTreeContextMenu(SolaceTreeNode node) {
        ContextMenu menu = new ContextMenu();
        if (node == null) return menu;
        if (node.getId() == null || node.getId() == 0L || node.getNodeType() == SolaceNodeType.DIRECTORY) {
            MenuItem group = new MenuItem("新建组");
            MenuItem connection = new MenuItem("新建连接");
            group.setOnAction(event -> { selected = node; createTreeNode(SolaceNodeType.DIRECTORY); });
            connection.setOnAction(event -> { selected = node; createTreeNode(SolaceNodeType.CONNECTION); });
            menu.getItems().addAll(group, connection);
        }
        if (node.getNodeType() == SolaceNodeType.CONNECTION) {
            MenuItem operation = new MenuItem(mode == Mode.SEND ? "新建发送" : "新建监听");
            operation.setOnAction(event -> { selected = node; createTreeNode(mode == Mode.SEND ? SolaceNodeType.SEND : SolaceNodeType.LISTEN); });
            menu.getItems().add(operation);
        }
        if (node.getId() != null && node.getId() != 0L) {
            MenuItem rename = new MenuItem("重命名");
            rename.setOnAction(event -> { selected = node; renameSelected(); });
            menu.getItems().add(rename);
            if (Boolean.TRUE.equals(node.getIsLeaf())) {
                MenuItem copy = new MenuItem("复制当前节点");
                copy.setOnAction(event -> { selected = node; copySelected(); });
                menu.getItems().add(copy);
            }
            MenuItem delete = new MenuItem("删除当前节点");
            delete.setOnAction(event -> { selected = node; deleteSelected(); });
            menu.getItems().add(delete);
        }
        return menu;
    }

    private void createTreeNode(SolaceNodeType type) {
        try {
            SolaceTreeNode parent = switch (type) {
                case DIRECTORY -> selected == null || selected.getId() == null || selected.getId() == 0L
                    ? null : nearestDirectory(selected);
                case CONNECTION -> selected == null || selected.getId() == null || selected.getId() == 0L
                    ? null : nearestDirectory(selected);
                case SEND, LISTEN -> nearestConnection(selected);
            };
            if ((type == SolaceNodeType.SEND || type == SolaceNodeType.LISTEN) && parent == null) {
                showError("请先选择一个连接节点，再新建" + (mode == Mode.SEND ? "发送" : "监听") + "配置。");
                return;
            }
            TextInputDialog dialog = new TextInputDialog(defaultNodeName(type));
            dialog.setTitle("新增 Solace 节点");
            dialog.setHeaderText(null);
            dialog.setContentText("名称：");
            dialog.showAndWait().filter(value -> !value.isBlank()).ifPresent(name -> {
                try {
                    SolaceTreeNode created = treeService.create(parent, type, name);
                    selected = created;
                    rebuildTree();
                    select(created.getId());
                } catch (RuntimeException error) {
                    showError(rootCause(error));
                }
            });
        } catch (RuntimeException error) {
            showError(rootCause(error));
        }
    }

    private String defaultNodeName(SolaceNodeType type) {
        return switch (type) {
            case DIRECTORY -> "新分组";
            case CONNECTION -> "新 Solace 连接";
            case SEND -> "新发送配置";
            case LISTEN -> "新监听配置";
        };
    }

    private SolaceTreeNode nearestDirectory(SolaceTreeNode node) {
        SolaceTreeNode current = node;
        while (current != null) {
            if (current.getNodeType() == SolaceNodeType.DIRECTORY) return current;
            current = findNode(current.getParentId());
        }
        return null;
    }

    private SolaceTreeNode nearestConnection(SolaceTreeNode node) {
        SolaceTreeNode current = node;
        while (current != null) {
            if (current.getNodeType() == SolaceNodeType.CONNECTION) return current;
            current = findNode(current.getParentId());
        }
        return null;
    }

    private SolaceTreeNode findNode(Long id) {
        if (id == null || id == 0L) return null;
        return workspace.getNodes().stream().filter(item -> Objects.equals(id, item.getId())).findFirst().orElse(null);
    }

    private void copySelected() {
        if (selected == null || !Boolean.TRUE.equals(selected.getIsLeaf())) {
            showError("只能复制发送或监听配置节点。");
            return;
        }
        try {
            CustomizeTreeItem<SolaceTreeNode> copy = treeService.copy(selected);
            if (copy != null) {
                selected = copy.getValue();
                rebuildTree();
                select(selected.getId());
            }
        } catch (RuntimeException error) {
            showError(rootCause(error));
        }
    }

    private void renameSelected() {
        if (selected == null || selected.getId() == null || selected.getId() == 0L) return;
        TextInputDialog dialog = new TextInputDialog(selected.getName());
        dialog.setTitle("重命名");
        dialog.setHeaderText(null);
        dialog.setContentText("名称：");
        dialog.showAndWait().filter(value -> !value.isBlank()).ifPresent(name -> {
            selected.setName(name.trim());
            try {
                treeService.update(selected);
                rebuildTree();
                select(selected.getId());
            } catch (RuntimeException error) {
                showError(rootCause(error));
            }
        });
    }

    private void setExpanded(TreeItem<SolaceTreeNode> item, boolean expanded) {
        if (item == null) return;
        item.setExpanded(expanded);
        item.getChildren().forEach(child -> setExpanded(child, expanded));
    }

    private Node emptyState() {
        VBox box = new VBox(10, new Label("请从左侧选择配置"),
            new Label(mode == Mode.SEND ? "发送树独立保存连接、变量和发送配置。" : "监听树独立保存连接、变量和监听配置。"));
        box.setPadding(new Insets(30));
        return box;
    }

    private void deleteSelected() {
        if (selected == null || selected.getId() == null || selected.getId() == 0L) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "确认删除“" + selected.getName() + "”及其所有子节点吗？", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            treeService.delete(selected);
            selected = null;
            rebuildTree();
            editor.setCenter(emptyState());
        } catch (RuntimeException error) {
            showError(rootCause(error));
        }
    }

    private void rebuildTree() {
        if (tree == null) return;
        SolaceTreeNode rootNode = new SolaceTreeNode();
        rootNode.setId(0L);
        rootNode.setParentId(-1L);
        rootNode.setName("数据列表");
        rootNode.setNodeType(SolaceNodeType.DIRECTORY);
        rootNode.setIsLeaf(false);
        TreeItem<SolaceTreeNode> rootItem = new CustomizeTreeItem<>(rootNode);
        List<SolaceTreeNode> visibleNodes = treeService.query(treeSearch.getText());
        Map<Long, TreeItem<SolaceTreeNode>> items = new LinkedHashMap<>();
        for (SolaceTreeNode node : visibleNodes) items.put(node.getId(), new CustomizeTreeItem<>(node));
        for (SolaceTreeNode node : visibleNodes) {
            TreeItem<SolaceTreeNode> item = items.get(node.getId());
            TreeItem<SolaceTreeNode> parent = node.getParentId() == null || node.getParentId() == 0L
                ? rootItem : items.get(node.getParentId());
            (parent == null ? rootItem : parent).getChildren().add(item);
        }
        rootItem.setExpanded(true);
        rootItem.getChildren().forEach(item -> item.setExpanded(true));
        tree.setRoot(rootItem);
        tree.refresh();
        tree.requestLayout();
        Long selectedId = selected == null ? null : selected.getId();
        if (selectedId != null) Platform.runLater(() -> select(selectedId));
    }

    private void showSelected() {
        if (selected == null) { editor.setCenter(emptyState()); return; }
        if (selected.getNodeType() == SolaceNodeType.DIRECTORY) editor.setCenter(directoryEditor(selected));
        else if (selected.getNodeType() == SolaceNodeType.CONNECTION) editor.setCenter(connectionEditor(selected));
        else if (selected.getNodeType() == SolaceNodeType.SEND && mode == Mode.SEND) editor.setCenter(sendEditor(selected));
        else if (selected.getNodeType() == SolaceNodeType.LISTEN && mode == Mode.LISTEN) editor.setCenter(listenerEditor(selected));
        else editor.setCenter(new VBox(10, new Label("该节点属于另一个 Solace 插件。"),
            new Label(mode == Mode.SEND ? "请在“Solace 监听”中打开。" : "请在“Solace 发送”中打开。")));
    }

    private Node directoryEditor(SolaceTreeNode node) {
        TextField name = text(node.getName());
        CustomTextArea variables = codeArea(formatMap(node.getVariables()));
        Button save = primary("保存环境");
        save.setOnAction(event -> {
            node.setName(name.getText().trim());
            node.setVariables(parseMap(variables.getText()));
            if (saveAndRebuild(node.getId())) showWeakMessage("环境配置已保存");
        });
        GridPane grid = form();
        row(grid, 0, "环境名称", name);
        row(grid, 1, "环境变量", editorPane(variables, 220));
        return section("环境 / 分组", new Label("变量格式为 KEY=VALUE，子连接和发送/监听配置会自动继承。"), grid, save);
    }

    private Node connectionEditor(SolaceTreeNode node) {
        SolaceConnectionConfig config = workspace.getConnections().get(node.getConnectionId());
        if (config == null) return emptyState();
        TextField name = text(node.getName());
        TextField host = text(config.getHost());
        TextField vpn = text(config.getMessageVpn());
        TextField username = text(config.getUsername());
        PasswordField password = new PasswordField(); password.setText(nullToEmpty(config.getPassword()));
        TextField client = text(config.getClientName());
        CheckBox remember = new CheckBox("本地加密保存密码");
        remember.setTooltip(new Tooltip("仅加密保存在本机配置中的密码；连接时仍使用输入的明文密码。"));
        remember.setSelected(config.isRememberPassword());
        TextField timeout = text(Integer.toString(config.getConnectTimeoutMillis()));
        TextField retries = text(Integer.toString(config.getReconnectRetries()));
        TextField retryWait = text(Integer.toString(config.getReconnectRetryWaitMillis()));
        CheckBox tls = new CheckBox("启用 TLS"); tls.setSelected(config.isTlsEnabled());
        CheckBox validate = new CheckBox("校验证书"); validate.setSelected(config.isValidateCertificate());
        TextField trustStore = text(config.getTrustStorePath());
        PasswordField trustPassword = new PasswordField(); trustPassword.setText(nullToEmpty(config.getTrustStorePassword()));
        ConnectionForm connectionForm = new ConnectionForm(host, vpn, username, password, client, remember,
            timeout, retries, retryWait, tls, validate, trustStore, trustPassword);
        GridPane grid = form();
        row(grid, 0, "连接名称", name); row(grid, 1, "Host", host); row(grid, 2, "Message VPN", vpn);
        row(grid, 3, "用户名", username); row(grid, 4, "密码", password); row(grid, 5, "Client Name", client);
        row(grid, 6, "连接超时(ms)", timeout); row(grid, 7, "重连次数", retries);
        row(grid, 8, "重连间隔(ms)", retryWait); row(grid, 9, "Trust Store", trustStore);
        row(grid, 10, "Trust Store 密码", trustPassword);
        Button test = new Button("测试连接");
        Button save = primary("保存连接");
        Label testStatus = new Label("未测试");
        test.setOnAction(event -> {
            try {
                runConnectionTest(resolvedConnection(node, readConnection(connectionForm)), test, testStatus);
            } catch (IllegalArgumentException error) {
                testStatus.setText("参数错误");
                showError(error.getMessage());
            }
        });
        save.setOnAction(event -> {
            node.setName(name.getText().trim());
            try { workspace.getConnections().put(node.getConnectionId(), readConnection(connectionForm)); }
            catch (IllegalArgumentException error) { showError(error.getMessage()); return; }
            if (saveAndRebuild(node.getId())) showWeakMessage("连接配置已保存");
        });
        Region connectionSpacer = new Region();
        HBox.setHgrow(connectionSpacer, Priority.ALWAYS);
        VBox content = section("连接配置", grid, new HBox(12, remember, tls, validate),
            new HBox(8, testStatus, connectionSpacer, test, save));
        return content;
    }

    private Node sendEditor(SolaceTreeNode node) {
        TextField name = text(node.getName());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("TOPIC", "QUEUE"));
        type.setValue(node.getSettings().getOrDefault("destinationType", "TOPIC"));
        TextField destination = text(node.getSettings().get("destination"));
        ComboBox<String> delivery = new ComboBox<>(FXCollections.observableArrayList("DIRECT", "PERSISTENT", "NON_PERSISTENT"));
        delivery.setValue(node.getSettings().getOrDefault("deliveryMode", "PERSISTENT"));
        TextField contentType = text(node.getSettings().getOrDefault("contentType", "application/json"));
        TextField correlation = text(node.getSettings().getOrDefault("correlationId", "${UUID}"));
        TextField replyTo = text(node.getSettings().get("replyTo"));
        TextField ttl = text(node.getSettings().getOrDefault("ttl", "0"));
        CustomTextArea body = codeArea(node.getSettings().getOrDefault("body", "{\n  \"id\": \"${UUID}\",\n  \"time\": ${TIMESTAMP}\n}"));
        CustomTextArea properties = codeArea(node.getSettings().get("properties"));
        CustomTextArea sendResult = codeArea("尚未发送");
        sendResult.setEditable(false);
        Label status = new Label("就绪");
        Label elapsed = new Label("耗时：--");
        Label responseSize = new Label("大小：--");
        Button save = new Button("保存");
        Button send = primary("发送");
        save.setOnAction(event -> {
            updateOperation(node, name, type, destination, delivery, contentType,
                correlation, replyTo, ttl, body, properties);
            if (saveAndRebuild(node.getId())) showWeakMessage("发送配置已保存");
        });
        send.setOnAction(event -> {
            updateOperation(node, name, type, destination, delivery, contentType,
                correlation, replyTo, ttl, body, properties);
            SolaceConnectionConfig rawConnection = workspace.getConnections().get(node.getConnectionId());
            if (rawConnection == null) { showError("连接配置不存在"); return; }
            var bodyResolution = resolve(node, body.getText());
            var destinationResolution = resolve(node, destination.getText());
            var correlationResolution = resolve(node, correlation.getText());
            var replyResolution = resolve(node, replyTo.getText());
            if (!bodyResolution.unresolvedVariables().isEmpty() || !destinationResolution.unresolvedVariables().isEmpty()) {
                showError("存在未解析变量：" + bodyResolution.unresolvedVariables() + destinationResolution.unresolvedVariables()); return;
            }
            SolaceOutboundMessage message;
            SolaceConnectionConfig connection;
            try {
                message = new SolaceOutboundMessage(
                    SolaceOutboundMessage.DestinationType.valueOf(type.getValue()), destinationResolution.value(),
                    bodyResolution.value(), contentType.getText().trim(), "UTF-8", correlationResolution.value(), replyResolution.value(),
                    DeliveryMode.valueOf(delivery.getValue()), parseLong(ttl.getText()), resolveMap(node, parseMap(properties.getText())));
                connection = resolvedConnection(node, rawConnection);
            }
            catch (IllegalArgumentException error) { showError(error.getMessage()); return; }
            List<String> missing = SolaceRequestValidator.validateSend(connection, message);
            if (!missing.isEmpty()) { showValidationErrors("无法发送消息", missing); return; }
            if (!saveWithoutRebuild()) return;
            long startedAt = System.currentTimeMillis();
            status.setText(message.deliveryMode() == DeliveryMode.DIRECT ? "正在发送…" : "已发送，等待 Broker ACK…");
            sendResult.replaceText("发送中…");
            elapsed.setText("耗时：--");
            responseSize.setText("大小：--");
            publisher.send(connection, message).whenComplete((receipt, error) -> Platform.runLater(() -> {
                long duration = System.currentTimeMillis() - startedAt;
                if (error != null) log.error("Solace message send failed: destinationType={}, destination={}",
                    message.destinationType(), message.destinationName(), error);
                String result = formatSendResult(receipt, error, duration);
                sendResult.replaceText(result);
                elapsed.setText("耗时：" + duration + " ms");
                responseSize.setText("大小：" + result.getBytes(StandardCharsets.UTF_8).length + " B");
                status.setText(error == null
                    ? (receipt.status() == SolacePublishReceipt.Status.BROKER_ACK ? "Broker 已确认" : "Direct 已发送")
                    : "发送失败 · " + ThrowableDetails.summary(error));
                persistSendHistory(node, message, properties.getText(), receipt, error, duration, result);
            }));
        });
        GridPane grid = form();
        pairRow(grid, 0, "配置名称", name, "Topic / Queue", destination);
        pairRow(grid, 1, "目标类型", type, "投递模式", delivery);
        pairRow(grid, 2, "Content Type", contentType, "TTL(ms)", ttl);
        pairRow(grid, 3, "Correlation ID", correlation, "Reply To", replyTo);
        HBox responseStatus = new HBox(15, status, new Region(), elapsed, responseSize);
        HBox.setHgrow(responseStatus.getChildren().get(1), Priority.ALWAYS);
        responseStatus.setAlignment(Pos.CENTER_LEFT);
        responseStatus.setPadding(new Insets(5));
        VBox responsePane = new VBox(responseStatus, editorPane(sendResult, 180));
        VBox.setVgrow(responsePane.getChildren().get(1), Priority.ALWAYS);
        SplitPane bodyAndResponse = new SplitPane(editorPane(body, 260), responsePane);
        bodyAndResponse.setOrientation(Orientation.VERTICAL);
        bodyAndResponse.setDividerPositions(0.60);
        TabPane editors = new TabPane(
            fixedTab("消息正文", bodyAndResponse),
            fixedTab("自定义属性（可选）", editorPane(properties, 420)));
        editors.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        editors.setPrefHeight(500);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox actionBar = new HBox(8, actionSpacer, save, send);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        return section("消息发送", grid, actionBar, editors);
    }

    private Node listenerEditor(SolaceTreeNode node) {
        TextField name = text(node.getName());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("TOPIC", "QUEUE"));
        type.setValue(node.getSettings().getOrDefault("destinationType", "TOPIC"));
        TextField destination = text(node.getSettings().get("destination"));
        CheckBox clientAck = new CheckBox("客户端 ACK（Queue）");
        clientAck.setSelected(Boolean.parseBoolean(node.getSettings().getOrDefault("clientAck", "false")));
        Label status = new Label("已停止");
        Button save = new Button("保存");
        Button start = primary("开始监听");
        Button pause = new Button("暂停");
        Button stop = new Button("停止");
        Button clear = new Button("清空消息");
        Button export = new Button("导出 JSON");
        save.setOnAction(event -> {
            updateListener(node, name, type, destination, clientAck);
            if (saveAndRebuild(node.getId())) showWeakMessage("监听配置已保存");
        });
        start.setOnAction(event -> {
            updateListener(node, name, type, destination, clientAck);
            SolaceConnectionConfig rawConnection = workspace.getConnections().get(node.getConnectionId());
            var resolved = resolve(node, destination.getText());
            if (rawConnection == null) { showValidationErrors("无法开始监听", List.of("连接配置")); return; }
            if (!resolved.unresolvedVariables().isEmpty()) {
                showError("Topic / Queue 存在未解析变量：" + resolved.unresolvedVariables()); return;
            }
            SolaceConnectionConfig connection;
            try { connection = resolvedConnection(node, rawConnection); }
            catch (IllegalArgumentException error) { showError(error.getMessage()); return; }
            SolaceListenerConfig listenerConfig = new SolaceListenerConfig(
                SolaceListenerConfig.DestinationType.valueOf(type.getValue()), resolved.value(), clientAck.isSelected());
            List<String> missing = SolaceRequestValidator.validateListener(connection, listenerConfig);
            if (!missing.isEmpty()) { showValidationErrors("无法开始监听", missing); return; }
            if (!saveWithoutRebuild()) return;
            status.setText("正在连接…");
            listener.start(connection, listenerConfig).whenComplete((ignored, error) -> Platform.runLater(() -> {
                if (error == null) status.setText("监听中");
                else {
                    String message = ThrowableDetails.summary(error);
                    status.setText("启动失败 · " + message);
                    showError("监听启动失败：\n" + ThrowableDetails.describe(error));
                }
            }));
        });
        pause.setOnAction(event -> { listener.pause(); status.setText("已暂停"); });
        stop.setOnAction(event -> listener.stop().whenComplete((ignored, error) -> Platform.runLater(() -> status.setText("已停止"))));
        clear.setOnAction(event -> { listener.clear(); receivedHistory.clear(); messages.getItems().clear(); });
        export.setOnAction(event -> exportMessages());
        messageFilter.setPromptText("过滤目标、消息 ID、正文或属性…");
        messageFilter.textProperty().addListener((obs, old, value) -> refreshMessageFilter());
        messages.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(SolaceReceivedMessage value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatReceivedMessage(value));
                setWrapText(!empty);
                setMaxWidth(Double.MAX_VALUE);
                setPrefHeight(Region.USE_COMPUTED_SIZE);
            }
        });
        GridPane grid = form(); row(grid, 0, "配置名称", name); row(grid, 1, "目标类型", type);
        row(grid, 2, "Topic / Queue", destination);
        VBox box = section("消息监听", grid, clientAck, new HBox(8, save, start, pause, stop, clear, export), status,
            new Separator(), new Label("接收消息（最多缓存 2000 条，超限丢弃最早消息）"), messageFilter, messages);
        VBox.setVgrow(messages, Priority.ALWAYS);
        return box;
    }

    private static String formatReceivedMessage(SolaceReceivedMessage message) {
        return "接收时间: " + message.receivedAtMillis()
            + "\n目标: " + nullToEmpty(message.destination())
            + "\n消息 ID: " + nullToEmpty(message.messageId())
            + "\nCorrelation ID: " + nullToEmpty(message.correlationId())
            + "\nContent-Type: " + nullToEmpty(message.contentType())
            + "\nContent-Encoding: " + nullToEmpty(message.contentEncoding())
            + "\n投递模式: " + String.valueOf(message.deliveryMode())
            + "\n大小: " + message.size() + " B"
            + "\n属性:\n" + JSON.toJSONString(message.properties(), JSONWriter.Feature.PrettyFormat)
            + "\n正文:\n" + nullToEmpty(message.body());
    }

    private void updateOperation(SolaceTreeNode node, TextField name, ComboBox<String> type,
                               TextField destination, ComboBox<String> delivery, TextField contentType,
                               TextField correlation, TextField replyTo, TextField ttl,
                               CustomTextArea body, CustomTextArea properties) {
        node.setName(name.getText().trim()); node.getSettings().put("destinationType", type.getValue());
        node.getSettings().put("destination", destination.getText());
        node.getSettings().put("deliveryMode", delivery.getValue());
        node.getSettings().put("contentType", contentType.getText());
        node.getSettings().put("correlationId", correlation.getText());
        node.getSettings().put("replyTo", replyTo.getText());
        node.getSettings().put("ttl", ttl.getText());
        node.getSettings().put("properties", properties.getText());
        node.getSettings().put("body", body.getText());
        node.setVariables(Map.of());
    }

    private Map<String, String> resolveMap(SolaceTreeNode node, Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(resolveRequired(node, "消息属性名", key),
            resolveRequired(node, "消息属性 " + key, value)));
        return result;
    }

    private void updateListener(SolaceTreeNode node, TextField name, ComboBox<String> type,
                              TextField destination, CheckBox clientAck) {
        node.setName(name.getText().trim()); node.getSettings().put("destinationType", type.getValue());
        node.getSettings().put("destination", destination.getText());
        node.getSettings().put("clientAck", Boolean.toString(clientAck.isSelected()));
        node.setVariables(Map.of());
    }

    private SolaceVariableResolver.Resolution resolve(SolaceTreeNode node, String value) {
        SolaceConnectionConfig connection = workspace.getConnections().get(node.getConnectionId());
        return resolver.resolve(value, new SolaceVariableResolver.VariableScope(environmentVariables(node), Map.of(), Map.of()));
    }

    private SolaceConnectionConfig resolvedConnection(SolaceTreeNode node, SolaceConnectionConfig source) {
        SolaceConnectionConfig target = new SolaceConnectionConfig();
        target.setHost(resolveRequired(node, "Host", source.getHost()));
        target.setMessageVpn(resolveRequired(node, "Message VPN", source.getMessageVpn()));
        target.setUsername(resolveRequired(node, "用户名", source.getUsername()));
        target.setPassword(resolveRequired(node, "密码", source.getPassword()));
        target.setClientName(resolveRequired(node, "Client Name", source.getClientName()));
        target.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
        target.setReconnectRetries(source.getReconnectRetries());
        target.setReconnectRetryWaitMillis(source.getReconnectRetryWaitMillis());
        target.setTlsEnabled(source.isTlsEnabled());
        target.setValidateCertificate(source.isValidateCertificate());
        target.setTrustStorePath(resolveRequired(node, "Trust Store", source.getTrustStorePath()));
        target.setTrustStorePassword(resolveRequired(node, "Trust Store 密码", source.getTrustStorePassword()));
        target.setVariables(Map.of());
        return target;
    }

    private String resolveRequired(SolaceTreeNode node, String field, String value) {
        var result = resolve(node, value);
        if (!result.unresolvedVariables().isEmpty()) {
            throw new IllegalArgumentException(field + " 存在未解析变量：" + result.unresolvedVariables());
        }
        return result.value();
    }

    private Map<String, String> environmentVariables(SolaceTreeNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        Long parentId = node.getParentId();
        List<SolaceTreeNode> ancestors = new ArrayList<>();
        while (parentId != null) {
            Long lookup = parentId;
            SolaceTreeNode parent = workspace.getNodes().stream().filter(item -> lookup.equals(item.getId())).findFirst().orElse(null);
            if (parent == null) break;
            if (parent.getNodeType() == SolaceNodeType.DIRECTORY) ancestors.add(0, parent);
            parentId = parent.getParentId();
        }
        ancestors.forEach(parent -> values.putAll(parent.getVariables()));
        return values;
    }

    private SqliteSolaceSendHistoryRepository createSendHistoryRepository() {
        try { return new SqliteSolaceSendHistoryRepository(Path.of(Base.DB_PATH, "data.db")); }
        catch (IOException error) { return null; }
    }

    private String formatSendResult(SolacePublishReceipt receipt, Throwable error, long durationMillis) {
        if (error != null) {
            return "状态：发送失败\n"
                + "完成时间：" + Instant.now() + "\n"
                + "耗时：" + durationMillis + " ms\n"
                + "错误详情：\n" + ThrowableDetails.describe(error);
        }
        return "状态：" + (receipt.status() == SolacePublishReceipt.Status.BROKER_ACK
            ? "Broker ACK" : "Direct 已接受") + "\n"
            + "Correlation Key：" + nullToEmpty(receipt.correlationKey()) + "\n"
            + "完成时间：" + Instant.ofEpochMilli(receipt.completedAtMillis()) + "\n"
            + "耗时：" + durationMillis + " ms\n"
            + "说明：" + nullToEmpty(receipt.message());
    }

    private void persistSendHistory(SolaceTreeNode node, SolaceOutboundMessage message, String rawProperties,
                                    SolacePublishReceipt receipt, Throwable error, long durationMillis, String result) {
        if (sendHistoryRepository == null) return;
        SolaceSendHistoryItem item = new SolaceSendHistoryItem();
        item.setId(UUID.randomUUID().toString());
        item.setNodeId(node.getId());
        item.setTimestamp(new Date());
        item.setStatus(error == null ? receipt.status().name() : "ERROR");
        item.setSummary(message.destinationName());
        item.setDestinationType(message.destinationType().name());
        item.setDestination(message.destinationName());
        item.setDeliveryMode(message.deliveryMode().name());
        item.setContentType(message.contentType());
        item.setCorrelationId(message.correlationId());
        item.setReplyTo(message.replyTo());
        item.setTtlMillis(message.timeToLiveMillis());
        item.setBody(message.body());
        item.setPropertiesJson(rawProperties);
        item.setResult(result);
        item.setCorrelationKey(receipt == null ? null : receipt.correlationKey());
        item.setDurationMillis(durationMillis);
        try {
            sendHistoryRepository.insert(item);
            if (sendHistoryBuilder != null) sendHistoryBuilder.refresh();
        } catch (IOException historyError) {
            showWeakMessage("消息已处理，但发送历史保存失败：" + historyError.getMessage());
        }
    }

    private void showSendHistoryDetail(SolaceSendHistoryItem item) {
        CustomTextArea detail = codeArea(item.getResult() + "\n\n"
            + "目标：" + nullToEmpty(item.getDestination()) + "\n"
            + "目标类型：" + nullToEmpty(item.getDestinationType()) + "\n"
            + "投递模式：" + nullToEmpty(item.getDeliveryMode()) + "\n"
            + "Correlation ID：" + nullToEmpty(item.getCorrelationId()) + "\n"
            + "Reply To：" + nullToEmpty(item.getReplyTo()) + "\n"
            + "TTL：" + item.getTtlMillis() + " ms\n\n"
            + "消息正文：\n" + nullToEmpty(item.getBody()) + "\n\n"
            + "自定义属性：\n" + nullToEmpty(item.getPropertiesJson()));
        detail.setEditable(false);
        editor.setCenter(section("发送历史详情",
            new Label("双击左侧历史记录可把该次请求恢复到原发送节点。"), editorPane(detail, 520)));
    }

    private Node historyEmptyState() {
        VBox box = new VBox(10, new Label("请选择一条发送历史"),
            new Label("单击查看发送与返回详情，双击可恢复到原发送节点。"));
        box.setPadding(new Insets(30));
        return box;
    }

    private void restoreSendHistory(SolaceSendHistoryItem item) {
        SolaceTreeNode node = findNode(item.getNodeId());
        if (node == null || node.getNodeType() != SolaceNodeType.SEND) {
            showError("原发送节点已不存在，无法恢复该历史记录。");
            return;
        }
        node.getSettings().put("destinationType", defaultIfBlank(item.getDestinationType(), "TOPIC"));
        node.getSettings().put("destination", nullToEmpty(item.getDestination()));
        node.getSettings().put("deliveryMode", defaultIfBlank(item.getDeliveryMode(), "PERSISTENT"));
        node.getSettings().put("contentType", nullToEmpty(item.getContentType()));
        node.getSettings().put("correlationId", nullToEmpty(item.getCorrelationId()));
        node.getSettings().put("replyTo", nullToEmpty(item.getReplyTo()));
        node.getSettings().put("ttl", Long.toString(item.getTtlMillis()));
        node.getSettings().put("body", nullToEmpty(item.getBody()));
        node.getSettings().put("properties", nullToEmpty(item.getPropertiesJson()));
        selected = node;
        if (saveAndRebuild(node.getId())) {
            showSelected();
            showWeakMessage("已恢复发送历史");
        }
    }

    private void showWeakMessage(String message) {
        Label toast = new Label(message);
        toast.getStyleClass().add("solace-toast");
        StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toast, new Insets(0, 20, 20, 0));
        root.getChildren().add(toast);
        PauseTransition delay = new PauseTransition(Duration.seconds(2.4));
        delay.setOnFinished(event -> root.getChildren().remove(toast));
        delay.play();
    }

    private void runConnectionTest(SolaceConnectionConfig config, Button testButton, Label status) {
        List<String> missing = SolaceRequestValidator.validateConnection(config);
        if (!missing.isEmpty()) {
            status.setText("参数不完整");
            showValidationErrors("无法测试连接", missing);
            return;
        }
        testButton.setDisable(true);
        status.setText("正在测试连接…");
        connectionTester.test(config).whenComplete((ignored, error) -> Platform.runLater(() -> {
            testButton.setDisable(false);
            if (error == null) {
                status.setText("连接成功");
                showWeakMessage("Solace 连接测试成功");
            } else {
                log.error("Solace connection test failed", error);
                String message = ThrowableDetails.summary(error);
                status.setText("连接失败 · " + message);
                showError("连接失败：\n" + ThrowableDetails.describe(error));
            }
        }));
    }

    private SolaceConnectionConfig readConnection(ConnectionForm form) {
        SolaceConnectionConfig config = new SolaceConnectionConfig();
        config.setHost(form.host().getText().trim()); config.setMessageVpn(form.vpn().getText().trim());
        config.setUsername(form.username().getText().trim()); config.setPassword(form.password().getText());
        config.setClientName(form.client().getText().trim()); config.setRememberPassword(form.remember().isSelected());
        config.setConnectTimeoutMillis(parseInt(form.timeout().getText(), "连接超时"));
        config.setReconnectRetries(parseInt(form.retries().getText(), "重连次数"));
        config.setReconnectRetryWaitMillis(parseInt(form.retryWait().getText(), "重连间隔"));
        config.setTlsEnabled(form.tls().isSelected()); config.setValidateCertificate(form.validate().isSelected());
        config.setTrustStorePath(form.trustStore().getText().trim());
        config.setTrustStorePassword(form.trustPassword().getText());
        config.setVariables(Map.of());
        return config;
    }

    private record ConnectionForm(TextField host, TextField vpn, TextField username, PasswordField password,
                                  TextField client, CheckBox remember, TextField timeout, TextField retries,
                                  TextField retryWait, CheckBox tls, CheckBox validate, TextField trustStore,
                                  PasswordField trustPassword) { }

    private void drainMessages() {
        List<SolaceReceivedMessage> batch = listener.drain(100);
        if (!batch.isEmpty()) {
            receivedHistory.addAll(batch);
            while (receivedHistory.size() > 2_000) receivedHistory.remove(0);
            refreshMessageFilter();
            // The listener receives on a background thread and the ListView is
            // refreshed on the FX pulse. Scroll after the items are installed
            // so the newest message is visible instead of leaving the viewport
            // at its previous position.
            if (!messages.getItems().isEmpty()) {
                Platform.runLater(() -> messages.scrollTo(messages.getItems().size() - 1));
            }
        }
    }

    private void refreshMessageFilter() {
        String filter = messageFilter.getText() == null ? "" : messageFilter.getText().trim().toLowerCase();
        messages.getItems().setAll(receivedHistory.stream().filter(message -> filter.isEmpty()
            || message.destination().toLowerCase().contains(filter)
            || nullToEmpty(message.messageId()).toLowerCase().contains(filter)
            || message.body().toLowerCase().contains(filter)
            || message.properties().toString().toLowerCase().contains(filter)).toList());
    }

    private void exportMessages() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出 Solace 接收消息");
        chooser.setInitialFileName("solace-messages.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File file = chooser.showSaveDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) return;
        List<SolaceReceivedMessage> snapshot = List.copyOf(receivedHistory);
        Thread.startVirtualThread(() -> {
            try {
                Files.writeString(file.toPath(), JSON.toJSONString(snapshot, JSONWriter.Feature.PrettyFormat), StandardCharsets.UTF_8);
                Platform.runLater(() -> info("已导出 " + snapshot.size() + " 条消息"));
            } catch (IOException error) {
                Platform.runLater(() -> showError("导出失败：" + error.getMessage()));
            }
        });
    }

    private boolean saveAndRebuild(Long selectId) {
        try {
            workspaceRefreshGate.suppressNext();
            context.save();
            rebuildTree();
            select(selectId);
            return true;
        } catch (IOException error) {
            workspaceRefreshGate.cancelSuppression();
            showError("保存失败：" + error.getMessage());
            return false;
        }
    }

    private boolean saveWithoutRebuild() {
        try {
            workspaceRefreshGate.suppressNext();
            context.save();
            return true;
        } catch (IOException error) {
            workspaceRefreshGate.cancelSuppression();
            showError("保存失败：" + error.getMessage());
            return false;
        }
    }

    private void select(Long id) {
        if (id == null || tree.getRoot() == null) return;
        TreeItem<SolaceTreeNode> found = find(tree.getRoot(), id);
        if (found != null) {
            TreeItem<SolaceTreeNode> parent = found.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
            tree.getSelectionModel().select(found);
            tree.getFocusModel().focus(tree.getRow(found));
            tree.scrollTo(tree.getRow(found));
        }
    }

    private TreeItem<SolaceTreeNode> find(TreeItem<SolaceTreeNode> item, Long id) {
        if (id.equals(item.getValue().getId())) return item;
        for (TreeItem<SolaceTreeNode> child : item.getChildren()) {
            TreeItem<SolaceTreeNode> found = find(child, id); if (found != null) return found;
        }
        return null;
    }

    private static GridPane form() { GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(10); return grid; }
    private static void row(GridPane grid, int row, String label, Node control) {
        grid.add(new Label(label), 0, row); grid.add(control, 1, row); GridPane.setHgrow(control, Priority.ALWAYS);
    }
    private static void pairRow(GridPane grid, int row, String firstLabel, Node firstControl,
                                String secondLabel, Node secondControl) {
        grid.add(new Label(firstLabel), 0, row);
        grid.add(firstControl, 1, row);
        grid.add(new Label(secondLabel), 2, row);
        grid.add(secondControl, 3, row);
        GridPane.setHgrow(firstControl, Priority.ALWAYS);
        GridPane.setHgrow(secondControl, Priority.ALWAYS);
    }
    private static VBox section(String title, Node... nodes) {
        Label heading = new Label(title); heading.getStyleClass().add("solace-heading");
        VBox box = new VBox(12); box.setPadding(new Insets(16)); box.getChildren().add(heading); box.getChildren().addAll(nodes);
        return box;
    }
    private static Button primary(String text) { Button button = new Button(text); button.getStyleClass().add("button-primary"); return button; }
    private static TextField text(String value) { TextField field = new TextField(nullToEmpty(value)); field.setMaxWidth(Double.MAX_VALUE); return field; }
    private static TextArea area(String value, double height) { TextArea area = new TextArea(nullToEmpty(value)); area.setPrefHeight(height); area.setWrapText(true); return area; }
    private static CustomTextArea codeArea(String value) {
        CustomTextArea area = new CustomTextArea();
        area.replaceText(nullToEmpty(value));
        area.setWrapText(true);
        return area;
    }
    private static VirtualizedScrollPane<CustomTextArea> editorPane(CustomTextArea editor, double height) {
        VirtualizedScrollPane<CustomTextArea> pane = new VirtualizedScrollPane<>(editor);
        pane.setPrefHeight(height);
        pane.setMinHeight(Math.min(100, height));
        return pane;
    }
    private static Tab fixedTab(String title, Node content) { return new Tab(title, content); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    private static String formatMap(Map<String, String> map) {
        StringBuilder value = new StringBuilder(); map.forEach((key, item) -> value.append(key).append('=').append(item).append('\n')); return value.toString();
    }
    private static Map<String, String> parseMap(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null) return result;
        for (String line : text.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) result.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return result;
    }
    private static long parseLong(String value) {
        try { return Long.parseLong(value == null || value.isBlank() ? "0" : value.trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("TTL 必须是整数毫秒"); }
    }
    private static int parseInt(String value, String field) {
        try { return Integer.parseInt(value == null || value.isBlank() ? "0" : value.trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(field + "必须是整数"); }
    }
    private static String rootCause(Throwable error) {
        return ThrowableDetails.summary(error);
    }
    private static void showError(String message) { alert(Alert.AlertType.ERROR, "错误", message); }
    private static void showValidationErrors(String title, List<String> missing) {
        StringBuilder message = new StringBuilder("请先填写以下必填项：\n");
        missing.forEach(field -> message.append("• ").append(field).append('\n'));
        alert(Alert.AlertType.WARNING, title, message.toString().stripTrailing());
    }
    private static void info(String message) { alert(Alert.AlertType.INFORMATION, "提示", message); }
    private static void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }

    @Override public void close() {
        listenerRefresh.stop();
        publisher.close();
        listener.close();
        try { workspaceListener.close(); } catch (Exception ignored) { }
    }
}
