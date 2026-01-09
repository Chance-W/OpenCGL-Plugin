package com.opencgl.http.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.base.hook.HookContext;
import com.opencgl.base.hook.RequestHook;
import com.opencgl.base.hook.ScriptEngineManager;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.CommitOnBlurTableCell;
import com.opencgl.base.utils.LoadingMask;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.utils.history.HistoryViewBuilder; // New Import
import com.opencgl.base.view.RequestManagerView; // New Import
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.http.model.HttpHistoryItem;
import com.opencgl.http.model.HttpRequestModel;
import com.opencgl.http.model.HttpResponseModel;
import com.opencgl.http.model.KeyValueEntry;
import com.opencgl.http.model.ProxyConfig;
import com.opencgl.http.service.CurlImporter;
import com.opencgl.http.service.EnvironmentService;
import com.opencgl.http.service.HttpClientService;
import com.opencgl.http.service.JsonFormatterService;
import com.opencgl.http.service.ProxyConfigService;
import com.opencgl.http.service.SwaggerImporter;
import com.opencgl.http.views.HttpDebuggerView;
import com.opencgl.http.model.HttpTreeItem;
import com.opencgl.http.service.HttpDebuggerHistoryService;
import com.opencgl.http.service.HttpTreeService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.stage.Window;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox; // Import explicitly
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;

import javafx.scene.Node;
import javafx.geometry.Insets;

import com.opencgl.http.service.CodeGeneratorService;
import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.http.i18n.I18N;
import javafx.stage.FileChooser;

/**
 * HTTP调试器控制器
 */
public class HttpDebuggerController extends HttpDebuggerView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(HttpDebuggerController.class);
    private final LoadingMask loadingMask = new LoadingMask();
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "http-debugger-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Future<?>> backgroundTasks = new CopyOnWriteArrayList<>();
    private final List<Path> temporaryPreviewFiles = new CopyOnWriteArrayList<>();
    private volatile boolean disposed;
    private javafx.event.EventHandler<javafx.scene.input.KeyEvent> shortcutHandler;

    private HttpClientService httpClientService;
    private JsonFormatterService jsonFormatterService;
    private EnvironmentService environmentService;
    private CodeGeneratorService codeGeneratorService;
    private HttpTreeService treeService;
    private ScriptEngineManager scriptEngineManager;
    private RequestHook currentHook;

    private ObservableList<KeyValueEntry> paramsData;
    private ObservableList<KeyValueEntry> headersData;
    private ObservableList<KeyValueEntry> bodyFormData;
    private ObservableList<KeyValueEntry> responseHeadersData;

    private HttpDebuggerHistoryService historyService; // Refactored Service
    private RequestManagerView requestManager; // New Manager

    /*
     * 环境配置弹框
     */
    private final HttpDebuggerEnvConfigureDialog httpEnvConfigureDialog = new HttpDebuggerEnvConfigureDialog();

    // UI State
    private HttpTreeItem currentTreeItem;
    private TreeView<HttpTreeItem> treeView;
    private final ObjectProperty<HttpResponseModel> currentResponseProperty = new SimpleObjectProperty<>();
    private HttpResponseModel currentResponse;
    // 保存当前响应以便格式化

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Services
        httpClientService = new HttpClientService();
        jsonFormatterService = new JsonFormatterService();
        environmentService = new EnvironmentService();
        codeGeneratorService = new CodeGeneratorService();
        treeService = new HttpTreeService();
        scriptEngineManager = new ScriptEngineManager();
        historyService = new HttpDebuggerHistoryService();

        // Data
        paramsData = FXCollections.observableArrayList();
        headersData = FXCollections.observableArrayList();
        bodyFormData = FXCollections.observableArrayList();
        responseHeadersData = FXCollections.observableArrayList();

        // Initialize RequestManagerView FIRST
        requestManager = new RequestManagerView();

        // Build and set TreeView and HistoryView (fills the tabs)
        setupTreeView();
        setupHistoryUI();

        // NOW inject the layout with ThemeSwitchUtil (after tabs are filled)
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentArea, requestManager.getTabPane());

        // Initialize I18n
        initI18n();

        // Continue with other UI setup
        setupUI();
        bindEvents();
        initHookScripts();
        setupKeyboardShortcuts();
    }

    private void initI18n() {
        // Toolbar
        proxyButton.textProperty().bind(I18N.getBinding("button.proxy"));
        importButton.textProperty().bind(I18N.getBinding("button.import"));

        // Request Line
        sendButton.textProperty().bind(I18N.getBinding("button.send"));
        saveButton.textProperty().bind(I18N.getBinding("button.save"));
        if (codeButton != null) {
            codeButton.textProperty().bind(I18N.getBinding("button.code"));
        }

        // Tabs
        requestTabPane.getTabs().get(0).textProperty().bind(I18N.getBinding("tab.params"));
        requestTabPane.getTabs().get(1).textProperty().bind(I18N.getBinding("tab.headers"));
        requestTabPane.getTabs().get(2).textProperty().bind(I18N.getBinding("tab.body"));
        requestTabPane.getTabs().get(3).textProperty().bind(I18N.getBinding("tab.auth"));
        if (hooksTab != null) hooksTab.textProperty().bind(I18N.getBinding("tab.hook"));
        requestTabPane.getTabs().get(5).textProperty().bind(I18N.getBinding("tab.settings"));

        responseTabPane.getTabs().get(0).textProperty().bind(I18N.getBinding("tab.response_body"));
        responseTabPane.getTabs().get(1).textProperty().bind(I18N.getBinding("tab.response_headers"));
        if (responseTabPane.getTabs().size() > 2) {
            responseTabPane.getTabs().get(2).textProperty().bind(I18N.getBinding("tab.response_preview"));
        }

        // Table Columns
        paramEnabledCol.textProperty().bind(I18N.getBinding("col.enabled"));
        paramKeyCol.textProperty().bind(I18N.getBinding("col.key"));
        paramValueCol.textProperty().bind(I18N.getBinding("col.value"));

        headerEnabledCol.textProperty().bind(I18N.getBinding("col.enabled"));
        headerKeyCol.textProperty().bind(I18N.getBinding("col.key"));
        headerValueCol.textProperty().bind(I18N.getBinding("col.value"));

        formKeyCol.textProperty().bind(I18N.getBinding("col.key"));
        formValueCol.textProperty().bind(I18N.getBinding("col.value"));

        respHeaderKeyCol.textProperty().bind(I18N.getBinding("col.key"));
        respHeaderValueCol.textProperty().bind(I18N.getBinding("col.value"));

        // Buttons
        addParamButton.textProperty().bind(I18N.getBinding("button.add_param"));
        addHeaderButton.textProperty().bind(I18N.getBinding("button.add_header"));
        addFormFieldButton.textProperty().bind(I18N.getBinding("button.add_form"));
        if (paramsBulkEditBtn != null)
            paramsBulkEditBtn.textProperty().bind(I18N.getBinding("button.bulk_edit"));
        if (formBulkEditBtn != null)
            formBulkEditBtn.textProperty().bind(I18N.getBinding("button.bulk_edit"));
        compactBodyButton.textProperty().bind(I18N.getBinding("button.compact"));
        if (previewInBrowserButton != null)
            previewInBrowserButton.textProperty().bind(I18N.getBinding("button.open_browser"));

        // Radios
        bodyNoneRadio.textProperty().bind(I18N.getBinding("radio.none"));
        bodyJsonRadio.textProperty().bind(I18N.getBinding("radio.json"));
        bodyFormRadio.textProperty().bind(I18N.getBinding("radio.form"));
        bodyRawRadio.textProperty().bind(I18N.getBinding("radio.raw"));
        bodyXmlRadio.textProperty().bind(I18N.getBinding("radio.xml"));

        // Settings
        configSslCheck.textProperty().bind(I18N.getBinding("label.ssl_verify"));
        configRedirectCheck.textProperty().bind(I18N.getBinding("label.follow_redirect"));

        // Status
        statusLabel.textProperty().bind(Bindings.createStringBinding(
            () -> I18N.get("label.status",
                currentResponseProperty.get() != null
                    ? String.valueOf(currentResponseProperty.get().getStatusCode())
                    : "-"),
            BaseI18N.localeProperty(), currentResponseProperty));
        timeLabel.textProperty().bind(Bindings.createStringBinding(
            () -> I18N.get("label.time",
                currentResponseProperty.get() != null
                    ? String.valueOf(currentResponseProperty.get().getResponseTime())
                    : "-"),
            BaseI18N.localeProperty(), currentResponseProperty));
        sizeLabel.textProperty().bind(Bindings.createStringBinding(
            () -> I18N.get("label.size",
                currentResponseProperty.get() != null && currentResponseProperty.get().getBody() != null
                    ? String.format("%.2f", currentResponseProperty.get().getBody().length() / 1024.0)
                    : "-"),
            BaseI18N.localeProperty(), currentResponseProperty));
    }

    private void setupUI() {
        // Method Combo
        methodComboBox.setItems(FXCollections.observableArrayList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"));
        methodComboBox.setValue("GET");

        // Environment Init
        setupEnvironmentUI();

        // Auth Types
        authTypeCombo.setItems(FXCollections.observableArrayList("No Auth", "Basic Auth", "Bearer Token"));
        authTypeCombo.setValue("No Auth");

        // Tables
        setupKeyValueTable(paramsTable, paramEnabledCol, paramKeyCol, paramValueCol, paramsData);
        setupKeyValueTable(headersTable, headerEnabledCol, headerKeyCol, headerValueCol, headersData);
        setupResponseHeadersTable();

        // Editors
        // bodyEditor.setSyntax("json"); // If supported by CodeArea wrapper
        responseBodyArea.setEditable(false);

        // 设置 Body 编辑器默认启用换行
        bodyEditor.setWrapText(true);

        // Initial UI State - ensure empty state is visible, request panel is hidden
        emptyState.setVisible(true);
        requestPanel.setVisible(false);

        // Setup Auth dynamic UI
        setupAuthUI();

        // Setup Form TableView
        setupBodyFormTable();

        // Setup body type switching
        setupBodyTypeSwitching();

        // Setup Response Preview
        // Setup Response Preview
        setupResponsePreview();

        // Setup mTLS pickers
        setupCertPickers();

        // Setup Response Preview
        setupResponsePreview();

        // Setup Code Generator
        setupCodeGeneratorUI();

        // Note: setupHistoryUI is now called in initialize() before ThemeSwitchUtil

        // Init Proxy Button Style
        if (proxyButton != null) {
            ProxyConfig currentProxy = httpClientService.getProxyConfigService().getConfig();
            if (currentProxy != null && currentProxy.isEnabled()) {
                proxyButton.setStyle("-fx-base: #ffeb3b");
            }
            proxyButton.setOnAction(e -> showProxyDialog());
        }

        if (importButton != null) {
            importButton.setOnAction(e -> showImportDialog());
        }

        Platform.runLater(() -> {
            if (bodyRawContainer != null && requestFabBox != null) {
                setupFabLayout(bodyRawContainer, requestFabBox);
            }
            if (responseBodyArea != null && responseFabBox != null) {
                if (responseFabBox.getParent() instanceof StackPane) {
                    setupFabLayout((StackPane) responseFabBox.getParent(), responseFabBox);
                }
            }
        });
    }

    private void setupFabLayout(StackPane container, HBox fabBox) {
        container.getChildren().stream()
            .filter(node -> node instanceof org.fxmisc.flowless.VirtualizedScrollPane)
            .findFirst()
            .ifPresent(scrollPane -> {
                Region region = (Region) scrollPane;

                Runnable attachListeners = () -> {
                    Set<Node> bars = region.lookupAll(".scroll-bar");
                    for (Node node : bars) {
                        if (node instanceof ScrollBar bar) {
                            if (bar.getOrientation() == Orientation.VERTICAL) {

                                if (bar.getProperties().containsKey("fabListenerAttached"))
                                    return;

                                bar.visibleProperty().addListener((obs, old, visible) -> {
                                    updateFabPadding(fabBox, visible);
                                });

                                updateFabPadding(fabBox, bar.isVisible());

                                bar.getProperties().put("fabListenerAttached", true);
                            }
                        }
                    }
                };

                attachListeners.run();

                region.getChildrenUnmodifiable()
                    .addListener((javafx.collections.ListChangeListener<Node>) change -> {
                        attachListeners.run();
                    });
            });
    }

    private void updateFabPadding(HBox fabBox, boolean scrollBarVisible) {
        double right = scrollBarVisible ? 25.0 : 5.0;
        Platform.runLater(() -> fabBox.setPadding(new Insets(5, right, 0, 0)));
    }

    private void setupTreeView() {
        if (requestManager == null) {
            logger.error("requestManager is NULL for TreeView setup.");
            return;
        }

        try {
            // Use TreeViewBuilder
            VBox treeViewContainer = new TreeViewBuilder<HttpTreeItem>()
                .service(treeService)
                .dataType(HttpTreeItem.class)
                .searchPrompt("Search Requests...")
                .enableSearch()
                .enableDragDrop(true)
                .enableToolbar(true) // Enable Toolbar
                .locateTargetSupplier(() -> this.currentTreeItem) // Enable Locate
                .onSelect(this::onTreeNodeSelected)
                .onTreeCreated(tree -> {
                    this.treeView = tree;
                })
                .build();

            requestManager.setCollectionView(treeViewContainer);
            logger.info("TreeView setup complete. Component: {}", treeViewContainer);
        }
        catch (Exception e) {
            logger.error("Failed to setup TreeView", e);
        }
    }

    // private void setupContextMenu() {
    // ContextMenu contextMenu = new ContextMenu();
    //
    // MenuItem newRequestItem = new MenuItem("新建请求");
    // newRequestItem.setOnAction(e -> createNewItem(HttpTreeItem.TYPE_REQUEST));
    //
    // MenuItem newFolderItem = new MenuItem("新建文件夹");
    // newFolderItem.setOnAction(e -> createNewItem(HttpTreeItem.TYPE_FOLDER));
    //
    // MenuItem copyItem = new MenuItem("复制");
    // copyItem.setOnAction(e -> copyCurrentNode());
    //
    // MenuItem deleteItem = new MenuItem("删除");
    // deleteItem.setOnAction(e -> deleteSelectedItem());
    //
    // contextMenu.getItems().addAll(newRequestItem, newFolderItem, copyItem, new
    // SeparatorMenuItem(), deleteItem);
    //
    // // Attach to TreeView (requires capturing instance)
    // if (treeView != null) {
    // treeView.setContextMenu(contextMenu);
    //
    // // Dynamic enable/disable based on selection
    // treeView.setOnContextMenuRequested(e -> {
    // TreeItem<HttpTreeItem> selected =
    // treeView.getSelectionModel().getSelectedItem();
    // boolean isRoot = selected == null || selected.getParent() == null;
    // deleteItem.setDisable(isRoot);
    // copyItem.setDisable(isRoot);
    // });
    // }
    // }

    private void createNewItem(String type) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(HttpTreeItem.TYPE_REQUEST.equals(type) ? I18N.get("msg.new_request") : I18N.get("msg.new_folder"));
        dialog.setHeaderText(null);
        dialog.setContentText(I18N.get("msg.enter_name"));

        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty())
                return;

            TreeItem<HttpTreeItem> selected = treeView != null ? treeView.getSelectionModel().getSelectedItem() : null;
            Long parentId = 0L; // Default to root level

            if (selected != null && selected.getValue() != null) {
                // If selected is a leaf (Request), add to its parent (as sibling)
                if (selected.getValue().getIsLeaf()) {
                    parentId = selected.getValue().getParentId() != null ? selected.getValue().getParentId() : 0L;
                }
                else {
                    // If selected is a folder, add as child
                    parentId = selected.getValue().getId();
                }
            }

            HttpTreeItem newItem;
            if (HttpTreeItem.TYPE_REQUEST.equals(type)) {
                newItem = treeService.createRequest(name, "GET", "", parentId);
            }
            else {
                newItem = treeService.createFolder(name, parentId);
            }

            logger.info("Created new {} '{}' with parentId: {}", type, name, parentId);

            // HACK: Rebuild tree to show new item
            setupTreeView();
        });
    }

    private void deleteSelectedItem() {
        TreeItem<HttpTreeItem> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getParent() == null)
            return; // Cannot delete root

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18N.get("msg.confirm_delete"));
        alert.setHeaderText(null);
        alert.setContentText(I18N.get("msg.confirm_delete_content", selected.getValue().getName()));

        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                treeService.delete(selected.getValue());
                selected.getParent().getChildren().remove(selected);
                TooltipUtil.showToast(contentArea, I18N.get("msg.delete_success"));

                if (currentTreeItem != null && currentTreeItem.getId().equals(selected.getValue().getId())) {
                    emptyState.setVisible(true);
                    requestPanel.setVisible(false);
                    currentTreeItem = null;
                }
            }
        });
    }

    private void onTreeNodeSelected(HttpTreeItem item) {
        if (item == null || item.isFolder())
            return;

        currentTreeItem = item;

        if (HttpTreeItem.TYPE_REQUEST.equals(item.getNodeType())) {
            emptyState.setVisible(false);
            requestPanel.setVisible(true);
            loadRequest(item);
        }
        // else {
        // emptyState.setVisible(true);
        // requestPanel.setVisible(false);
        // }
    }

    private void loadRequest(HttpTreeItem item) {
        methodComboBox.setValue(item.getMethod() != null ? item.getMethod() : "GET");
        urlField.setText(item.getUrl());

        // Load Config (Timeout in ms -> s)
        if (item.getTimeout() != null) {
            configTimeoutField.setText(String.valueOf(item.getTimeout() / 1000));
        }
        else {
            configTimeoutField.setText("30");
        }
        configSslCheck.setSelected(item.getSslVerification() != null ? item.getSslVerification() : true);
        configRedirectCheck.setSelected(item.getFollowRedirects() != null ? item.getFollowRedirects() : true);

        // Load mTLS Config
        configCertPathField.setText(item.getClientCertPath() != null ? item.getClientCertPath() : "");
        configCertPassField.setText(item.getClientCertPass() != null ? item.getClientCertPass() : "");
        configCaPathField.setText(item.getServerCertPath() != null ? item.getServerCertPath() : "");
        configCaPassField.setText(item.getServerCertPass() != null ? item.getServerCertPass() : "");

        // Load Params, Headers, Body, Auth, Hook
        // ... (Parsing JSON to ObservableList)

        if (item.getBodyType() != null) {
            switch (item.getBodyType()) {
                case "JSON":
                    bodyJsonRadio.setSelected(true);
                    requestTabPane.getSelectionModel().select(2); // Select Body Tab
                    break;
                case "FORM":
                    bodyFormRadio.setSelected(true);
                    requestTabPane.getSelectionModel().select(2); // Select Body Tab
                    break;
                case "RAW":
                    bodyRawRadio.setSelected(true);
                    requestTabPane.getSelectionModel().select(2); // Select Body Tab
                    break;
                case "XML":
                    bodyXmlRadio.setSelected(true);
                    requestTabPane.getSelectionModel().select(2); // Select Body Tab
                    break;
                default:
                    bodyNoneRadio.setSelected(true);
                    break;
            }
        }
        else {
            bodyNoneRadio.setSelected(true);
        }

        // Load Params
        paramsData.clear();
        if (item.getParams() != null && !item.getParams().isEmpty()) {
            try {
                paramsData.addAll(parseKeyValueList(item.getParams()));
            }
            catch (Exception e) {
                logger.error("Failed to parse params JSON", e);
            }
        }

        // Load Headers
        headersData.clear();
        if (item.getHeaders() != null && !item.getHeaders().isEmpty()) {
            try {
                headersData.addAll(parseKeyValueList(item.getHeaders()));
            }
            catch (Exception e) {
                logger.error("Failed to parse headers JSON", e);
            }
        }

        // Load Body or Form Data
        bodyFormData.clear();
        if ("FORM".equals(item.getBodyType())) {
            if (item.getBody() != null && !item.getBody().isEmpty()) {
                try {
                    bodyFormData.addAll(parseKeyValueList(item.getBody()));
                }
                catch (Exception e) {
                    logger.error("Failed to parse form body JSON", e);
                }
            }
            bodyEditor.replaceText("");
        }
        else {
            bodyEditor.replaceText(item.getBody() != null ? item.getBody() : "");
        }

        // Hook（路径，选择后自动加载内容）
        if (hookScriptPathField != null) {
            hookScriptPathField.setText(item.getHookScript() != null ? item.getHookScript() : "");
        }

        // Load Auth Config
        if (item.getAuthConfig() != null) {
            try {
                JSONObject auth = JSON.parseObject(item.getAuthConfig());
                String type = auth.getString("type");
                authTypeCombo.setValue(type);

                // Fields are created async by listener, but we are on JavaFX thread so it's
                // sync?
                // The listener creates nodes immediately.
                Platform.runLater(() -> {
                    if ("Basic Auth".equals(type)) {
                        TextField u = findAuthField("authUsername");
                        if (u != null)
                            u.setText(auth.getString("username"));
                        TextField p = findAuthField("authPassword");
                        if (p != null)
                            p.setText(auth.getString("password"));
                    }
                    else if ("Bearer Token".equals(type)) {
                        TextField t = findAuthField("authToken");
                        if (t != null)
                            t.setText(auth.getString("token"));
                    }
                });
            }
            catch (Exception e) {
                logger.error("Failed to parse auth config", e);
            }
        }
        else {
            authTypeCombo.setValue("No Auth");
        }
    }

    private void saveRequest() {
        if (currentTreeItem == null)
            return;

        currentTreeItem.setMethod(methodComboBox.getValue());
        currentTreeItem.setUrl(urlField.getText());
        currentTreeItem.setBodyType(getSelectedBodyType());

        // Save Params & Headers
        currentTreeItem.setParams(toJSON(paramsData));
        currentTreeItem.setHeaders(toJSON(headersData));

        // Save Body or Form Data
        if ("FORM".equals(currentTreeItem.getBodyType())) {
            currentTreeItem.setBody(toJSON(bodyFormData));
        }
        else {
            currentTreeItem.setBody(bodyEditor.getText());
        }

        // Save Config (Timeout s -> ms)
        try {
            int timeoutSec = Integer.parseInt(configTimeoutField.getText().trim());
            currentTreeItem.setTimeout(timeoutSec * 1000);
        }
        catch (Exception e) {
            currentTreeItem.setTimeout(30000); // Default 30s
        }
        currentTreeItem.setSslVerification(configSslCheck.isSelected());
        currentTreeItem.setFollowRedirects(configRedirectCheck.isSelected());

        // Save mTLS Config
        currentTreeItem.setClientCertPath(configCertPathField.getText());
        currentTreeItem.setClientCertPass(configCertPassField.getText());
        currentTreeItem.setServerCertPath(configCaPathField.getText());
        currentTreeItem.setServerCertPass(configCaPassField.getText());

        String hookPath = hookScriptPathField != null ? hookScriptPathField.getText() : null;
        currentTreeItem.setHookScript((hookPath != null && !hookPath.trim().isEmpty()) ? hookPath.trim() : null);

        // Save Auth Config
        String authType = authTypeCombo.getValue();
        JSONObject authConfig = new JSONObject();
        authConfig.put("type", authType);

        if ("Basic Auth".equals(authType)) {
            TextField u = findAuthField("authUsername");
            TextField p = findAuthField("authPassword");
            authConfig.put("username", u != null ? u.getText() : "");
            authConfig.put("password", p != null ? p.getText() : "");
        }
        else if ("Bearer Token".equals(authType)) {
            TextField t = findAuthField("authToken");
            authConfig.put("token", t != null ? t.getText() : "");
        }
        currentTreeItem.setAuthConfig(authConfig.toJSONString());

        // Update Tree Item in DB
        treeService.update(currentTreeItem);
        TooltipUtil.showToast(contentArea, I18N.get("msg.save_success"));
        logger.info("Saved request: {}", currentTreeItem.getName());
    }

    private TextField findAuthField(String key) {
        if (authContentPane == null)
            return null;
        for (Node node : authContentPane.getChildren()) {
            // Fields are wrapped in HBox
            if (node instanceof HBox box) {
                for (Node child : box.getChildren()) {
                    if (child instanceof TextField && key.equals(child.getUserData())) {
                        return (TextField) child;
                    }
                }
            }
        }
        return null;
    }

    private List<KeyValueEntry> parseKeyValueList(String json) {
        List<KeyValueEntry> result = new ArrayList<>();
        JSONArray array = JSON.parseArray(json);
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            result.add(new KeyValueEntry(
                obj.getString("key"),
                obj.getString("value"),
                obj.getBooleanValue("enabled")));
        }
        return result;
    }

    private String toJSON(ObservableList<KeyValueEntry> data) {
        JSONArray array = new JSONArray();
        for (KeyValueEntry entry : data) {
            // Skip read-only entries (e.g. auto-generated Auth headers)
            if (entry.isReadOnly()) {
                continue;
            }
            JSONObject obj = new JSONObject();
            obj.put("key", entry.getKey());
            obj.put("value", entry.getValue());
            obj.put("enabled", entry.isEnabled());
            array.add(obj);
        }
        return array.toJSONString();
    }

    private String getSelectedBodyType() {
        if (bodyJsonRadio.isSelected())
            return "JSON";
        if (bodyFormRadio.isSelected())
            return "FORM";
        if (bodyRawRadio.isSelected())
            return "RAW";
        if (bodyXmlRadio.isSelected())
            return "XML";
        return "NONE";
    }

    private void setupKeyValueTable(TableView<KeyValueEntry> table,
                                    TableColumn<KeyValueEntry, Boolean> enabledCol,
                                    TableColumn<KeyValueEntry, String> keyCol,
                                    TableColumn<KeyValueEntry, String> valueCol,
                                    ObservableList<KeyValueEntry> data) {
        table.setEditable(true);
        table.setItems(data);

        enabledCol.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            {
                checkBox.setAlignment(javafx.geometry.Pos.CENTER);
                setAlignment(javafx.geometry.Pos.CENTER);
                checkBox.selectedProperty().addListener((obs, oldV, newV) -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        // Only update model if logic allows (e.g. not binding loop)
                        // But here we want UI to drive Model
                        getTableRow().getItem().setEnabled(newV);
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                }
                else {
                    KeyValueEntry entry = getTableRow().getItem();
                    // Avoid triggering listener during update
                    checkBox.setSelected(entry.isEnabled());
                    checkBox.setDisable(entry.isReadOnly());
                    setGraphic(checkBox);
                }
            }
        });
        enabledCol.setCellValueFactory(cellData -> cellData.getValue().enabledProperty());

        keyCol.setCellFactory(CommitOnBlurTableCell.forStringColumn(entry -> !entry.isReadOnly()));
        keyCol.setCellValueFactory(cellData -> cellData.getValue().keyProperty());
        keyCol.setEditable(true);
        keyCol.setOnEditCommit(event -> {
            if (!event.getRowValue().isReadOnly()) {
                event.getRowValue().setKey(event.getNewValue());
            }
        });

        valueCol.setCellFactory(CommitOnBlurTableCell.forStringColumn(entry -> !entry.isReadOnly()));
        valueCol.setCellValueFactory(cellData -> cellData.getValue().valueProperty());
        valueCol.setEditable(true);
        valueCol.setOnEditCommit(event -> {
            if (!event.getRowValue().isReadOnly()) {
                event.getRowValue().setValue(event.getNewValue());
            }
        });

        // Add Delete Column
        TableColumn<KeyValueEntry, Void> deleteCol = new TableColumn<>("");
        deleteCol.setPrefWidth(30);
        deleteCol.setSortable(false);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("×");

            {
                deleteBtn.setStyle("-fx-font-weight: bold; -fx-padding: 0;");
                deleteBtn.setOnAction(e -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        KeyValueEntry item = getTableRow().getItem();
                        if (!item.isReadOnly()) {
                            getTableView().getItems().remove(item);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                }
                else {
                    KeyValueEntry entry = getTableRow().getItem();
                    deleteBtn.setDisable(entry.isReadOnly());
                    setGraphic(deleteBtn);
                }
            }
        });
        table.getColumns().add(deleteCol);
    }

    private void setupResponseHeadersTable() {
        responseHeadersTable.setItems(responseHeadersData);
        respHeaderKeyCol.setCellValueFactory(cellData -> cellData.getValue().keyProperty());
        respHeaderValueCol.setCellValueFactory(cellData -> cellData.getValue().valueProperty());
    }

    /**
     * 设置 Auth UI 动态显示
     */
    private void setupAuthUI() {
        authTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            authContentPane.getChildren().clear();
            updateAuthHeader(); // Remove old auth header

            if (newVal == null || "No Auth".equals(newVal)) {
                return;
            }

            Label infoLabel = new Label();
            infoLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");

            if ("Basic Auth".equals(newVal)) {
                infoLabel.setText(I18N.get("auth.basic_tip"));

                HBox usernameBox = new HBox(10);
                usernameBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Label usernameLabel = new Label(I18N.get("auth.username"));
                usernameLabel.setMinWidth(80);
                TextField usernameField = new TextField();
                usernameField.setPromptText(I18N.get("auth.username_prompt"));
                usernameField.setUserData("authUsername"); // Tag for retrieval
                usernameField.textProperty().addListener(o -> updateAuthHeader()); // Listener
                HBox.setHgrow(usernameField, Priority.ALWAYS);
                usernameBox.getChildren().addAll(usernameLabel, usernameField);

                HBox passwordBox = new HBox(10);
                passwordBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Label passwordLabel = new Label(I18N.get("auth.password"));
                passwordLabel.setMinWidth(80);
                PasswordField passwordField = new PasswordField();
                passwordField.setPromptText(I18N.get("auth.password_prompt"));
                passwordField.setUserData("authPassword");
                passwordField.textProperty().addListener(o -> updateAuthHeader());
                HBox.setHgrow(passwordField, Priority.ALWAYS);
                passwordBox.getChildren().addAll(passwordLabel, passwordField);

                authContentPane.getChildren().addAll(infoLabel, usernameBox, passwordBox);

            }
            else if ("Bearer Token".equals(newVal)) {
                infoLabel.setText(I18N.get("auth.bearer_tip"));

                HBox tokenBox = new HBox(10);
                tokenBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Label tokenLabel = new Label(I18N.get("auth.token"));
                tokenLabel.setMinWidth(80);
                TextField tokenField = new TextField();
                tokenField.setPromptText(I18N.get("auth.token_prompt"));
                tokenField.setUserData("authToken");
                tokenField.textProperty().addListener(o -> updateAuthHeader()); // Listener
                HBox.setHgrow(tokenField, Priority.ALWAYS);
                tokenBox.getChildren().addAll(tokenLabel, tokenField);

                authContentPane.getChildren().addAll(infoLabel, tokenBox);
            }
        });
    }

    private void updateAuthHeader() {
        String authType = authTypeCombo.getValue();
        String authValue = null;

        if ("Basic Auth".equals(authType)) {
            TextField u = findAuthField("authUsername");
            TextField p = findAuthField("authPassword");
            if (u != null && p != null) {
                String user = u.getText() == null ? "" : u.getText();
                String pass = p.getText() == null ? "" : p.getText();

                String auth = user + ":" + pass;
                String encoded = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                authValue = "Basic " + encoded;
            }
        }
        else if ("Bearer Token".equals(authType)) {
            TextField t = findAuthField("authToken");
            if (t != null) {
                String token = t.getText() == null ? "" : t.getText();
                authValue = "Bearer " + token;
            }
        }

        // Find existing read-only Auth header
        KeyValueEntry authEntry = null;
        for (KeyValueEntry entry : headersData) {
            if ("Authorization".equalsIgnoreCase(entry.getKey()) && entry.isReadOnly()) {
                authEntry = entry;
                break;
            }
        }

        if (authValue != null) {
            // Update or Create
            if (authEntry != null) {
                authEntry.setValue(authValue);
                if (!authEntry.isEnabled())
                    authEntry.setEnabled(true); // Force enable? User said "checked by default"
            }
            else {
                // Insert at top
                authEntry = new KeyValueEntry("Authorization", authValue, true, true);
                headersData.addFirst(authEntry);
            }
        }
        else {
            // Remove if exists
            if (authEntry != null) {
                headersData.remove(authEntry);
            }
        }
    }

    /**
     * 设置 Form TableView
     */
    private void setupBodyFormTable() {
        bodyFormTable.setItems(bodyFormData);
        bodyFormTable.setEditable(true);

        formKeyCol.setCellFactory(CommitOnBlurTableCell.forStringColumn(entry -> !entry.isReadOnly()));
        formKeyCol.setCellValueFactory(cellData -> cellData.getValue().keyProperty());
        formKeyCol.setEditable(true);
        formKeyCol.setOnEditCommit(event -> {
            event.getRowValue().setKey(event.getNewValue());
        });

        formValueCol.setCellValueFactory(cellData -> cellData.getValue().valueProperty());
        formValueCol.setCellFactory(CommitOnBlurTableCell.forStringColumn(entry -> !entry.isReadOnly()));
        formValueCol.setEditable(true);
        formValueCol.setOnEditCommit(event -> {
            if (!event.getRowValue().isReadOnly()) {
                event.getRowValue().setValue(event.getNewValue());
            }
        });

        // Add Delete Column
        TableColumn<KeyValueEntry, Void> deleteCol = new TableColumn<>("");
        deleteCol.setPrefWidth(30);
        deleteCol.setSortable(false);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("×");

            {
                deleteBtn.setStyle("-fx-background-color: transparent;-fx-font-weight: bold; -fx-padding: 0;");
                deleteBtn.setOnAction(e -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        KeyValueEntry item = getTableRow().getItem();
                        if (!item.isReadOnly()) {
                            getTableView().getItems().remove(item);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                }
                else {
                    // Form data usually perfectly editable, but check readOnly anyway
                    KeyValueEntry entry = getTableRow().getItem();
                    // deleteBtn.setDisable(entry.isReadOnly());
                    // Assuming form data can always be deleted for now unless readOnly logic
                    // expands
                    setGraphic(deleteBtn);
                }
            }
        });
        bodyFormTable.getColumns().add(deleteCol);
    }

    /**
     * 设置 Body 类型切换逻辑
     */
    private void setupBodyTypeSwitching() {
        bodyTypeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null)
                return;

            RadioButton selected = (RadioButton) newToggle;
            boolean isFormType = selected == bodyFormRadio;
            boolean isJsonOrXml = selected == bodyJsonRadio || selected == bodyXmlRadio;
            boolean isNone = selected == bodyNoneRadio;

            // Toggle visibility
            // If None, disable editor or show empty?
            // Postman: None -> "This request does not have a body"
            // Here we just disable the editor area.

            if (isFormType) {
                // Show Form container, Hide Raw Editor
                bodyRawContainer.setVisible(false);
                bodyRawContainer.setManaged(false);
                bodyFormContainer.setVisible(true);
                bodyFormContainer.setManaged(true);
            }
            else {
                // Show Raw Editor, Hide Form container
                bodyRawContainer.setVisible(true);
                bodyRawContainer.setManaged(true);
                bodyFormContainer.setVisible(false);
                bodyFormContainer.setManaged(false);

                // Disable editor if None
                bodyEditor.setDisable(isNone);
                if (isNone) {
                    bodyEditor.replaceText("");
                }
            }

            // Toolbar visibility
            if (bodyFormatToolbar != null) {
                bodyFormatToolbar.setVisible(isJsonOrXml);
            }
            else {
                compactBodyButton.setVisible(isJsonOrXml);
            }
        });
    }

    private void toggleBulkEdit(Button toggleBtn, Button addButton, TableView<?> table, CustomTextArea editor,
                                ObservableList<KeyValueEntry> data) {
        boolean isBulkMode = editor.isVisible();

        if (isBulkMode) {
            // Switch back to Table (parse text)
            String text = editor.getText();
            List<KeyValueEntry> parsed = parseBulkText(text);
            data.setAll(parsed);

            editor.setVisible(false);
            editor.setManaged(false);
            table.setVisible(true);
            table.setManaged(true);

            if (addButton != null) {
                addButton.setVisible(true);
                addButton.setManaged(true);
            }

            toggleBtn.setText(I18N.get("button.bulk_edit_mode"));
        }
        else {
            // Switch to Bulk (format table data)
            String text = formatBulkText(data);
            editor.replaceText(text);

            table.setVisible(false);
            table.setManaged(false);
            editor.setVisible(true);
            editor.setManaged(true);

            if (addButton != null) {
                addButton.setVisible(false);
                addButton.setManaged(false);
            }

            toggleBtn.setText(I18N.get("button.keyvalue_edit_mode"));
        }
    }

    private String formatBulkText(List<KeyValueEntry> data) {
        StringBuilder sb = new StringBuilder();
        for (KeyValueEntry entry : data) {
            if (!entry.isEnabled()) {
                sb.append("//");
            }
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    private List<KeyValueEntry> parseBulkText(String text) {
        List<KeyValueEntry> list = new ArrayList<>();
        if (text == null)
            return list;

        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            boolean enabled = true;
            if (line.startsWith("//")) {
                enabled = false;
                line = line.substring(2).trim();
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex > -1) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                list.add(new KeyValueEntry(key, value, enabled));
            }
            else {
                // No colon, treat as key without value? or ignore?
                // Postman treats "foo" as key="foo", value=""
                list.add(new KeyValueEntry(line, "", enabled));
            }
        }
        return list;
    }

    private void bindEvents() {
        sendButton.setOnAction(e -> sendRequest());
        saveButton.setOnAction(e -> saveRequest());

        addParamButton.setOnAction(e -> paramsData.add(new KeyValueEntry()));
        addHeaderButton.setOnAction(e -> headersData.add(new KeyValueEntry()));
        addFormFieldButton.setOnAction(e -> bodyFormData.add(new KeyValueEntry()));

        // Bulk Edit Toggles
        if (paramsBulkEditBtn != null) {
            paramsBulkEditBtn.setOnAction(
                e -> toggleBulkEdit(paramsBulkEditBtn, addParamButton, paramsTable, paramsBulkEditor, paramsData));
            // Placeholder text for bulk editor
            paramsBulkEditor.setPromptText(
                "Rows are separated by new lines\nKeys and values are separated by :\nPrepend // to any row you want to add but keep disabled");
        }
        if (formBulkEditBtn != null) {
            formBulkEditBtn.setOnAction(e -> toggleBulkEdit(formBulkEditBtn, addFormFieldButton, bodyFormTable,
                formBulkEditor, bodyFormData));
            formBulkEditor.setPromptText(
                "Rows are separated by new lines\nKeys and values are separated by :\nPrepend // to any row you want to add but keep disabled");
        }

        // Hook：路径监听、浏览、保存、测试（与 Dubbo 模块一致）
        if (hookScriptPathField != null) {
            hookScriptPathField.textProperty().addListener((obs, oldVal, newVal) -> onHookScriptPathChanged(newVal));
        }
        if (browseHookButton != null) {
            browseHookButton.setOnAction(e -> browseHookAction());
        }
        if (saveHookButton != null) {
            saveHookButton.setOnAction(e -> saveHookAction());
        }
        if (testHookButton != null) {
            testHookButton.setOnAction(e -> testHookAction());
        }

        if (formatJsonButton != null) {
            formatJsonButton.setVisible(false);
            formatJsonButton.setManaged(false);
        }

        compactBodyButton.setOnAction(e -> compactBody());

        // Bind CustomTextArea internal format buttons
        if (bodyEditor != null) {
            bodyEditor.setExternalFormatAction(this::formatBody);
        }
        if (responseBodyArea != null) {
            responseBodyArea.setExternalFormatAction(this::formatResponse);
        }

    }

    // --- History UI Methods ---

    private void setupHistoryUI() {
        if (requestManager == null)
            return;

        VBox historyView = new HistoryViewBuilder<HttpHistoryItem>()
            .service(historyService)
            .cellDisplay(config -> config
                .primaryText(item -> item.getMethod() + " " + item.getUrl())
                .secondaryText(HttpHistoryItem::getId) // Display ID or something else? Maybe Request Time is
                // better but cellDisplay has timestampField
                .badgeText(item -> item.getStatusCode() != null ? String.valueOf(item.getStatusCode()) : "")
                .timestampField(HttpHistoryItem::getRequestTime))
            .restoreAction(this::restoreHistoryItem)
            .build();

        requestManager.setHistoryView(historyView);
    }

    private void restoreHistoryItem(HttpHistoryItem item) {
        if (item == null)
            return;

        // Deserialize snapshots
        try {
            HttpRequestModel req = JSON.parseObject(item.getRequestSnapshot(), HttpRequestModel.class);

            // Restore UI fields
            methodComboBox.setValue(req.getMethod());
            urlField.setText(req.getUrl());

            // Params
            paramsData.clear();
            if (req.getParams() != null) {
                req.getParams().forEach((k, v) -> paramsData.add(new KeyValueEntry(k, v, true)));
            }

            // Headers
            headersData.clear();
            if (req.getHeaders() != null) {
                req.getHeaders().forEach((k, v) -> headersData.add(new KeyValueEntry(k, v, true)));
            }

            // Body
            // Assume RequestModel body is final string
            // Need to guess body type from Content-Type header if RequestModel dedicated
            // field missing
            // But HistoryItem doesn't store bodyType? Or RequestModel does?
            // HttpRequestModel usually has setBodyType. If not, raw.
            if (req.getBodyType() != null) {
                switch (req.getBodyType()) {
                    case "JSON":
                        bodyJsonRadio.setSelected(true);
                        break;
                    case "FORM":
                        bodyFormRadio.setSelected(true);
                        break; // Form restoration logic might need parsing if body is stored as query string
                    default:
                        bodyRawRadio.setSelected(true);
                        break;
                }
            }

            bodyEditor.replaceText(req.getBody() != null ? req.getBody() : "");

            // If Form, we should parse the body string back to KeyValueEntry list?
            if ("FORM".equals(req.getBodyType()) && req.getBody() != null) {
                bodyFormData.clear();
                // Simple parser split by & and =
                String[] pairs = req.getBody().split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        String k = java.net.URLDecoder.decode(pair.substring(0, idx),
                            java.nio.charset.StandardCharsets.UTF_8);
                        String v = java.net.URLDecoder.decode(pair.substring(idx + 1),
                            java.nio.charset.StandardCharsets.UTF_8);
                        bodyFormData.add(new KeyValueEntry(k, v, true));
                    }
                }
            }

            // Switch to Request View
            requestPanel.setVisible(true);
            emptyState.setVisible(false);
            // Select this history item implies we are not on a tree item?
            // currentTreeItem = null; // Maybe keep it null to indicate detached mode

            // Restore Response
            if (item.getResponseSnapshot() != null) {
                HttpResponseModel resp = JSON.parseObject(item.getResponseSnapshot(), HttpResponseModel.class);
                if (resp != null) {
                    displayResponse(resp);
                }
                else {
                    responseBodyArea.clear();
                    currentResponseProperty.set(null);
                    responseHeadersData.clear();
                }
            }
            else {
                responseBodyArea.clear();
                currentResponseProperty.set(null);
                responseHeadersData.clear();
            }

        }
        catch (Exception e) {
            logger.error("Failed to restore history", e);
            showError("Failed to restore history item");
        }
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        // Ctrl+Enter 发送请求
        javafx.scene.input.KeyCodeCombination sendShortcut = new javafx.scene.input.KeyCodeCombination(
            javafx.scene.input.KeyCode.ENTER, javafx.scene.input.KeyCombination.SHORTCUT_DOWN);

        // Ctrl+S 保存
        javafx.scene.input.KeyCodeCombination saveShortcut = new javafx.scene.input.KeyCodeCombination(
            javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.SHORTCUT_DOWN);

        // Ctrl+R 刷新
        javafx.scene.input.KeyCodeCombination refreshShortcut = new javafx.scene.input.KeyCodeCombination(
            javafx.scene.input.KeyCode.R, javafx.scene.input.KeyCombination.SHORTCUT_DOWN);

        // F5 刷新
        javafx.scene.input.KeyCodeCombination f5Shortcut = new javafx.scene.input.KeyCodeCombination(
            javafx.scene.input.KeyCode.F5);

        // Ctrl+D 复制当前节点
        javafx.scene.input.KeyCodeCombination copyShortcut = new javafx.scene.input.KeyCodeCombination(
            javafx.scene.input.KeyCode.D, javafx.scene.input.KeyCombination.SHORTCUT_DOWN);

        // Ctrl+Alt+L 格式化 Body
        javafx.scene.input.KeyCodeCombination formatShortcut = new javafx.scene.input.KeyCodeCombination(
            javafx.scene.input.KeyCode.L, javafx.scene.input.KeyCombination.SHORTCUT_DOWN,
            javafx.scene.input.KeyCombination.ALT_DOWN);

        shortcutHandler = event -> {
            if (sendShortcut.match(event)) {
                sendRequest();
                event.consume();
            }
            else if (saveShortcut.match(event)) {
                saveRequest();
                event.consume();
            }
            else if (refreshShortcut.match(event) || f5Shortcut.match(event)) {
                refreshCurrentRequest();
                event.consume();
            }
            else if (copyShortcut.match(event)) {
                copyCurrentNode();
                event.consume();
            }
            else if (formatShortcut.match(event)) {
                formatBody();
                event.consume();
            }
        };
        mainStackPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, shortcutHandler);
    }

    /**
     * 刷新当前请求（重新加载树节点数据）
     */
    private void refreshCurrentRequest() {
        if (currentTreeItem == null) {
            logger.warn("No request selected to refresh");
            return;
        }

        try {
            // 从数据库重新加载当前节点的数据
            HttpTreeItem refreshed = treeService.queryById(currentTreeItem.getId());
            if (refreshed != null) {
                currentTreeItem = refreshed;
                // 重新加载到UI
                if (HttpTreeItem.TYPE_REQUEST.equals(refreshed.getNodeType())) {
                    loadRequest(refreshed);
                    logger.info("Refreshed request: {}", refreshed.getName());
                }
            }
        }
        catch (Exception e) {
            logger.error("Failed to refresh request", e);
            showError(I18N.get("message.error.refresh_failed", e.getMessage()));
        }
    }

    /**
     * 格式化 Body 内容
     */
    private void formatBody() {
        String content = bodyEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            String bodyType = getSelectedBodyType();
            String formatted;

            switch (bodyType) {
                case "JSON":
                    formatted = jsonFormatterService.formatJson(content);
                    break;
                case "XML":
                    formatted = formatXml(content);
                    break;
                default:
                    // 尝试自动检测
                    if (jsonFormatterService.isValidJson(content)) {
                        formatted = jsonFormatterService.formatJson(content);
                    }
                    else if (isXml(content)) {
                        formatted = formatXml(content);
                    }
                    else {
                        logger.warn("Content is not JSON or XML, cannot format");
                        return;
                    }
            }

            if (formatted != null && !formatted.equals(content)) {
                bodyEditor.replaceText(formatted);
                logger.info("Body formatted successfully");
            }
        }
        catch (Exception e) {
            logger.error("Failed to format body", e);
            showError(I18N.get("message.error.format_failed", e.getMessage()));
        }
    }

    /**
     * 压缩 Body 内容
     */
    private void compactBody() {
        String content = bodyEditor.getText();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            String bodyType = getSelectedBodyType();
            String compacted;

            switch (bodyType) {
                case "JSON":
                    compacted = jsonFormatterService.compactJson(content);
                    break;
                case "XML":
                    compacted = compactXml(content);
                    break;
                default:
                    // 尝试自动检测
                    if (jsonFormatterService.isValidJson(content)) {
                        compacted = jsonFormatterService.compactJson(content);
                    }
                    else if (isXml(content)) {
                        compacted = compactXml(content);
                    }
                    else {
                        logger.warn("Content is not JSON or XML, cannot compact");
                        return;
                    }
            }

            if (compacted != null && !compacted.equals(content)) {
                bodyEditor.replaceText(compacted);
                logger.info("Body compacted successfully");
            }
        }
        catch (Exception e) {
            logger.error("Failed to compact body", e);
            showError(I18N.get("message.error.compact_failed", e.getMessage()));
        }
    }

    /**
     * 格式化 XML
     */
    private String formatXml(String xml) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(new ByteArrayInputStream(xml.getBytes()));

            TransformerFactory transformerFactory = TransformerFactory
                .newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");

            StringWriter writer = new StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(doc),
                new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString().trim();
        }
        catch (Exception e) {
            logger.error("XML formatting failed", e);
            return xml;
        }
    }

    /**
     * 压缩 XML（移除空格和换行）
     */
    private String compactXml(String xml) {
        try {
            return xml.replaceAll(">\\s+<", "><").trim();
        }
        catch (Exception e) {
            logger.error("XML compacting failed", e);
            return xml;
        }
    }

    /**
     * 检测是否是 XML
     */
    private boolean isXml(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("<") && trimmed.contains(">");
    }

    /**
     * 格式化响应内容（根据 Content-Type 自动选择格式）
     */
    private void formatResponse() {
        String content = responseBodyArea.getText();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        try {
            // 获取 Content-Type
            String contentType = getResponseContentType();
            String formatted = null;

            // 根据 Content-Type 选择格式化方式
            if (contentType != null) {
                if (contentType.contains("application/json") || contentType.contains("text/json")) {
                    // JSON 格式化
                    if (jsonFormatterService.isValidJson(content)) {
                        formatted = jsonFormatterService.formatJson(content);
                    }
                }
                else if (contentType.contains("application/xml") || contentType.contains("text/xml")) {
                    // XML 格式化
                    if (isXml(content)) {
                        formatted = formatXml(content);
                    }
                }
                else if (contentType.contains("text/html")) {
                    // HTML 格式化（使用 Jsoup）
                    formatted = formatHtml(content);
                }
            }

            // 如果没有 Content-Type 或不支持的类型，尝试自动检测
            if (formatted == null) {
                if (jsonFormatterService.isValidJson(content)) {
                    formatted = jsonFormatterService.formatJson(content);
                }
                else if (isXml(content)) {
                    formatted = formatXml(content);
                }
                else {
                    logger.warn("无法识别响应格式，Content-Type: " + contentType);
                    // Treat as plain text if it's an error or just unknown text
                    // showError("无法识别响应格式，请检查 Content-Type");
                    return;
                }
            }

            if (formatted != null && !formatted.equals(content)) {
                responseBodyArea.replaceText(formatted);
                logger.info("Response formatted successfully");
            }
        }
        catch (Exception e) {
            logger.error("Failed to format response", e);
            showError(I18N.get("message.error.format_failed", e.getMessage()));
        }
    }

    /**
     * 获取响应的 Content-Type
     */
    private String getResponseContentType() {
        if (currentResponse == null || currentResponse.getHeaders() == null) {
            return null;
        }

        // 查找 Content-Type header（不区分大小写）
        for (java.util.Map.Entry<String, String> entry : currentResponse.getHeaders().entrySet()) {
            if ("content-type".equalsIgnoreCase(entry.getKey())) {
                return entry.getValue().toLowerCase();
            }
        }
        return null;
    }

    /**
     * 切换 Body 编辑器的换行模式
     */
    private void toggleBodyWrap() {
        boolean currentWrap = bodyEditor.isWrapText();
        bodyEditor.setWrapText(!currentWrap);

    }

    /**
     * 格式化 HTML 内容
     */
    private String formatHtml(String html) {
        try {
            // 使用 Jsoup 解析并格式化 HTML
            Document doc = Jsoup.parse(html);
            // 设置缩进宽度为 2
            doc.outputSettings().indentAmount(2).prettyPrint(true);
            return doc.html();
        }
        catch (Exception e) {
            logger.error("HTML formatting failed", e);
            return html; // 格式化失败时返回原内容
        }
    }

    /**
     * 复制当前选中的节点
     */
    private void copyCurrentNode() {
        if (treeView == null) {
            logger.warn("TreeView not initialized");
            return;
        }

        TreeItem<HttpTreeItem> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getValue() == null) {
            showError(I18N.get("message.error.select_node_first"));
            return;
        }

        // 如果复制的是当前正在编辑的节点，先保存更改，确保复制的是最新数据
        // 并且直接使用 currentTreeItem 作为源，因为它包含了最新的内存状态
        HttpTreeItem original;
        if (currentTreeItem != null && selectedItem.getValue().getId().equals(currentTreeItem.getId())) {
            saveRequest();
            // saveRequest updates currentTreeItem in memory
            original = currentTreeItem;
        }
        else {
            original = selectedItem.getValue();
            // Reload from DB to ensure we have all latest fields
            if (original.getId() != null && original.getId() > 0) {
                HttpTreeItem reloaded = treeService.queryById(original.getId());
                if (reloaded != null) {
                    original = reloaded;
                }
            }
        }

        // 不允许复制根节点
        if (original.getId() == null || original.getId() == 0L) {
            showError(I18N.get("message.error.root_cannot_copy"));
            return;
        }

        try {
            // 创建副本
            HttpTreeItem copy = new HttpTreeItem();
            copy.setParentId(original.getParentId());
            copy.setName("copy-" + original.getName() + "-" + generateRandomSuffix());
            copy.setNodeType(original.getNodeType());
            copy.setIsLeaf(original.getIsLeaf());
            copy.setSortOrder(original.getSortOrder() != null ? original.getSortOrder() + 1 : 0);

            // 复制请求配置（如果是请求节点）
            if (HttpTreeItem.TYPE_REQUEST.equals(original.getNodeType())) {
                copy.setMethod(original.getMethod());
                copy.setUrl(original.getUrl());
                copy.setHeaders(original.getHeaders());
                copy.setParams(original.getParams());
                copy.setBody(original.getBody());
                copy.setBodyType(original.getBodyType());
                copy.setAuthConfig(original.getAuthConfig());
                copy.setHookScript(original.getHookScript());
                copy.setTimeout(original.getTimeout());
            }

            // 保存到数据库
            HttpTreeItem saved = treeService.add(copy).getValue();

            // 添加到树中（作为兄弟节点）
            TreeItem<HttpTreeItem> parent = selectedItem.getParent();
            if (parent != null) {
                TreeItem<HttpTreeItem> newTreeItem = new TreeItem<>(saved);
                parent.getChildren().add(newTreeItem);
                parent.setExpanded(true);
                treeView.getSelectionModel().select(newTreeItem);
            }

            logger.info("Successfully copied node: {} -> {}", original.getName(), saved.getName());

        }
        catch (Exception e) {
            logger.error("Failed to copy node", e);
            showError(I18N.get("message.error.copy_failed", e.getMessage()));
        }
    }

    /**
     * 生成随机后缀
     */
    private String generateRandomSuffix() {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void initHookScripts() {
        // 路径监听、浏览、保存已在 setupRequestConfigTab 中绑定；此处仅确保默认目录存在
        java.nio.file.Path defaultHooks = java.nio.file.Paths.get(System.getProperty("user.home"), ".opencgl", "hooks");
        try {
            if (!java.nio.file.Files.exists(defaultHooks)) {
                java.nio.file.Files.createDirectories(defaultHooks);
            }
        }
        catch (Exception e) {
            logger.warn("Could not create default hooks dir", e);
        }
    }

    private void browseHookAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("dialog.select_hook"));
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Groovy/JS Scripts", "*.groovy", "*.js"),
            new FileChooser.ExtensionFilter("All Files", "*.*"));
        String currentPath = hookScriptPathField != null ? hookScriptPathField.getText() : null;
        if (currentPath != null && !currentPath.trim().isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                fileChooser.setInitialDirectory(currentFile.isFile() ? currentFile.getParentFile() : currentFile);
            }
        }
        else {
            java.nio.file.Path defaultHooks = java.nio.file.Paths.get(System.getProperty("user.home"), ".opencgl", "hooks");
            if (java.nio.file.Files.exists(defaultHooks)) {
                fileChooser.setInitialDirectory(defaultHooks.toFile());
            }
        }
        Window owner = (mainStackPane != null && mainStackPane.getScene() != null) ? mainStackPane.getScene().getWindow() : null;
        File selected = fileChooser.showOpenDialog(owner);
        if (selected != null && hookScriptPathField != null) {
            hookScriptPathField.setText(selected.getAbsolutePath());
        }
    }

    private void saveHookAction() {
        String currentPath = hookScriptPathField != null ? hookScriptPathField.getText() : null;
        if (currentPath == null || currentPath.trim().isEmpty()) {
            showError(I18N.get("message.error.please_select_file"));
            return;
        }
        String content = hookPreviewTextArea != null ? hookPreviewTextArea.getText() : "";
        if (content == null) content = "";
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(currentPath);
            if (!java.nio.file.Files.exists(path) && path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }
            java.nio.file.Files.writeString(path, content);
            TooltipUtil.showToast(contentArea, I18N.get("msg.save_success"));
            onHookScriptPathChanged(currentPath);
        }
        catch (Exception e) {
            logger.error("Failed to save hook script", e);
            showError("Failed to save script: " + e.getMessage());
        }
    }

    private void onHookScriptPathChanged(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            currentHook = null;
            if (hookPreviewTextArea != null) hookPreviewTextArea.clear();
            return;
        }
        try {
            java.nio.file.Path scriptPath = java.nio.file.Paths.get(pathStr);
            if (!java.nio.file.Files.exists(scriptPath)) {
                java.nio.file.Path relativePath = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".opencgl", "hooks", pathStr);
                if (java.nio.file.Files.exists(relativePath)) {
                    scriptPath = relativePath;
                }
            }
            if (java.nio.file.Files.exists(scriptPath)) {
                currentHook = scriptEngineManager.loadScript(scriptPath);
                if (hookPreviewTextArea != null) {
                    try {
                        hookPreviewTextArea.setText(java.nio.file.Files.readString(scriptPath));
                    }
                    catch (Exception e) {
                        hookPreviewTextArea.setText("// Failed to load: " + e.getMessage());
                    }
                }
                logger.info("Loaded hook script: {}", scriptPath);
            }
            else {
                if (hookPreviewTextArea != null) hookPreviewTextArea.setText("// Script file not found: " + pathStr);
                currentHook = null;
            }
        }
        catch (Exception ex) {
            logger.error("Load hook script failed", ex);
            if (hookPreviewTextArea != null) hookPreviewTextArea.setText("// Failed to load: " + ex.getMessage());
            currentHook = null;
        }
    }

    public void testHookAction() {
        if (currentHook == null) {
            showError(I18N.get("message.error.select_hook_first"));
            return;
        }
        try {
            HookContext context = new HookContext();
            context.setProtocol("http");
            context.put("url", urlField != null ? urlField.getText() : "");
            context.put("method", methodComboBox != null ? methodComboBox.getValue() : "");
            context.put("log", org.slf4j.LoggerFactory.getLogger("HookScript"));
            context.put("history", com.opencgl.base.utils.OperationHisRecord.class);
            StringBuilder report = new StringBuilder("Hook Execution Report:\n\n");
            String originalRequest = bodyEditor != null ? bodyEditor.getNonAnnotationText() : "";
            if (originalRequest == null || originalRequest.trim().isEmpty()) {
                report.append("[Pre-Process] Skipped (Empty Request)\n");
            }
            else {
                long start = System.currentTimeMillis();
                try {
                    String processed = currentHook.preProcess(originalRequest, context);
                    long duration = System.currentTimeMillis() - start;
                    report.append("[Pre-Process] Executed in ").append(duration).append(" ms\n");
                    report.append(originalRequest.equals(processed) ? "Result: No Change\n" : "Result: Request MODIFIED\n");
                }
                catch (Exception e) {
                    report.append("[Pre-Process] Failed: ").append(e.getMessage()).append("\n");
                }
            }
            report.append("\n--------------------------------------------------\n\n");
            String originalResponse = responseBodyArea != null ? responseBodyArea.getText() : "";
            if (originalResponse == null || originalResponse.trim().isEmpty()) {
                report.append("[Post-Process] Skipped (Empty Response)\n");
            }
            else {
                long start = System.currentTimeMillis();
                try {
                    String processed = currentHook.postProcess(originalResponse, context);
                    long duration = System.currentTimeMillis() - start;
                    report.append("[Post-Process] Executed in ").append(duration).append(" ms\n");
                    report.append(originalResponse.equals(processed) ? "Result: No Change\n" : "Result: Response MODIFIED\n");
                }
                catch (Exception e) {
                    report.append("[Post-Process] Failed: ").append(e.getMessage()).append("\n");
                }
            }
            if (!context.getScriptOutput().isEmpty()) {
                report.append("--- [Script Print Output] ---\n");
                for (String msg : context.getScriptOutput()) {
                    report.append("> ").append(msg).append("\n");
                }
                report.append("-----------------------------\n\n");
            }
            if (hookOutputTextArea != null) hookOutputTextArea.setText(report.toString());
        }
        catch (Exception e) {
            logger.error("Hook test failed", e);
            showError("Hook execution failed: " + e.getMessage());
        }
    }

    private void sendRequest() {
        // UI Loading State
        loadingMask.show(contentArea);
        sendButton.setDisable(true);
        sendButton.textProperty().unbind(); // Unbind to set transient text
        sendButton.setText(I18N.get("button.sending"));

        try {
            HttpRequestModel requestModel = new HttpRequestModel();
            requestModel.setMethod(methodComboBox.getValue());

            // Variable Substitution for URL
            String rawUrl = urlField.getText();
            String finalUrl = environmentService.substitute(rawUrl);
            requestModel.setUrl(finalUrl);

            // Request Configuration
            try {
                int timeout = Integer.parseInt(configTimeoutField.getText().trim());
                requestModel.setTimeout(timeout);
            }
            catch (NumberFormatException e) {
                // Default to 30 if invalid
                requestModel.setTimeout(30);
            }
            requestModel.setSslVerification(configSslCheck.isSelected());
            requestModel.setFollowRedirects(configRedirectCheck.isSelected());

            // mTLS / Custom CA Config
            requestModel.setClientCertPath(configCertPathField.getText());
            requestModel.setClientCertPass(configCertPassField.getText());
            requestModel.setServerCertPath(configCaPathField.getText());
            requestModel.setServerCertPass(configCaPassField.getText());

            // Headers (Substitute keys and values)
            headersData.stream()
                .filter(KeyValueEntry::isEnabled)
                .forEach(e -> {
                    String k = environmentService.substitute(e.getKey());
                    String v = environmentService.substitute(e.getValue());
                    requestModel.getHeaders().put(k, v);
                });

            String authType = authTypeCombo.getValue();
            if ("Basic Auth".equals(authType)) {
                TextField u = findAuthField("authUsername");
                TextField p = findAuthField("authPassword");
                if (u != null && p != null) {
                    String user = environmentService.substitute(u.getText());
                    String pass = environmentService.substitute(p.getText());
                    String auth = user + ":" + pass;
                    String encoded = Base64.getEncoder()
                        .encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    // OVERRIDE the one from table if it exists
                    requestModel.getHeaders().put("Authorization", "Basic " + encoded);
                }
            }

            paramsData.stream()
                .filter(KeyValueEntry::isEnabled)
                .forEach(e -> {
                    String k = environmentService.substitute(e.getKey());
                    String v = environmentService.substitute(e.getValue());
                    requestModel.getParams().put(k, v);
                });

            // Body
            String type = getSelectedBodyType();
            requestModel.setBodyType(type);
            if ("FORM".equals(type)) {
                String formContent = bodyFormData.stream()
                    .filter(KeyValueEntry::isEnabled)
                    .map(e -> {
                        String k = environmentService.substitute(e.getKey());
                        String v = environmentService.substitute(e.getValue());
                        return java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8) + "=" +
                            java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
                    })
                    .collect(java.util.stream.Collectors.joining("&"));
                requestModel.setBody(formContent);
                // Ensure content-type is set for form
                if (!requestModel.getHeaders().containsKey("Content-Type")) {
                    requestModel.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
                }
            }
            else {
                String rawBody = FormatVariableUtil.format(bodyEditor.getNonAnnotationText());
                requestModel.setBody(environmentService.substitute(rawBody));
            }

            // Execute Pre-request Hook
            if (currentHook != null) {
                try {
                    HookContext context = new HookContext();
                    context.setEnvironment(environmentService.getCurrentEnvName());
                    context.setProtocol("http");
                    context.setHeaders(requestModel.getHeaders());
                    context.put("log", org.slf4j.LoggerFactory.getLogger("HookScript"));
                    context.put("history", com.opencgl.base.utils.OperationHisRecord.class);
                    // Pass full request model to script as "request"
                    context.put("request", requestModel);
                    context.put("env", environmentService); // Allow script to access env service if needed

                    String currentBody = requestModel.getBody() == null ? "" : requestModel.getBody();
                    String newBody = currentHook.preProcess(currentBody, context);
                    requestModel.setBody(newBody);

                    // Sync back headers if modified in context
                    if (context.getHeaders() != null) {
                        requestModel.setHeaders(context.getHeaders());
                    }

                    logger.info("Executed pre-request hook: {}", currentHook.getName());
                }
                catch (Exception e) {
                    logger.error("Failed to execute pre-request hook", e);
                    Platform.runLater(() -> {
                        showError(I18N.get("message.error.pre_request_script", e.getMessage()));
                        loadingMask.hide();
                    });
                    restoreSendButton();
                    return;
                }
            }

            // Send...
            submitBackground(() -> {
                try {
                    HttpResponseModel response = httpClientService.sendRequest(requestModel);
                    // Save History (Fire and forget)
                    historyService.add(requestModel, response);
                    runOnFx(() -> {
                        try {
                            displayResponse(response);
                        }
                        catch (Exception e) {
                            logger.error("Error displaying response", e);
                            showError(I18N.get("message.error.display_response", e.getMessage()));
                        }
                        finally {
                            loadingMask.hide();
                            restoreSendButton();
                        }
                    });
                }
                catch (Exception e) {
                    runOnFx(() -> {
                        HttpResponseModel errorResponse = new HttpResponseModel();
                        errorResponse.setStatusCode(0);
                        errorResponse.setStatusMessage(I18N.get("label.error.internal"));
                        java.io.StringWriter sw = new java.io.StringWriter();
                        e.printStackTrace(new java.io.PrintWriter(sw));
                        errorResponse.setBody("Unexpected Error:\n" + sw);
                        errorResponse.setHeaders(Collections.emptyMap());

                        try {
                            displayResponse(errorResponse);
                        }
                        catch (Exception displayError) {
                            showError(I18N.get("message.error.critical", e.getMessage()));
                        }
                        finally {
                            loadingMask.hide();
                            restoreSendButton();
                        }
                    });
                }
            });

        }
        catch (Exception e) {
            logger.error("Failed to build request", e);
            showError(I18N.get("message.error.build_request", e.getMessage()));
            loadingMask.hide();
            restoreSendButton();
        }
    }

    private void restoreSendButton() {
        sendButton.setDisable(false);
        sendButton.textProperty().bind(I18N.getBinding("button.send"));
    }

    private void displayResponse(HttpResponseModel response) {
        // Tests Hook should run here
        // ...

        responseBodyArea.replaceText(response.getBody());

        responseHeadersData.clear();
        response.getHeaders().forEach((k, v) -> responseHeadersData.add(new KeyValueEntry(k, v)));

        // 保存当前响应
        currentResponse = response;
        currentResponseProperty.set(response);

        // 自动格式化响应内容
        Platform.runLater(() -> {
            try {
                formatResponse();
                updateResponsePreview(response);
            }
            catch (Exception e) {
                logger.warn("Auto-format failed, keeping original content", e);
            }
        });
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(msg);
        alert.show();
    }

    // --- Environment Management ---

    private void setupEnvironmentUI() {
        // Assume envComboBox and manageEnvButton are injected.
        // If they are null (old FXML), we might need to inject them into the layout
        // dynamically.
        // For safety, check null.
        if (envComboBox == null) {
            envComboBox = new ComboBox<>();
            // Hack: Try to add to top layout if possible, or just fail gracefully
            if (methodComboBox != null && methodComboBox.getParent() instanceof HBox) {
                HBox topBar = (HBox) methodComboBox.getParent();
                topBar.getChildren().add(topBar.getChildren().indexOf(urlField) + 1, envComboBox);

                manageEnvButton = new Button("Env");
                manageEnvButton.setOnAction(e -> manageEnvironments());
                topBar.getChildren().add(topBar.getChildren().indexOf(envComboBox) + 1, manageEnvButton);
            }
        }

        if (envComboBox != null) {
            refreshEnvCombo();
            envComboBox.valueProperty().addListener((obs, old, val) -> {
                environmentService.setCurrentEnvName(val);
            });
            // Auto Select current
            envComboBox.getSelectionModel().select(environmentService.getCurrentEnvName());
        }
    }

    private void refreshEnvCombo() {
        if (envComboBox != null) {
            // "None" 仅表示未配置，放在首位，不进入环境配置列表
            List<String> names = new ArrayList<>(environmentService.getEnvironmentNames());
            names.add(0, com.opencgl.http.service.EnvironmentService.DEFAULT_ENV_NAME);
            envComboBox.setItems(FXCollections.observableArrayList(names));
        }
    }

    /**
     * 环境配置入口，参考 DubboWidgetController#settingLabelAction / #manageEnvAction
     */
    private void manageEnvironments() {
        try {
            Window owner = (mainStackPane != null && mainStackPane.getScene() != null)
                ? mainStackPane.getScene().getWindow() : null;
            httpEnvConfigureDialog.showAndWait(owner);
        }
        catch (java.io.IOException e) {
            logger.error("Failed to open env dialog", e);
        }
        refreshEnvCombo();
    }

    // --- Response Preview ---

    // --- Proxy Settings ---
    private void showProxyDialog() {
        Window owner = (mainStackPane != null && mainStackPane.getScene() != null)
            ? mainStackPane.getScene().getWindow() : null;
        Dialog<ProxyConfig> dialog = new Dialog<>();
        dialog.setTitle(I18N.get("dialog.proxy.title"));
        dialog.setHeaderText(I18N.get("dialog.proxy.header"));
        if (owner != null) dialog.initOwner(owner);

        ButtonType saveBtnType = new ButtonType(I18N.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        // Load current
        ProxyConfigService proxyService = httpClientService.getProxyConfigService();
        ProxyConfig current = proxyService.getConfig();
        if (current == null)
            current = new ProxyConfig();

        // UI
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        CheckBox enableChk = new CheckBox(I18N.get("label.enable_proxy"));
        enableChk.setSelected(current.isEnabled());

        TextField hostField = new TextField(current.getHost());
        hostField.setPromptText("127.0.0.1");

        TextField portField = new TextField(String.valueOf(current.getPort()));
        portField.setPromptText("8888");

        // Enable/Disable fields
        hostField.disableProperty().bind(enableChk.selectedProperty().not());
        portField.disableProperty().bind(enableChk.selectedProperty().not());

        grid.add(enableChk, 0, 0, 2, 1);
        grid.add(new Label(I18N.get("label.host") + ":"), 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(new Label(I18N.get("label.port") + ":"), 0, 2);
        grid.add(portField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Convert result
        // Need effectively final vars for lambda, but fields are fine
        ProxyConfig finalCurrent = current;
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                ProxyConfig newConfig = new ProxyConfig();
                newConfig.setEnabled(enableChk.isSelected());
                newConfig.setHost(hostField.getText());
                try {
                    newConfig.setPort(Integer.parseInt(portField.getText()));
                }
                catch (NumberFormatException e) {
                    newConfig.setPort(8888);
                }
                return newConfig;
            }
            return null;
        });

        dialog.setOnShown(ev -> {
            if (owner != null) DialogUtil.centerDialogOnOwner(dialog, owner);
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) ThemeManager.getInstance().registerScene(scene);
        });
        dialog.setOnHidden(ev -> {
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) ThemeManager.getInstance().unregisterScene(scene);
        });

        dialog.showAndWait().ifPresent(config -> {
            proxyService.saveConfig(config);
            // Visual feedback?
            if (config.isEnabled()) {
                proxyButton.setStyle("-fx-base: #ffeb3b"); // Highlight if active
            }
            else {
                proxyButton.setStyle("");
            }
        });
    }

    private void setupResponsePreview() {
        if (previewInBrowserButton != null) {
            previewInBrowserButton.setOnAction(e -> openResponseInBrowser());
        }
    }

    private void setupCertPickers() {
        if (browseCertBtn != null) {
            browseCertBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.setTitle(I18N.get("dialog.select_client_cert"));
                fc.getExtensionFilters()
                    .add(new FileChooser.ExtensionFilter("Keystore", "*.jks", "*.p12", "*.pfx"));
                java.io.File f = fc.showOpenDialog(browseCertBtn.getScene().getWindow());
                if (f != null) {
                    configCertPathField.setText(f.getAbsolutePath());
                }
            });
        }

        if (browseCaBtn != null) {
            browseCaBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.setTitle(I18N.get("dialog.select_ca_cert"));
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Certificates", "*.cer",
                    "*.crt", "*.pem", "*.jks", "*.p12"));
                File f = fc.showOpenDialog(browseCaBtn.getScene().getWindow());
                if (f != null) {
                    configCaPathField.setText(f.getAbsolutePath());
                }
            });
        }
    }

    private void updateResponsePreview(HttpResponseModel response) {
        if (previewInBrowserButton == null)
            return;

        String contentType = getResponseContentType();
        boolean isHtml = contentType != null && contentType.contains("text/html");

        previewInBrowserButton.setVisible(isHtml);
        previewInBrowserButton.setManaged(isHtml);
    }

    private void openResponseInBrowser() {
        if (currentResponse == null || currentResponse.getBody() == null)
            return;

        try {
            // Create temporary file
            File tempFile = File.createTempFile("opencgl_preview_", ".html");
            tempFile.deleteOnExit();
            temporaryPreviewFiles.add(tempFile.toPath());

            // Write content
            java.nio.file.Files.write(tempFile.toPath(),
                currentResponse.getBody().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Open in browser
            if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(tempFile.toURI());
            }
            else {
                showError(I18N.get("message.error.browser_not_supported"));
            }
        }
        catch (Exception e) {
            logger.error("Failed to open browser", e);
            showError(I18N.get("message.error.browser_failed") + ": " + e.getMessage());
        }
    }

    // --- Code Generation ---

    private void setupCodeGeneratorUI() {
        if (codeButton == null) {
            codeButton = new Button(I18N.get("button.code"));
            // Insert into top bar if possible
            if (saveButton != null && saveButton.getParent() instanceof HBox) {
                HBox topBar = (HBox) saveButton.getParent();
                int idx = topBar.getChildren().indexOf(saveButton);
                if (idx != -1) {
                    topBar.getChildren().add(idx + 1, codeButton);
                }
            }
        }

        if (codeButton != null) {
            codeButton.setOnAction(e -> showCodeDialog());
        }
    }

    private void showCodeDialog() {
        HttpRequestModel requestModel = new HttpRequestModel();
        // Populate request model similar to sendRequest, but WITHOUT substitution for
        // raw code gen
        // OR WITH substitution if user wants resolved request.
        // Usually code gen shows what you would run, so substitution is better if
        // resolved,
        // OR postman shows variable placeholders. Postman shows resolved usually or
        // options.
        // Let's substitute for now to be executable.

        requestModel.setMethod(methodComboBox.getValue());
        requestModel.setUrl(environmentService.substitute(urlField.getText()));

        headersData.stream().filter(KeyValueEntry::isEnabled).forEach(e -> requestModel.getHeaders()
            .put(environmentService.substitute(e.getKey()), environmentService.substitute(e.getValue())));

        paramsData.stream().filter(KeyValueEntry::isEnabled).forEach(e -> requestModel.getParams()
            .put(environmentService.substitute(e.getKey()), environmentService.substitute(e.getValue())));

        String type = getSelectedBodyType();
        requestModel.setBodyType(type);
        if ("FORM".equals(type)) {
            String formContent = bodyFormData.stream()
                .filter(KeyValueEntry::isEnabled)
                .map(e -> {
                    String k = environmentService.substitute(e.getKey());
                    String v = environmentService.substitute(e.getValue());
                    return java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8) + "=" +
                        java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
                })
                .collect(java.util.stream.Collectors.joining("&"));
            requestModel.setBody(formContent);
        }
        else {
            requestModel.setBody(environmentService.substitute(bodyEditor.getNonAnnotationText()));
        }

        // Get current proxy config
        ProxyConfig proxyConfig = httpClientService.getProxyConfigService().getConfig();

        // Generate
        String curl = codeGeneratorService.generateCurl(requestModel, proxyConfig);
        String java = codeGeneratorService.generateJava(requestModel, proxyConfig);
        String python = codeGeneratorService.generatePython(requestModel, proxyConfig);

        // Dialog：跟随主窗口所在显示器，并接入主题
        Window owner = (mainStackPane != null && mainStackPane.getScene() != null)
            ? mainStackPane.getScene().getWindow() : null;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(I18N.get("button.code"));
        dialog.setHeaderText(I18N.get("dialog.code.header"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (owner != null) dialog.initOwner(owner);

        TabPane tabs = new TabPane();
        tabs.getTabs().add(createCodeTab("cURL", curl));
        tabs.getTabs().add(createCodeTab("Java", java));
        tabs.getTabs().add(createCodeTab("Python", python));

        tabs.setPrefSize(600, 400);
        dialog.getDialogPane().setContent(tabs);
        dialog.setOnShown(ev -> {
            if (owner != null) DialogUtil.centerDialogOnOwner(dialog, owner);
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) ThemeManager.getInstance().registerScene(scene);
        });
        dialog.setOnHidden(ev -> {
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) ThemeManager.getInstance().unregisterScene(scene);
        });
        dialog.show();
    }

    private Tab createCodeTab(String title, String code) {
        Tab tab = new Tab(title);
        TextArea area = new TextArea(code);
        area.setEditable(false);
        area.setWrapText(true);
        tab.setContent(area);
        return tab;
    }

    private void showImportDialog() {
        Window owner = (mainStackPane != null && mainStackPane.getScene() != null)
            ? mainStackPane.getScene().getWindow() : null;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(I18N.get("button.import"));
        dialog.setHeaderText(I18N.get("dialog.import.header"));
        if (owner != null) dialog.initOwner(owner);

        ButtonType importBtn = new ButtonType(I18N.get("button.import"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importBtn, ButtonType.CANCEL);

        TabPane tabPane = new TabPane();

        // cURL Tab
        Tab curlTab = new Tab(I18N.get("tab.curl"));
        curlTab.setClosable(false);
        VBox curlBox = new VBox(10);
        curlBox.setPadding(new javafx.geometry.Insets(10));
        Label curlLabel = new Label(I18N.get("label.paste_curl") + ":");
        TextArea curlArea = new TextArea();
        curlArea.setPromptText(
            "curl -X POST https://api.example.com -H 'Content-Type: application/json' -d '{\"key\":\"value\"}'");
        curlArea.setPrefRowCount(8);
        curlArea.setWrapText(true);
        curlBox.getChildren().addAll(curlLabel, curlArea);
        curlTab.setContent(curlBox);

        // Swagger Tab
        Tab swaggerTab = new Tab("Swagger/OpenAPI");
        swaggerTab.setClosable(false);
        VBox swaggerBox = new VBox(10);
        swaggerBox.setPadding(new javafx.geometry.Insets(10));
        Label swaggerLabel = new Label(I18N.get("label.select_swagger") + ":");
        TextField swaggerFilePath = new TextField();
        swaggerFilePath.setEditable(false);
        swaggerFilePath.setPromptText(I18N.get("label.no_file_selected"));
        Button browseBtn = new Button("Browse...");
        HBox fileBox = new HBox(10, swaggerFilePath, browseBtn);
        HBox.setHgrow(swaggerFilePath, Priority.ALWAYS);

        final File[] selectedFile = {null};
        browseBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(I18N.get("dialog.select_swagger"));
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            File file = fileChooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                selectedFile[0] = file;
                swaggerFilePath.setText(file.getAbsolutePath());
            }
        });

        swaggerBox.getChildren().addAll(swaggerLabel, fileBox);
        swaggerTab.setContent(swaggerBox);

        tabPane.getTabs().addAll(curlTab, swaggerTab);
        tabPane.setPrefSize(600, 300);
        dialog.getDialogPane().setContent(tabPane);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == importBtn) {
                try {
                    Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();

                    if (selectedTab == curlTab) {
                        String curlCmd = curlArea.getText();
                        if (curlCmd != null && !curlCmd.trim().isEmpty()) {
                            com.opencgl.http.model.HttpRequestModel requestModel = CurlImporter.parse(curlCmd);

                            // 直接更新当前请求面板
                            Platform.runLater(() -> {
                                // 显示请求面板
                                emptyState.setVisible(false);
                                emptyState.setManaged(false);
                                requestPanel.setVisible(true);
                                requestPanel.setManaged(true);

                                // 填充 Method 和 URL
                                methodComboBox.setValue(requestModel.getMethod());
                                urlField.setText(requestModel.getUrl());

                                // 填充 Headers
                                if (requestModel.getHeaders() != null && !requestModel.getHeaders().isEmpty()) {
                                    headersData.clear();
                                    requestModel.getHeaders()
                                        .forEach((k, v) -> headersData.add(new KeyValueEntry(k, v, true)));
                                }

                                // 填充 Body
                                if (requestModel.getBody() != null) {
                                    bodyEditor.replaceText(requestModel.getBody());
                                    String bodyType = requestModel.getBodyType();
                                    if ("JSON".equals(bodyType)) {
                                        bodyJsonRadio.setSelected(true);
                                    }
                                    else if ("FORM".equals(bodyType)) {
                                        bodyFormRadio.setSelected(true);
                                    }
                                    else {
                                        bodyRawRadio.setSelected(true);
                                    }
                                }

                                showInfo(I18N.get("message.info.import_success"));
                            });
                        }
                    }
                    else if (selectedTab == swaggerTab) {
                        if (selectedFile[0] != null) {
                            Long parentId = currentTreeItem != null
                                ? (currentTreeItem.getNodeType().equals(HttpTreeItem.TYPE_FOLDER)
                                ? currentTreeItem.getId()
                                : currentTreeItem.getParentId())
                                : 0L;

                            List<HttpTreeItem> items = SwaggerImporter
                                .importFromFile(selectedFile[0], parentId);

                            for (HttpTreeItem item : items) {
                                treeService.save(item);
                            }

                            Platform.runLater(() -> {
                                setupTreeView();
                                showInfo("Imported " + items.size() + " requests from Swagger");
                            });
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("Import failed", e);
                    Platform.runLater(() -> showError("Import failed: " + e.getMessage()));
                }
            }
            return null;
        });

        dialog.setOnShown(ev -> {
            if (owner != null) DialogUtil.centerDialogOnOwner(dialog, owner);
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) ThemeManager.getInstance().registerScene(scene);
        });
        dialog.setOnHidden(ev -> {
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) ThemeManager.getInstance().unregisterScene(scene);
        });

        dialog.show();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18N.get("msg.success"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void submitBackground(Runnable action) {
        if (disposed) return;
        backgroundTasks.removeIf(Future::isDone);
        backgroundTasks.add(backgroundExecutor.submit(action));
    }

    private void runOnFx(Runnable action) {
        if (disposed) return;
        Platform.runLater(() -> {
            if (!disposed) action.run();
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Future<?> task : backgroundTasks) {
            try {
                task.cancel(true);
            } catch (Exception ignored) {
            }
        }
        backgroundTasks.clear();
        try {
            backgroundExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
        if (historyService != null) {
            try {
                historyService.close();
            } catch (Exception ignored) {
            }
        }
        if (httpClientService != null) {
            try {
                httpClientService.close();
            } catch (Exception ignored) {
            }
        }
        if (mainStackPane != null && shortcutHandler != null) {
            try {
                mainStackPane.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, shortcutHandler);
            } catch (Exception ignored) {
            }
            shortcutHandler = null;
        }
        for (Path path : temporaryPreviewFiles) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
        temporaryPreviewFiles.clear();
    }
}
