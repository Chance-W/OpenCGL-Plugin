package com.opencgl.dubbo.controller;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.controls.CustomMFXFilterComboBox;
import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.base.hook.HookContext;
import com.opencgl.base.hook.RequestHook;
import com.opencgl.base.hook.ScriptEngineManager;
import com.opencgl.base.hook.TlsConfig;
import com.opencgl.base.model.Base;
import com.opencgl.base.model.HistoryItem;
import com.opencgl.base.service.HistoryService;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.history.HistoryViewBuilder;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.base.view.RequestManagerView;
import com.opencgl.dubbo.dao.DubboWidgetDao;
import com.opencgl.dubbo.factory.DubboSendMessageFactory;
import com.opencgl.dubbo.i18n.I18N;
import com.opencgl.dubbo.model.DubboEnvConfig;
import com.opencgl.dubbo.model.DubboHistoryItem;
import com.opencgl.dubbo.model.DubboInfo;
import com.opencgl.dubbo.model.DubboRequest;
import com.opencgl.dubbo.model.DubboResponse;
import com.opencgl.dubbo.model.DubboTreeItem;
import com.opencgl.dubbo.utils.DubboConfigFileParseUtil;
import com.opencgl.dubbo.utils.DubboUrlUtils;
import com.opencgl.dubbo.utils.DubboUtil;
import com.opencgl.dubbo.utils.ZkClientTestUtil;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DubboWidgetController implements Initializable, TreeOperateService<DubboTreeItem> {
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "dubbo-client-worker"); t.setDaemon(true); return t; });
    private final Set<Future<?>> runningTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;
    // 保存自己的ClassLoader
    private final ClassLoader pluginCl;

    public DubboWidgetController() {
        this.pluginCl = this.getClass().getClassLoader();
    }

    @FXML
    protected SplitPane splitPane;
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    public StackPane contentInputAndOutputPane;
    @FXML
    protected Button sendButton;
    @FXML
    protected Button refreshButton;
    @FXML
    protected Button saveButton;
    @FXML
    protected Button copyButton;
    @FXML
    protected Button importButton;
    @FXML
    protected Button manageEnvButton;

    @FXML
    protected CustomMFXFilterComboBox<String> chooseEnvComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseIntComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseMetComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseProvideComboBox;

    @FXML
    protected TabPane requestTabPane;
    @FXML
    protected Tab requestBodyTab;

    @FXML
    protected TextField extRequestType;
    @FXML
    protected CustomTextArea outputTextArea;
    @FXML
    protected CustomTextArea inputTextArea;
    @FXML
    protected TextField requestCountField;
    @FXML
    protected TextField timeoutField;
    @FXML
    protected TextField retriesField;
    @FXML
    protected TextField versionField;
    @FXML
    protected TextField dubboGroupField;
    @FXML
    protected CustomTextArea attachmentsArea;
    @FXML
    protected CustomTextArea hookOutputTextArea;
    @FXML
    protected CustomTextArea hookPreviewTextArea;

    // History Detail Views
    @FXML
    protected StackPane historyDetailPane;
    @FXML
    protected CustomTextArea historyFullTextArea;

    @FXML
    public Label statusLabel;
    @FXML
    public Label sizeLabel;
    @FXML
    public Label timeLabel;

    @FXML
    private Label connConfigLabel;
    @FXML
    private Label timeoutLabel;
    @FXML
    private Label retriesLabel;
    @FXML
    private Label directProviderLabel;
    @FXML
    private Label reqTypeLabel;
    @FXML
    private Label batchReqLabel;
    @FXML
    private Label countLabel;
    @FXML
    private Label versionLabel;
    @FXML
    private Label dubboGroupLabel;
    @FXML
    private Label implicitParamLabel;
    @FXML
    private Label tlsConfigLabel;
    @FXML
    private Label tlsEnableLabel;
    @FXML
    private Label clientCertLabel;
    @FXML
    private Label clientKeyLabel;
    @FXML
    private Label keyPassLabel;
    @FXML
    private Label caCertLabel;
    @FXML
    private Label hookScriptLabel;
    @FXML
    private Label scriptPreviewLabel;
    @FXML
    private Label testOutputLabel;
    @FXML
    private Button testHookButton;

    @FXML
    private Tab settingsTab;
    @FXML
    private Tab hooksTab;

    // TLS Config Fields
    @FXML
    protected io.github.palexdev.materialfx.controls.MFXCheckbox tlsEnableCheckbox;
    // @FXML protected io.github.palexdev.materialfx.controls.MFXCheckbox
    // tlsMutualAuthCheckbox; // Removed
    @FXML
    protected TextField tlsClientCertPathField;
    @FXML
    protected TextField tlsClientKeyPathField;
    @FXML
    protected PasswordField tlsKeyPasswordField; // Changed from MFXPasswordField
    @FXML
    protected TextField tlsKeyTextFieldVisible; // Phase 21: Password Toggle
    @FXML
    protected Button tlsKeyToggleBtn; // Phase 21: Password Toggle

    @FXML
    protected TextField tlsCaCertPathField;

    @FXML
    public void toggleTlsKeyVisibility() {
        boolean isTextVisible = tlsKeyTextFieldVisible.isVisible();
        if (isTextVisible) {
            // Switch to Password Mode
            tlsKeyTextFieldVisible.setVisible(false);
            tlsKeyTextFieldVisible.setManaged(false);
            tlsKeyPasswordField.setVisible(true);
            tlsKeyPasswordField.setManaged(true);
            // Open Eye Icon
            ((javafx.scene.shape.SVGPath) tlsKeyToggleBtn.getGraphic()).setContent(
                "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z");
        }
        else {
            // Switch to Text Mode
            tlsKeyPasswordField.setVisible(false);
            tlsKeyPasswordField.setManaged(false);
            tlsKeyTextFieldVisible.setVisible(true);
            tlsKeyTextFieldVisible.setManaged(true);
            // Closed Eye Icon (Slash)
            ((SVGPath) tlsKeyToggleBtn.getGraphic()).setContent(
                "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z");
        }
    }

    private final Logger logger = LoggerFactory.getLogger(DubboWidgetController.class);
    private final DubboWidgetDao dubboWidgetDao = new DubboWidgetDao();
    private final DubboConfigFileParseUtil dubboConfigFileOperUtil = new DubboConfigFileParseUtil();
    private final DubboEnvConfigureDialog dubboEnvConfigureController = new DubboEnvConfigureDialog();
    private final ZkClientTestUtil zkClientTest = new ZkClientTestUtil();
    private final String basePath = Base.BASE_PATH + "/conf/dubbo/";

    private final SendMessageService<DubboRequest, DubboResponse> sendMessageService = DubboSendMessageFactory
        .sendDubboMessage();

    private TreeView<DubboTreeItem> dubboTreeItemTreeView = null;
    private DubboTreeItem currentDisplayItem; // Track currently displayed item for Locate feature

    // Unified UI Components
    private RequestManagerView requestManager;
    private HistoryViewBuilder<DubboHistoryItem> historyViewBuilder;

    // Hook 扩展支持
    private ScriptEngineManager scriptEngineManager;
    private RequestHook currentHook;

    @FXML
    protected TextField hookScriptPathField; // Changed from MFXComboBox
    @FXML
    protected Button browseHookButton;
    @FXML
    protected Button saveHookButton;

    // Cache for view switching
    private Node originalTop;
    private Node originalCenter;

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Capture original view state FIRST
        originalTop = contentBorderPane.getTop();
        originalCenter = contentBorderPane.getCenter();

        initI18n();
        init();
        showTooltip();
        setupKeyboardShortcuts();

        // 1. Build Collection View (TreeView)
        VBox treeViewContainer = new TreeViewBuilder<DubboTreeItem>()
            .service(this)
            .onTreeCreated(tree -> this.dubboTreeItemTreeView = tree)
            .dataType(DubboTreeItem.class)
            .enableDragDrop(true)
            .enableSearch()
            .enableToolbar(true) // NEW: Enable Toolbar
            .locateTargetSupplier(() -> this.currentDisplayItem) // Provide current display item for Locate
            .searchPrompt("搜索节点。。。")
            .build();

        // 2. Build History View
        historyViewBuilder = new HistoryViewBuilder<DubboHistoryItem>()
            .service(new HistoryService() {
                @Override
                public List<DubboHistoryItem> getHistory() {
                    try {
                        return dubboWidgetDao.queryHistory();
                    }
                    catch (Exception e) {
                        logger.error("Failed to query history", e);
                        return new ArrayList<>();
                    }
                }

                @Override
                public void clearHistory() {
                    try {
                        dubboWidgetDao.clearHistory();
                    }
                    catch (Exception e) {
                        logger.error("Failed to clear history", e);
                    }
                }

                @Override
                public void deleteHistory(HistoryItem item) {
                    // 单条删除，暂不实现
                }
            })
            // Simplified Phase 1: Cell Display Configuration
            .cellDisplay(config -> config
                .primaryText(DubboHistoryItem::getMethodName)
                .secondaryText(DubboHistoryItem::getInterfaceName)
                .badgeText(item -> item.getEnvName() != null ? item.getEnvName() : "Unknown Env")
                .timestampField(DubboHistoryItem::getTimestamp))
            // Manual Detail View Management via onSelect
            .onSelect(this::onHistorySelected)
            .restoreAction(this::restoreHistory)
            .returnToMain(contentBorderPane, originalTop, originalCenter); // Split build()

        VBox historyView = historyViewBuilder.build();

        // 3. Create RequestManagerView (Unified Container)
        requestManager = new RequestManagerView();
        requestManager.setCollectionView(treeViewContainer);
        requestManager.setHistoryView(historyView);

        // 4. Setup Theme and Layout
        // The RequestManagerView contains the TabPane.
        // ThemeSwitchUtil.treeStyleSwitch expects a TabPane as the 3rd argument.
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, requestManager.getTabPane());

        // Listen to tab selection to manage view switching
        requestManager.getTabPane().getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null)
                return;

            // If Collection Tab selected (Index 0)
            if (newVal == requestManager.getTabPane().getTabs().get(0)) {
                // Return to original view if needed
                if (contentBorderPane.getTop() != originalTop) {
                    contentBorderPane.setTop(originalTop);
                    contentBorderPane.setCenter(originalCenter);
                    historyDetailPane.setVisible(false);
                }
            }
            // If History Tab selected (Index 1)
            else if (newVal == requestManager.getTabPane().getTabs().get(1)) {
                // Logic to show history view is handled by the tab itself
                // But if we were in detail view, maybe we want to keep it or reset?
                // Current logic: Just ensure detail pane is visible if it was set?
                // Actually, let's just make sure if we switch TO history, we don't force detail
                // view unless selected
                // For now, minimal intervention.
                if (contentBorderPane.getCenter() == historyDetailPane) {
                    historyDetailPane.setVisible(true);
                }
            }
        });
    }

    private void initCopyMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem duplicateItem = new MenuItem("Duplicate Request");
        duplicateItem.setOnAction(e -> copyLabelAction());

        MenuItem copyUrlItem = new MenuItem("Copy as URL");
        copyUrlItem.setOnAction(e -> copyAsUrlAction());

        MenuItem copyCodeItem = new MenuItem("Copy as Java Code");
        copyCodeItem.setOnAction(e -> copyAsJavaCodeAction());

        contextMenu.getItems().addAll(duplicateItem, copyUrlItem, copyCodeItem);

        copyButton.setOnMouseClicked(event -> contextMenu.show(copyButton, Side.BOTTOM, 0, 0));
    }

    private void copyAsUrlAction() {
        try {
            // Custom Format as requested: Interface, Method, Group, Version, Body
            StringBuilder sb = new StringBuilder();
            sb.append("Interface: ").append(chooseIntComboBox.getText()).append("\n");
            sb.append("Method: ").append(chooseMetComboBox.getText()).append("\n");

            DubboEnvConfig config = null;
            try {
                config = dubboWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
            }
            catch (Exception e) {
                /* ignore */
            }

            String group = config != null ? config.getZkGroup() : "";
            sb.append("Group: ").append(group).append("\n");
            sb.append("Version: ").append(versionField.getText()).append("\n");
            sb.append("Attachments: ").append(attachmentsArea.getText()).append("\n");

            sb.append("Address: ").append(chooseProvideComboBox.getText()).append("\n");
            sb.append("RequestType: ").append(extRequestType.getText()).append("\n");
            sb.append("Timeout: ").append(timeoutField.getText()).append("\n");
            sb.append("Retries: ").append(retriesField.getText()).append("\n");
            sb.append("HookScript: ")
                .append((hookScriptPathField != null && hookScriptPathField.getText() != null)
                    ? hookScriptPathField.getText()
                    : "None")
                .append("\n");

            // Phase 23: TLS Config (Copy as URL)
            sb.append("TlsEnable: ").append(tlsEnableCheckbox.isSelected()).append("\n");
            sb.append("ClientCertPath: ").append(tlsClientCertPathField.getText()).append("\n");
            sb.append("ClientKeyPath: ").append(tlsClientKeyPathField.getText()).append("\n");
            sb.append("ClientKeyPassword: ").append(tlsKeyPasswordField.getText()).append("\n");
            sb.append("CaCertPath: ").append(tlsCaCertPathField.getText()).append("\n");

            sb.append("Body: \n").append(inputTextArea.getText()).append("\n");

            String response = outputTextArea.getText();
            if (StringUtils.isNotEmpty(response)) {
                sb.append("Response: \n").append(response).append("\n");
            }

            ClipboardContent content = new ClipboardContent();
            content.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(content);
            DialogUtil.showSuccessInfo("Request info copied to clipboard");

        }
        catch (Exception e) {
            logger.error("Failed to copy info", e);
            DialogUtil.showErrorInfo("Copy failed: " + e.getMessage());
        }
    }

    private void copyAsJavaCodeAction() {
        try {
            String code = com.opencgl.dubbo.utils.DubboUrlUtils.generateJavaCode(
                chooseProvideComboBox.getText(),
                chooseIntComboBox.getText(),
                chooseMetComboBox.getText(),
                "",
                "");

            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            Clipboard.getSystemClipboard().setContent(content);
            DialogUtil.showSuccessInfo("Java Code copied to clipboard");
        }
        catch (Exception e) {
            logger.error("Failed to copy Java Code", e);
        }
    }

    @FXML
    public void importLabelAction() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(I18N.get("dialog.import.title"));
        dialog.setHeaderText(I18N.get("dialog.import.header"));
        dialog.initStyle(StageStyle.UNDECORATED);
        // 注册到 ThemeManager 以支持主题切换
        dialog.setOnShown(event -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) {
                ThemeManager.getInstance().registerScene(scene);
            }
        });

        // 当对话框关闭时注销
        dialog.setOnHidden(event -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) {
                ThemeManager.getInstance().unregisterScene(scene);
            }
        });

        // 确保应用 root 样式类以支持 CSS 变量
        dialog.getDialogPane().getStyleClass().add("root");

        // Set the button types
        ButtonType importButtonType = new ButtonType("Import", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importButtonType, ButtonType.CANCEL);

        // Create the custom content
        VBox content = new VBox(10);
        Label label = new Label(I18N.get("dialog.import.paste"));
        TextArea textArea = new TextArea();
        textArea.setPromptText(I18N.get("dialog.import.prompt"));
        textArea.setWrapText(true);
        textArea.setPrefRowCount(10);
        textArea.setPrefColumnCount(50);

        content.getChildren().addAll(label, textArea);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == importButtonType) {
                return textArea.getText();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(text -> {
            try {
                if (text.trim().isEmpty())
                    return;

                String contentStr = text.trim();
                if (contentStr.startsWith("dubbo://")) {
                    // Legacy URL import
                    Map<String, String> properties = DubboUrlUtils.parseUrl(contentStr);
                    if (properties.containsKey("interface")) {
                        chooseIntComboBox.setText(properties.get("interface"));
                    }
                    if (properties.containsKey("address")) {
                        chooseProvideComboBox.setText(properties.get("address"));
                    }
                }
                else if (contentStr.contains("Interface:")) {
                    // Custom Format Parse
                    String intf = "";
                    String method = "";
                    String group = "";
                    String version = "";
                    String attachments = "";
                    String address = "";
                    String requestType = "";
                    String timeout = "";
                    String retries = "";
                    String hookScript = "";

                    // TLS variables
                    String tlsEnable = "";
                    String clientCert = "";
                    String clientKey = "";
                    String clientKeyPass = "";
                    String caCert = "";

                    StringBuilder body = new StringBuilder();
                    StringBuilder response = new StringBuilder();

                    String[] lines = contentStr.split("\n");
                    int readingState = 0; // 0: Meta, 1: Body, 2: Response

                    for (String line : lines) {
                        String trimmedLine = line.trim();
                        if (trimmedLine.startsWith("Response:") || trimmedLine.startsWith("ResponseBody:")
                            || trimmedLine.startsWith("Response body:")) {
                            readingState = 2;
                            continue;
                        }
                        else if (trimmedLine.startsWith("Body:") || trimmedLine.startsWith("RequestBody:")) {
                            readingState = 1;
                            continue;
                        }

                        if (readingState == 1) {
                            body.append(line).append("\n");
                            continue;
                        }
                        else if (readingState == 2) {
                            response.append(line).append("\n");
                            continue;
                        }

                        // Meta parsing
                        if (trimmedLine.startsWith("Interface:")) {
                            intf = trimmedLine.substring("Interface:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("Method:")) {
                            method = trimmedLine.substring("Method:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("Group:")) {
                            group = trimmedLine.substring("Group:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("Address:")) {
                            address = trimmedLine.substring("Address:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("Version:")) {
                            version = trimmedLine.substring("Version:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("Attachments:")) {
                            attachments = trimmedLine.substring("Attachments:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("RequestType:")) {
                            requestType = trimmedLine.substring("RequestType:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("Timeout:")) {
                            timeout = trimmedLine.substring("Timeout:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("Retries:")) {
                            retries = trimmedLine.substring("Retries:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("HookScript:")) {
                            hookScript = trimmedLine.substring("HookScript:".length()).trim();
                            if ("None".equalsIgnoreCase(hookScript))
                                hookScript = "";
                        }
                        // Phase 23: TLS Parsing
                        else if (trimmedLine.startsWith("TlsEnable:")) {
                            tlsEnable = trimmedLine.substring("TlsEnable:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("ClientCertPath:")) {
                            clientCert = trimmedLine.substring("ClientCertPath:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("ClientKeyPath:")) {
                            clientKey = trimmedLine.substring("ClientKeyPath:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("ClientKeyPassword:")) {
                            clientKeyPass = trimmedLine.substring("ClientKeyPassword:".length()).trim();
                        }
                        else if (trimmedLine.startsWith("CaCertPath:")) {
                            caCert = trimmedLine.substring("CaCertPath:".length()).trim();
                        }
                    }

                    if (!StringUtils.isEmpty(intf))
                        chooseIntComboBox.setText(intf);
                    if (!StringUtils.isEmpty(method))
                        chooseMetComboBox.setText(method);
                    if (!StringUtils.isEmpty(address))
                        chooseProvideComboBox.setText(address);
                    if (!StringUtils.isEmpty(version))
                        versionField.setText(version);
                    if (!StringUtils.isEmpty(attachments))
                        attachmentsArea.setText(attachments);
                    if (!StringUtils.isEmpty(requestType))
                        extRequestType.setText(requestType);
                    if (!StringUtils.isEmpty(timeout))
                        timeoutField.setText(timeout);
                    if (!StringUtils.isEmpty(retries))
                        retriesField.setText(retries);
                    if (!StringUtils.isEmpty(hookScript)) {
                        if (hookScriptPathField != null)
                            hookScriptPathField.setText(hookScript);
                    }

                    // Phase 23: Set TLS Config (Import)
                    if (StringUtils.isNotEmpty(tlsEnable)) {
                        tlsEnableCheckbox.setSelected(Boolean.parseBoolean(tlsEnable));
                    }
                    if (StringUtils.isNotEmpty(clientCert))
                        tlsClientCertPathField.setText(clientCert);
                    if (StringUtils.isNotEmpty(clientKey))
                        tlsClientKeyPathField.setText(clientKey);
                    if (StringUtils.isNotEmpty(clientKeyPass))
                        tlsKeyPasswordField.setText(clientKeyPass);
                    if (StringUtils.isNotEmpty(caCert))
                        tlsCaCertPathField.setText(caCert);
                    if (!body.isEmpty())
                        inputTextArea.setText(body.toString().trim());
                    if (!response.isEmpty()) {
                        outputTextArea.setText(response.toString().trim());
                        // Switch to output tab if possible? Or just set text.
                        // Assuming user will click to see it.
                    }

                }
                else {
                    DialogUtil.showErrorInfo("Unknown format. Please use 'dubbo://' or 'Interface:...' format.");
                    return;
                }

                DialogUtil.showSuccessInfo("Import Successful");
            }
            catch (Exception e) {
                logger.error("Import failed", e);
                DialogUtil.showErrorInfo("Import Failed: " + e.getMessage());
            }
        });
    }

    private void restoreHistory(DubboHistoryItem item) {
        if (item == null)
            return;

        // Restore Environment
        if (item.getEnvName() != null) {
            this.chooseEnvComboBox.setText(item.getEnvName());
        }

        // Restore Interface & Method
        this.chooseIntComboBox.setText(item.getInterfaceName());
        this.chooseMetComboBox.setText(item.getMethodName());

        // Restore Input Body
        if (item.getInputText() != null) {
            this.inputTextArea.setText(item.getInputText());
            // Trigger syntax highlighting or formatting if needed?
            // CustomTextArea usually handles text changes.
        }

        // Restore Request Type if we saved it (we didn't explicitly in sendLabelAction
        // above, but DubboHistoryItem has the field)
        if (item.getRequestType() != null) {
            this.extRequestType.setText(item.getRequestType());
        }

        // Restore Version & Attachments
        if (item.getVersion() != null) {
            this.versionField.setText(item.getVersion());
        }
        else {
            this.versionField.setText("");
        }

        if (StringUtils.isNotEmpty(item.getAttachments())) { // Assuming item is the dubboTreeItem here
            this.attachmentsArea.setText(item.getAttachments());
        }
        else {
            this.attachmentsArea.setText("");
        }

        // Restore Timeout & Retries
        if (item.getTimeout() != null) {
            this.timeoutField.setText(String.valueOf(item.getTimeout()));
        }
        else {
            this.timeoutField.setText("100000"); // Default 100000
        }

        if (item.getRetries() != null) {
            this.retriesField.setText(String.valueOf(item.getRetries()));
        }
        else {
            this.retriesField.setText("0");
        }

        // Restore Provider URL (stored in Address)
        if (item.getAddress() != null) {
            this.chooseProvideComboBox.setText(item.getAddress());
        }

        // Phase 23: Restore TLS Config from History
        tlsEnableCheckbox.setSelected(Boolean.TRUE.equals(item.getTlsEnable()));
        tlsClientCertPathField
            .setText(StringUtils.isNotEmpty(item.getClientCertPath()) ? item.getClientCertPath() : "");
        tlsClientKeyPathField.setText(StringUtils.isNotEmpty(item.getClientKeyPath()) ? item.getClientKeyPath() : "");
        tlsKeyPasswordField
            .setText(StringUtils.isNotEmpty(item.getClientKeyPassword()) ? item.getClientKeyPassword() : "");
        tlsCaCertPathField.setText(StringUtils.isNotEmpty(item.getCaCertPath()) ? item.getCaCertPath() : "");

        // Switch to Collection/Request Tab ?
        // Or stay in History? Usually user wants to send, so switching to Request Body
        // tab (Tab 0) is good.
        // But the main TabPane (Collections vs History) is separate from Request
        // Configuration TabPane.
        // The Request Configuration TabPane is inside splitPane.
        // I should probably ensure the "Collection" tab is selected? No, "History" tab
        // is where they double clicked.
        // They can modify right there.
        // But the Request Config is VISIBLE on the right/bottom whatever.
        // In my new layout: Left is TabPane(Collection, History). Right is
        // SplitPane(Request Config, Response).
        // So clicking history on left updates right. Perfect.
    }

    /**
     * 初始化 Hook 脚本组件
     */
    /**
     * 初始化 Hook 脚本组件
     */
    private void initHookScripts() {
        scriptEngineManager = new ScriptEngineManager();

        // Manual UI construction removed as FXML is now updated.
        // The following controls are injected via FXML:
        // hookScriptPathField, browseHookButton, saveHookButton

        // Ensure path field listener
        if (hookScriptPathField != null) {
            hookScriptPathField.textProperty()
                .addListener((observable, oldValue, newValue) -> onHookScriptPathChanged(newValue));
        }

        // Bind Browse Button
        if (browseHookButton != null) {
            MFXFontIcon folderIcon = new MFXFontIcon("fas-folder-open");
            browseHookButton.setGraphic(folderIcon);
            browseHookButton.setText(""); // clear text
            browseHookButton.setOnAction(e -> browseHookAction());
            Tooltip browseTip = new Tooltip();
            browseTip.textProperty().bind(I18N.getBinding("tooltip.browse_script"));
            browseHookButton.setTooltip(browseTip);
        }

        // Bind Save Button
        if (saveHookButton != null) {
            MFXFontIcon saveIcon = new MFXFontIcon("fas-floppy-disk");
            saveHookButton.setGraphic(saveIcon);
            saveHookButton.setText(""); // clear text
            saveHookButton.setOnAction(e -> saveHookAction());
            Tooltip saveTip = new Tooltip();
            saveTip.textProperty().bind(I18N.getBinding("tooltip.save_script"));
            saveHookButton.setTooltip(saveTip);
        }
    }

    private void browseHookAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("dialog.select_hook"));
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Groovy Scripts", "*.groovy"),
            new FileChooser.ExtensionFilter("All Files", "*.*"));

        // Set initial directory if current path exists
        String currentPath = hookScriptPathField.getText();
        if (StringUtils.isNotEmpty(currentPath)) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                if (currentFile.isDirectory()) {
                    fileChooser.setInitialDirectory(currentFile);
                }
                else {
                    fileChooser.setInitialDirectory(currentFile.getParentFile());
                }
            }
        }
        else {
            // Default to ~/.opencgl/hooks if exists
            Path defaultHooks = Paths.get(System.getProperty("user.home"), ".opencgl", "hooks");
            if (Files.exists(defaultHooks)) {
                fileChooser.setInitialDirectory(defaultHooks.toFile());
            }
        }

        File selectedFile = fileChooser.showOpenDialog(mainStackPane.getScene().getWindow());
        if (selectedFile != null) {
            hookScriptPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void saveHookAction() {
        String currentPath = hookScriptPathField.getText();
        if (StringUtils.isEmpty(currentPath)) {
            DialogUtil.showErrorInfo("Please select a file path first.");
            return;
        }

        String content = hookPreviewTextArea.getText();
        if (content == null) {
            content = "";
        }

        try {
            Path path = Paths.get(currentPath);
            // Verify parent exists or create? Ideally we are editing existing file, or new
            // one?
            // If path doesn't exist, we might be creating new one.
            if (!Files.exists(path)) {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
            }
            Files.writeString(path, content);
            DialogUtil.showSuccessInfo("Script saved successfully.");

            // Reload logic is handled by onHookScriptPathChanged re-triggering or manual
            // reload?
            // Ideally we force reload.
            onHookScriptPathChanged(currentPath);

        }
        catch (Exception e) {
            logger.error("Failed to save hook script", e);
            DialogUtil.showErrorInfo("Failed to save script: " + e.getMessage());
        }
    }

    /**
     * Hook 脚本路径变化事件
     */
    private void onHookScriptPathChanged(String pathStr) {
        if (StringUtils.isEmpty(pathStr)) {
            currentHook = null;
            if (hookPreviewTextArea != null)
                hookPreviewTextArea.setText("");
            return;
        }
        try {
            java.nio.file.Path scriptPath = Paths.get(pathStr);

            // If absolute path doesn't exist, try resolving against default hooks dir for
            // backward compatibility
            if (!Files.exists(scriptPath)) {
                Path relativePath = Paths.get(
                    System.getProperty("user.home"), ".opencgl", "hooks", pathStr);
                if (Files.exists(relativePath)) {
                    scriptPath = relativePath;
                }
            }

            if (Files.exists(scriptPath)) {
                currentHook = scriptEngineManager.loadScript(scriptPath);

                // Preview Script Content
                if (hookPreviewTextArea != null) {
                    try {
                        String content = Files.readString(scriptPath);
                        // Avoid triggering loop if we update text? No, text area doesn't trigger path
                        // change.
                        hookPreviewTextArea.setText(content);
                    }
                    catch (Exception e) {
                        hookPreviewTextArea.setText("// Failed to load script content: " + e.getMessage());
                    }
                }
                logger.info("已加载 Hook 脚本: {}", scriptPath);
            }
            else {
                // File not found
                hookPreviewTextArea.setText("// Script file not found: " + pathStr);
                currentHook = null;
            }
        }
        catch (Exception ex) {
            logger.error("加载 Hook 脚本失败", ex);
            // DialogUtil.showErrorInfo("加载 Hook 脚本失败: " + ex.getMessage()); // Don't popup
            // on every keystroke
            if (hookPreviewTextArea != null)
                hookPreviewTextArea.setText("// Failed to load script: " + ex.getMessage());
            currentHook = null;
        }
    }

    public void init() {
        initStyle();
        chooseEnvComboBox.setOnMouseClicked(event -> {
            try {
                List<DubboEnvConfig> configs = dubboWidgetDao.queryAllEnvConfig();
                List<String> names = configs.stream().map(DubboEnvConfig::getEnvName).collect(Collectors.toList());
                chooseEnvComboBox.setItems(FXCollections.observableArrayList(names));
            }
            catch (Exception e) {
                logger.error("", e);
            }
        });

        // noinspection unchecked,rawtypes
        chooseEnvComboBox.getSelectionModel().selectedItemProperty()
            .addListener((ChangeListener) (observable, oldValue, newValue) -> {
                if (null == newValue) {
                    return;
                }
                chooseIntComboBox.getItems().clear();
                chooseMetComboBox.getItems().clear();
                try {
                    DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
                    if (config != null && !StringUtils.isEmpty(config.getServiceData())) {
                        List<String> allContent = JSON.parseArray(config.getServiceData(), String.class);
                        List<String> infs = new ArrayList<>();
                        for (String line : allContent) {
                            infs.add(line.split(",")[0]);
                        }
                        chooseIntComboBox.setItems(FXCollections
                            .observableArrayList(infs.stream().distinct().collect(Collectors.toList())));
                    }
                }
                catch (Exception ex) {
                    logger.error("", ex);
                }
            });

        // noinspection unchecked,rawtypes
        chooseIntComboBox.getSelectionModel().selectedItemProperty()
            .addListener((ChangeListener) (observable, oldValue, newValue) -> {
                if (newValue == null) {
                    return;
                }
                chooseMetComboBox.getItems().clear();
                try {
                    DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
                    if (config != null && !StringUtils.isEmpty(config.getServiceData())) {
                        List<String> allContent = JSON.parseArray(config.getServiceData(), String.class);
                        List<String> methods = new ArrayList<>();
                        for (String line : allContent) {
                            if (line.split(",")[0].equals(chooseIntComboBox.getText())
                                && line.split(",")[1].equals(chooseMetComboBox.getText())) {
                                methods.add(line.split(",")[1]);
                            }
                        }
                        chooseMetComboBox.setItems(FXCollections
                            .observableArrayList(methods.stream().distinct().collect(Collectors.toList())));
                    }
                }
                catch (Exception ex) {
                    logger.error("", ex);
                }
            });

        chooseMetComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (null == newValue) {
                return;
            }
            chooseProvideComboBox.getItems().clear();
            logger.info("方法事件监控,方法选择已选中 {}", chooseMetComboBox.getText());

            try {
                DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
                if (config == null)
                    return;

                String apiPath = config.getApiPackagePath();
                if (StringUtils.isEmpty(apiPath)) {
                    return;
                }
                String[] jarFile = apiPath.split(",");
                String requestType = "";

                if (!StringUtils.isEmpty(config.getServiceData())) {
                    List<String> allContent = JSON.parseArray(config.getServiceData(), String.class);
                    for (String line : allContent) {
                        if (line.split(",")[0].equals(chooseIntComboBox.getText())
                            && line.split(",")[1].equals(chooseMetComboBox.getText())) {
                            requestType = line.split(",")[2];
                        }
                    }
                }

                if (StringUtils.isEmpty(requestType)) {
                    return;
                }
                if (requestType.startsWith("java")) {
                    inputTextArea.setText(requestType);
                    return;
                }
                inputTextArea.setText(DubboConfigFileParseUtil.jsonMessage(jarFile, requestType));
            }
            catch (Exception e) {
                logger.error("", e);
            }
        });

        chooseProvideComboBox.setOnMouseClicked(event -> {
            chooseProvideComboBox.getItems().clear();
            if (StringUtils.isEmpty(chooseIntComboBox.getText())) {
                return;
            }
            submitAsync(() -> {
                try {
                    DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
                    if (config != null) {
                        String zkAddress = config.getRegistryAddress();
                        String dubboGroup = config.getZkGroup();
                        runOnUi(() -> LoadingUtil.show(contentBorderPane));
                        List<String> providers = DubboConfigFileParseUtil
                            .getProviders("/" + dubboGroup + "/" + chooseIntComboBox.getText(), zkAddress);
                        runOnUi(
                            () -> chooseProvideComboBox.setItems(FXCollections.observableArrayList(providers)));
                        chooseProvideComboBox.getItems().sorted();
                    }
                }
                catch (Throwable e) {
                    logger.error("", e);
                    runOnUi(() -> DialogUtil.showErrorInfo(e.getMessage()));
                }
                finally {
                    runOnUi(() -> LoadingUtil.remove(contentBorderPane));
                }
            });
        });

        initHookScripts();
    }

    private void onHistorySelected(DubboHistoryItem item) {
        if (item == null)
            return;

        // Format Details
        StringBuilder sb = new StringBuilder();
        appendDetail(sb, "Interface", item.getInterfaceName());
        appendDetail(sb, "Method", item.getMethodName());
        appendDetail(sb, "Group", item.getGroup());
        appendDetail(sb, "Version", item.getVersion());
        appendDetail(sb, "Address", item.getAddress());
        appendDetail(sb, "RequestType", item.getRequestType());
        appendDetail(sb, "Timeout", item.getTimeout());
        appendDetail(sb, "Retries", item.getRetries());
        appendDetail(sb, "HookScript", item.getHookScript());
        appendDetail(sb, "Attachments", item.getAttachments());
        appendDetail(sb, "TlsEnable", item.getTlsEnable());
        appendDetail(sb, "ClientCertPath", item.getClientCertPath());
        appendDetail(sb, "ClientKeyPath", item.getClientKeyPath());
        appendDetail(sb, "ClientKeyPassword", item.getClientKeyPassword());
        appendDetail(sb, "CaCertPath", item.getCaCertPath());
        appendDetail(sb, "Body", item.getInputText());
        appendDetail(sb, "ResponseBody", item.getOutputText());

        historyFullTextArea.setText(sb.toString());

        // Switch View
        if (contentBorderPane != null && historyDetailPane != null) {
            contentBorderPane.setTop(null);
            contentBorderPane.setCenter(historyDetailPane);
            historyDetailPane.setVisible(true);
        }
    }

    private void appendDetail(StringBuilder sb, String key, Object value) {
        sb.append(key).append(": ").append(value != null ? value : "").append("\n");
    }

    private void initStyle() {
        MFXFontIcon sendButtonIcon = new MFXFontIcon("fas-play");
        sendButton.setGraphic(sendButtonIcon);
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        // Ctrl+Enter 发送请求
        KeyCodeCombination sendShortcut = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);
        // Ctrl+S 保存
        KeyCodeCombination saveShortcut = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
        // Ctrl+R 刷新
        KeyCodeCombination refreshShortcut = new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN);
        // F5 刷新
        KeyCodeCombination f5Shortcut = new KeyCodeCombination(KeyCode.F5);
        // Ctrl+D 复制当前项
        KeyCodeCombination copyShortcut = new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN);

        mainStackPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (sendShortcut.match(event)) {
                sendLabelAction();
                event.consume();
            }
            else if (saveShortcut.match(event)) {
                saveLabelAction();
                event.consume();
            }
            else if (refreshShortcut.match(event) || f5Shortcut.match(event)) {
                refreshLabelAction();
                event.consume();
            }
            else if (copyShortcut.match(event)) {
                copyLabelAction();
                event.consume();
            }
        });
    }

    @FXML
    public void sendLabelAction() {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            if (StringUtils.isEmpty(chooseIntComboBox.getText())
                || StringUtils.isEmpty(chooseMetComboBox.getText())) {
                DialogUtil.showErrorInfo("环境/接口/方法等关键信息不能为空!");
                return;
            }
            String envName = chooseEnvComboBox.getText();
            DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(envName);

            if (config == null
                || StringUtils.isEmpty(config.getRegistryAddress())) {
                DialogUtil.showErrorInfo("环境配置文件不存在，或服务注册地址为空!");
                return;
            }
            DubboInfo dubboInfo = DubboInfo.builder()
                .zk(config.getRegistryAddress())
                .group(config.getZkGroup() != null ? config.getZkGroup() : "")
                .build();

            String requestType = "";
            if (!StringUtils.isEmpty(extRequestType.getText())) {
                requestType = extRequestType.getText();
            }
            else {
                List<String> allContent;
                try {
                    if (!StringUtils.isEmpty(config.getServiceData())) {
                        allContent = JSON.parseArray(config.getServiceData(), String.class);
                        for (String line : allContent) {
                            if (line.split(",")[0].equals(chooseIntComboBox.getText())
                                && line.split(",")[1].equals(chooseMetComboBox.getText())) {
                                requestType = line.split(",")[2];
                            }
                        }
                    }
                }
                catch (Exception e) {
                    logger.error("", e);
                }
            }

            // Hook 上下文构建
            HookContext hookContext = new HookContext();
            hookContext.setInterfaceName(chooseIntComboBox.getText());
            hookContext.setMethodName(chooseMetComboBox.getText());
            hookContext.setEnvironment(chooseEnvComboBox.getText());
            hookContext.setProtocol("dubbo");
            hookContext.put("log", org.slf4j.LoggerFactory.getLogger("HookScript"));
            hookContext.put("history", com.opencgl.base.utils.OperationHisRecord.class);

            // 获取请求次数
            int requestCount = 1;
            try {
                String countText = requestCountField.getText();
                if (countText != null && !countText.trim().isEmpty()) {
                    requestCount = Integer.parseInt(countText.trim());
                    if (requestCount <= 0) {
                        requestCount = 1;
                    }
                }
            }
            catch (NumberFormatException e) {
                logger.warn("请求次数格式错误,使用默认值1", e);
            }

            AtomicReference<String> outResult = new AtomicReference<>("");
            final RequestHook hookRef = currentHook;
            final HookContext ctxRef = hookContext;
            final int finalRequestCount = requestCount;
            final String finalRegistryAddr = config.getRegistryAddress();
            final String finalRegistryGroup = config.getZkGroup() != null ? config.getZkGroup() : "";
            final String finalAttachments = attachmentsArea.getText();

            final AtomicBoolean hasError = new AtomicBoolean(false); // Declared here for visibility in finally block
            final AtomicReference<String> finalCapturedRequest = new AtomicReference<>(null);

            String finalRequestType = requestType;
            // Capture text BEFORE implicit background thread (UI Thread)
            final String baseInputText = inputTextArea.getNonAnnotationText();

            // Phase 17: TLS Config
            TlsConfig tlsConfig = TlsConfig.builder()
                .enabled(tlsEnableCheckbox.isSelected())
                // .mutualAuth(tlsMutualAuthCheckbox.isSelected()) // Removed
                .clientCertPath(tlsClientCertPathField.getText())
                .clientKeyPath(tlsClientKeyPathField.getText())
                .clientKeyPassword(tlsKeyPasswordField.getText())
                .caCertPath(tlsCaCertPathField.getText())
                .build();

            submitAsync(() -> {
                // 清空输出并显示加载中
                runOnUi(() -> {
                    outputTextArea.setText(""); // Clear previous output
                    // inputTextArea.setText(""); // Removed to prevent clearing body
                    // 设置默认值
                    if (requestCountField.getText() == null || requestCountField.getText().isEmpty()) {
                        requestCountField.setText("1");
                    }
                    if (timeoutField.getText() == null || timeoutField.getText().isEmpty()) {
                        timeoutField.setText("100000");
                    }
                    if (retriesField.getText() == null || retriesField.getText().isEmpty()) {
                        retriesField.setText("0");
                    } // 清空旧结果
                    LoadingUtil.show(contentInputAndOutputPane);
                    // Switch to Body Tab (contains Output)
                    requestTabPane.getSelectionModel().select(requestBodyTab);
                });

                try {
                    Thread.currentThread().setContextClassLoader(pluginCl);

                    // 批量发送请求（复用连接），使用流式发送 (Lazy Generation)
                    // 第一个请求用于初始化连接配置
                    DubboRequest firstRequest = DubboRequest.builder()
                        .dubboRegistryAddr(dubboInfo.getZk())
                        .dubboRegistryGroup(dubboInfo.getGroup())
                        .dubboProvidersUrl(chooseProvideComboBox.getText())
                        .interfaceName(chooseIntComboBox.getText())
                        .tlsConfig(tlsConfig) // Phase 17: Set TLS Config
                        .method(chooseMetComboBox.getText())
                        .reqType(finalRequestType)
                        .reqJsonMessage(baseInputText) // Use captured base text
                        .settingTimeout(Integer.parseInt(timeoutField.getText()))
                        .retries(Integer.parseInt(retriesField.getText()))
                        .version(versionField.getText())
                        .dubboGroup(dubboGroupField.getText())
                        .attachments(parseAttachments(attachmentsArea.getText()))
                        .build();

                    DubboUtil dubboUtil = new DubboUtil(firstRequest);

                    // 使用 sendStream 替代 sendBatch
                    dubboUtil.sendStream(finalRequestCount, (index) -> {
                        // 【请求生成器】惰性生成，避免 OOM
                        try {
                            // 1. 变量格式化
                            String formattedJson = FormatVariableUtil.format(baseInputText);
                            String processedRequestJson = formattedJson;

                            // 2. Hook 预处理
                            if (hookRef != null) {
                                try {
                                    logger.info("========== Hook 预处理开始 (请求 {}/{}) ==========", index + 1,
                                        finalRequestCount);
                                    processedRequestJson = hookRef.preProcess(processedRequestJson, ctxRef);
                                    if (!formattedJson.equals(processedRequestJson)) {
                                        logger.info("请求已被 Hook 修改");
                                    }
                                    logger.info("========== Hook 预处理完成 ==========");
                                }
                                catch (Exception e) {
                                    logger.error("Hook 预处理失败 (请求 " + (index + 1) + "/" + finalRequestCount + "): " + e.getMessage(), e);
                                }
                            }

                            // Capture history from first request
                            if (index == 0) {
                                finalCapturedRequest.set(processedRequestJson);
                            }

                            // 3. 构建请求对象
                            return DubboRequest.builder()
                                .dubboRegistryAddr(dubboInfo.getZk())
                                .dubboRegistryGroup(dubboInfo.getGroup())
                                .dubboProvidersUrl(chooseProvideComboBox.getText())
                                .interfaceName(chooseIntComboBox.getText())
                                .method(chooseMetComboBox.getText())
                                .tlsConfig(tlsConfig) // Phase 17: Set TLS Config for streaming requests
                                .reqType(finalRequestType)
                                .reqJsonMessage(processedRequestJson)
                                .settingTimeout(Integer.parseInt(timeoutField.getText()))
                                .retries(Integer.parseInt(retriesField.getText()))
                                .version(versionField.getText())
                                .dubboGroup(dubboGroupField.getText())
                                .attachments(parseAttachments(attachmentsArea.getText()))
                                .build();
                        }
                        catch (Exception e) {
                            logger.error("请求生成失败", e);
                            return null;
                        }

                    }, (indexedResult) -> {
                        // 【进度回调】处理单个结果
                        try {
                            Integer reqIndex = (Integer) indexedResult.get("requestIndex");
                            Integer totalReqs = (Integer) indexedResult.get("totalRequests");

                            StringBuilder resultTextBuilder = new StringBuilder();
                            String singleResult = null;

                            // Hook 后处理
                            if (!indexedResult.containsKey("error")) {
                                Object result = indexedResult.get("result");
                                if (result == null) {
                                    singleResult = "void (无返回值 / null)";
                                }
                                else if (result instanceof String || result instanceof Number || result instanceof Boolean) {
                                    singleResult = String.valueOf(result);
                                }
                                else {
                                    try {
                                        singleResult = JSON.toJSONString(result, SerializerFeature.PrettyFormat,
                                            SerializerFeature.WriteDateUseDateFormat);
                                    }
                                    catch (Exception ex) {
                                        singleResult = String.valueOf(result);
                                    }
                                }

                                if (hookRef != null) {
                                    try {
                                        logger.info("========== Hook 后处理开始 (响应 {}/{}) ==========", reqIndex, totalReqs);
                                        singleResult = hookRef.postProcess(singleResult, ctxRef);
                                        logger.info("========== Hook 后处理完成 ==========");
                                    }
                                    catch (Exception e) {
                                        logger.error("Hook 后处理失败 (响应 " + reqIndex + "/" + totalReqs + "): " + e.getMessage(), e);
                                    }
                                }
                            }

                            // 分隔符
                            if (reqIndex > 1) {
                                resultTextBuilder.append("\n\n").append(StringUtils.repeat("-", 50)).append("\n");
                                resultTextBuilder.append("Request #").append(reqIndex).append("\n");
                                resultTextBuilder.append(StringUtils.repeat("-", 50)).append("\n\n");
                            }

                            if (hasError.get()) {
                                return;
                            }

                            if (indexedResult.containsKey("error")) {
                                hasError.set(true);
                                resultTextBuilder.append("请求失败:\n");
                                resultTextBuilder.append("错误: ").append(indexedResult.get("error")).append("\n");
                                resultTextBuilder.append("类型: ").append(indexedResult.get("errorClass"));

                                if (totalReqs > 1) {
                                    int remainingCount = totalReqs - reqIndex;
                                    if (remainingCount > 0) {
                                        resultTextBuilder.append("\n⚠️  批量请求已终止，剩余 ").append(remainingCount)
                                            .append(" 个请求未执行。\n");
                                    }
                                }
                            }
                            else {
                                resultTextBuilder.append(singleResult);
                            }

                            String finalText = resultTextBuilder.toString();

                            // Create final copies for lambda capture
                            final String finalSingleResult = singleResult;
                            final Integer finalReqIndex = reqIndex;
                            final Integer finalTotalReqs = totalReqs;

                            // 实时更新 UI
                            runOnUi(() -> {
                                // Update Status Bar
                                if (indexedResult.containsKey("elapsedTime")) {
                                    timeLabel.setText(I18N.get("label.time", indexedResult.get("elapsedTime")));
                                }
                                if (finalSingleResult != null) {
                                    // Estimate size in bytes (UTF-8)
                                    int size = finalSingleResult
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                                    if (size < 1024) {
                                        sizeLabel.setText(I18N.get("label.size", size + " B"));
                                    }
                                    else {
                                        sizeLabel.setText(I18N.get("label.size", String.format("%.2f KB", size / 1024.0)));
                                    }
                                }
                                if (indexedResult.containsKey("error")) {
                                    statusLabel.setText(I18N.get("msg.status_fail"));
                                    statusLabel.setStyle("-fx-text-fill: red;");
                                }
                                else {
                                    statusLabel.setText(I18N.get("msg.status_success"));
                                    statusLabel.setStyle("-fx-text-fill: #3daea5;"); // Success Green
                                }

                                // 限制最大长度，避免 UI 卡死
                                int currentLength = outputTextArea.getLength();
                                if (currentLength > 500000) { // 500KB limit
                                    outputTextArea.replaceText(0, currentLength,
                                        "Log truncated due to size limit...\n");
                                }
                                outputTextArea.appendText(finalText);
                                outputTextArea.requestFollowCaret();

                                // Close loading mask immediately after last request result is shown
                                if (finalReqIndex.intValue() == finalTotalReqs.intValue()) {
                                    LoadingUtil.remove(contentInputAndOutputPane);
                                }
                            });

                        }
                        catch (Exception e) {
                            logger.error("处理响应失败", e);
                        }
                    });

                    logger.info("所有请求已完成");
                }
                catch (Throwable e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw, true));
                    logger.error("", e);

                    // 显示错误到 UI
                    final String errorMessage = "请求失败:\n" + sw;
                    runOnUi(() -> {
                        outputTextArea.setText(errorMessage);
                    });
                }
                finally {
                    runOnUi(() -> LoadingUtil.remove(contentInputAndOutputPane));

                    // 记录第一个请求的内容作为示例
                    String sampleInput = inputTextArea.getNonAnnotationText();
                    if (finalRequestCount > 1) {
                        sampleInput = "批量请求 x" + finalRequestCount + "，首个请求:\n" + sampleInput;
                    }

                    OperationHisRecord.record("SEND DUBBO MESSAGE:",
                        "环境名称:" + chooseEnvComboBox.getText(),
                        "zk地址:" + finalRegistryAddr,
                        "dubbo分组:" + finalRegistryGroup,
                        "接口:" + chooseIntComboBox.getText(),
                        "方法:" + chooseMetComboBox.getText(),
                        "",
                        "消息体:",
                        sampleInput);

                    // Save structured history
                    try {
                        String finalHookScript = hookScriptPathField != null ? hookScriptPathField.getText() : null;
                        String historyInputText = finalCapturedRequest.get() != null ? finalCapturedRequest.get()
                            : inputTextArea.getNonAnnotationText();

                        // Try to format if it's JSON
                        try {
                            if (historyInputText != null && (historyInputText.trim().startsWith("{")
                                || historyInputText.trim().startsWith("["))) {
                                Object json = JSON.parse(historyInputText);
                                historyInputText = JSON.toJSONString(json, SerializerFeature.PrettyFormat,
                                    SerializerFeature.WriteDateUseDateFormat);
                            }
                        }
                        catch (Exception ignore) {
                        }

                        DubboHistoryItem historyItem = new DubboHistoryItem(
                            UUID.randomUUID().toString(),
                            new Date(),
                            // status: "SUCCESS" or "FAIL" ? We don't know yet fully, but let's assume
                            // triggered.
                            // Actually, HTTP Debugger saves it immediately.
                            hasError.get() ? "FAIL" : "SUCCESS",
                            chooseIntComboBox.getText() + "#" + chooseMetComboBox.getText(),
                            chooseIntComboBox.getText(),
                            chooseMetComboBox.getText(),
                            chooseProvideComboBox.getText(), // Use Provider URL instead of ZK Address
                            chooseEnvComboBox.getText(),
                            historyInputText, // InputText (processed)
                            finalRegistryGroup, // Group
                            dubboGroupField.getText(), // dubboGroup
                            versionField.getText(), // Version
                            finalRequestType, // RequestType
                            Integer.parseInt(timeoutField.getText()), // Timeout
                            Integer.parseInt(retriesField.getText()), // Retries
                            finalHookScript, // HookScript
                            finalAttachments, // Attachments
                            tlsConfig.isEnabled(), // TLS Enable
                            tlsConfig.getClientCertPath(), // Client Cert
                            tlsConfig.getClientKeyPath(), // Client Key
                            tlsConfig.getClientKeyPassword(), // Key Password
                            tlsConfig.getCaCertPath() // CA Cert
                        );

                        // Use the FINAL output text from the text area, which contains the formatted
                        // results
                        String capturedOutput = outputTextArea.getText();
                        historyItem.setOutputText(capturedOutput);

                        dubboWidgetDao.insertHistory(historyItem);

                        runOnUi(() -> {
                            // Check if History Tab is selected (Tab index 1)
                            // We need to access tabs from requestManager (if we want to optimize)
                            // BUT effectively we just want to refresh history if it's visible OR always.
                            // Refreshing memory list is cheap.
                            if (historyViewBuilder != null) {
                                historyViewBuilder.refresh();
                            }
                        });
                    }
                    catch (Exception e) {
                        logger.error("Failed to save history", e);
                    }
                }
            });
        }
        catch (Exception e) {
            logger.error("", e);
        }
        finally {
            Thread.currentThread().setContextClassLoader(old);
        }

    }

    @FXML
    public void refreshLabelAction() {
        DubboTreeItem dubboTreeItem = dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getValue();
        try {
            dubboTreeItem = dubboWidgetDao.queryData(dubboTreeItem);
            chooseEnvComboBox.setText(dubboTreeItem.getEnvName());
            chooseIntComboBox.setText(dubboTreeItem.getInterfaceInfo());
            chooseMetComboBox.setText(dubboTreeItem.getMethodInfo());

            // 恢复请求次数
            if (dubboTreeItem.getRequestCount() != null) {
                requestCountField.setText(dubboTreeItem.getRequestCount().toString());
            }
            else {
                requestCountField.setText("1");
            }

            // 恢复 Version & Attachments & Dubbo分组
            versionField.setText(dubboTreeItem.getVersion());
            dubboGroupField.setText(StringUtils.defaultString(dubboTreeItem.getDubboGroup()));
            attachmentsArea.setText(dubboTreeItem.getAttachments());

            // 恢复扩展模式状态 -> 恢复高级设置
            if (Boolean.TRUE.equals(dubboTreeItem.getIsSelected())) {
                // extTitledPane.setExpanded(true); // TitledPane removed
                extRequestType.setText(dubboTreeItem.getRequestType());

                // 恢复 Hook 脚本
                String savedHookScript = dubboTreeItem.getHookScript();
                if (savedHookScript != null && !savedHookScript.isEmpty()) {
                    if (hookScriptPathField != null)
                        hookScriptPathField.setText(savedHookScript);
                }
                else {
                    if (hookScriptPathField != null)
                        hookScriptPathField.setText("");
                }
            }
            else {
                extRequestType.clear();
                if (hookScriptPathField != null)
                    hookScriptPathField.setText("");
            }
            inputTextArea.setText(dubboTreeItem.getInputText());
        }
        catch (Exception e) {
            logger.error("", e);
        }
    }

    @FXML
    public void saveLabelAction() {
        DubboTreeItem dubboTreeItem = dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getValue();
        dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
        dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
        dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());

        dubboTreeItem.setIsSelected(true); // Always save settings, extTitledPane is removed

        dubboTreeItem.setRequestType(extRequestType.getText());
        dubboTreeItem.setInputText(inputTextArea.getText());

        // 保存请求次数
        try {
            String countText = requestCountField.getText();
            if (countText != null && !countText.trim().isEmpty()) {
                dubboTreeItem.setRequestCount(Integer.parseInt(countText.trim()));
            }
            else {
                dubboTreeItem.setRequestCount(1);
            }
        }
        catch (NumberFormatException e) {
            dubboTreeItem.setRequestCount(1);
        }

        // 保存 Hook 脚本
        // Always save hook script if selected, as extTitledPane is removed
        String selectedHook = hookScriptPathField != null ? hookScriptPathField.getText() : null;
        dubboTreeItem.setHookScript(StringUtils.isEmpty(selectedHook) ? null : selectedHook);

        dubboTreeItem.setVersion(versionField.getText());
        dubboTreeItem.setDubboGroup(StringUtils.isEmpty(dubboGroupField.getText()) ? null : dubboGroupField.getText());
        dubboTreeItem.setAttachments(attachmentsArea.getText());

        try {
            dubboTreeItem.setTimeout(Integer.parseInt(timeoutField.getText()));
        }
        catch (NumberFormatException e) {
            dubboTreeItem.setTimeout(null);
        }

        try {
            dubboTreeItem.setRetries(Integer.parseInt(retriesField.getText()));
        }
        catch (NumberFormatException e) {
            dubboTreeItem.setRetries(null);
        }

        // Provider URL not saved per user request
        dubboTreeItem.setProviderUrl(null);

        // Phase 17: Save TLS Config
        dubboTreeItem.setTlsEnable(tlsEnableCheckbox.isSelected());
        // dubboTreeItem.setMutualAuth(tlsMutualAuthCheckbox.isSelected());
        dubboTreeItem.setClientCertPath(tlsClientCertPathField.getText());
        dubboTreeItem.setClientKeyPath(tlsClientKeyPathField.getText());
        dubboTreeItem.setClientKeyPassword(tlsKeyPasswordField.getText());
        dubboTreeItem.setCaCertPath(tlsCaCertPathField.getText());

        // Group
        try {
            String currentEnv = chooseEnvComboBox.getText();
            if (StringUtils.isNotEmpty(currentEnv)) {
                DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(currentEnv);
                if (config != null) {
                    dubboTreeItem.setGroup(config.getRegistryGroup());
                }
            }
        }
        catch (Exception ignore) {
        }

        try {
            dubboWidgetDao.updateData(dubboTreeItem);
            TooltipUtil.showToast(contentBorderPane, I18N.get("msg.modify_success"));
            dubboTreeItemTreeView.refresh();
        }
        catch (Exception e) {
            TooltipUtil.showToast(contentBorderPane, e.getMessage());
            logger.error("", e);
        }
    }

    @FXML
    public void settingLabelAction() throws IOException {
        javafx.stage.Window owner = (mainStackPane != null && mainStackPane.getScene() != null)
            ? mainStackPane.getScene().getWindow() : null;
        dubboEnvConfigureController.showAndWait(owner);
    }

    @FXML
    public void copyLabelAction() {
        if (dubboTreeItemTreeView.getSelectionModel().getSelectedItem() == null
            || !dubboTreeItemTreeView.getSelectionModel().getSelectedItem().isLeaf()) {
            TooltipUtil.showToast(contentBorderPane, I18N.get("msg.child_not_selected"));
            return;
        }
        DubboTreeItem originDubboTreeItem = dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getValue();

        DubboTreeItem dubboTreeItem = new DubboTreeItem();
        dubboTreeItem.setId(originDubboTreeItem.getId());
        dubboTreeItem.setParentId(
            dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getParent().getValue().getId());
        dubboTreeItem.setIsLeaf(originDubboTreeItem.getIsLeaf());
        dubboTreeItem.setName("copy-" + originDubboTreeItem.getName() + "-" + FormatVariableUtil.getRandom(8));
        dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
        dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
        dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());
        dubboTreeItem.setIsSelected(true); // Always save settings
        dubboTreeItem.setRequestType(extRequestType.getText());
        dubboTreeItem.setInputText(inputTextArea.getText());
        String selectedHook = hookScriptPathField != null ? hookScriptPathField.getText() : null;
        dubboTreeItem.setHookScript(StringUtils.isEmpty(selectedHook) ? null : selectedHook);

        String versionText = versionField.getText();
        dubboTreeItem.setVersion(StringUtils.isEmpty(versionText) ? null : versionText);
        String dubboGroupText = dubboGroupField.getText();
        dubboTreeItem.setDubboGroup(StringUtils.isEmpty(dubboGroupText) ? null : dubboGroupText);
        dubboTreeItem.setAttachments(attachmentsArea.getText());

        // Phase 21: Save TLS Config (Copy)
        dubboTreeItem.setTlsEnable(tlsEnableCheckbox.isSelected());
        // dubboTreeItem.setMutualAuth(false); // Implicit
        dubboTreeItem.setClientCertPath(tlsClientCertPathField.getText());
        dubboTreeItem.setClientKeyPath(tlsClientKeyPathField.getText());
        dubboTreeItem.setClientKeyPassword(tlsKeyPasswordField.getText());
        dubboTreeItem.setCaCertPath(tlsCaCertPathField.getText());

        // 保存请求次数
        try {
            String countText = requestCountField.getText();
            if (countText != null && !countText.trim().isEmpty()) {
                dubboTreeItem.setRequestCount(Integer.parseInt(countText.trim()));
            }
            else {
                dubboTreeItem.setRequestCount(1);
            }
        }
        catch (NumberFormatException e) {
            dubboTreeItem.setRequestCount(1);
        }
        try {
            Long newTreeItemId = dubboWidgetDao.insertData(dubboTreeItem);
            dubboTreeItem.setId(newTreeItemId);
            TooltipUtil.showToast(contentBorderPane, I18N.get("msg.success"));
            TreeItem<DubboTreeItem> treeItem = new TreeItem<>(dubboTreeItem);
            dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(treeItem);
            dubboTreeItemTreeView.getSelectionModel().select(treeItem);
            TooltipUtil.showToast(contentBorderPane, I18N.get("msg.success"));
        }
        catch (Exception e) {
            TooltipUtil.showToast(contentBorderPane, e.getMessage());
            logger.error("", e);
        }
    }

    @FXML
    public void manageEnvAction() {
        try {
            javafx.stage.Window owner = (mainStackPane != null && mainStackPane.getScene() != null)
                ? mainStackPane.getScene().getWindow() : null;
            dubboEnvConfigureController.showAndWait(owner);
        }
        catch (IOException e) {
            logger.error("Failed to open env config", e);
        }
    }

    private void initI18n() {
        // Tabs
        requestBodyTab.textProperty().bind(I18N.getBinding("tab.body_json"));
        settingsTab.textProperty().bind(I18N.getBinding("label.settings"));
        hooksTab.textProperty().bind(I18N.getBinding("label.hooks"));

        // Labels in Settings
        connConfigLabel.textProperty().bind(I18N.getBinding("msg.conn_config"));
        timeoutLabel.textProperty().bind(I18N.getBinding("label.timeout"));
        retriesLabel.textProperty().bind(I18N.getBinding("label.retries"));
        directProviderLabel.textProperty().bind(I18N.getBinding("msg.direct_provider"));
        reqTypeLabel.textProperty().bind(I18N.getBinding("label.reqType"));
        batchReqLabel.textProperty().bind(I18N.getBinding("label.batch"));
        countLabel.textProperty().bind(I18N.getBinding("label.count"));
        versionLabel.textProperty().bind(I18N.getBinding("label.version"));
        dubboGroupLabel.textProperty().bind(I18N.getBinding("label.group"));
        implicitParamLabel.textProperty().bind(I18N.getBinding("label.implicit"));

        tlsConfigLabel.textProperty().bind(I18N.getBinding("label.tls_config"));
        tlsEnableLabel.textProperty().bind(I18N.getBinding("label.tls_enable"));
        clientCertLabel.textProperty().bind(I18N.getBinding("label.client_cert"));
        clientKeyLabel.textProperty().bind(I18N.getBinding("label.client_key"));
        keyPassLabel.textProperty().bind(I18N.getBinding("label.password"));
        caCertLabel.textProperty().bind(I18N.getBinding("label.ca_cert"));

        // Hooks Tab
        hookScriptLabel.textProperty().bind(I18N.getBinding("label.script"));
        scriptPreviewLabel.textProperty().bind(I18N.getBinding("label.script_preview"));
        testOutputLabel.textProperty().bind(I18N.getBinding("label.test_output"));
        testHookButton.textProperty().bind(I18N.getBinding("label.test_hook"));

        // Buttons
        sendButton.textProperty().bind(I18N.getBinding("label.send"));
        saveButton.textProperty().bind(I18N.getBinding("label.save"));
        copyButton.textProperty().bind(I18N.getBinding("label.copy"));
        importButton.textProperty().bind(I18N.getBinding("label.import"));

        // ComboBox Prompts
        chooseEnvComboBox.promptTextProperty().bind(I18N.getBinding("label.env"));
        chooseIntComboBox.promptTextProperty().bind(I18N.getBinding("label.interface"));
        chooseMetComboBox.promptTextProperty().bind(I18N.getBinding("label.method"));
    }

    public void showTooltip() {
        chooseIntComboBox
            .setOnMouseEntered(event -> chooseIntComboBox.setTooltip(new Tooltip(chooseIntComboBox.getText())));
        chooseMetComboBox
            .setOnMouseEntered(event -> chooseMetComboBox.setTooltip(new Tooltip(chooseMetComboBox.getText())));
        chooseProvideComboBox.setOnMouseEntered(
            event -> chooseProvideComboBox.setTooltip(new Tooltip(chooseProvideComboBox.getText())));
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> add(DubboTreeItem dubboTreeItem) {
        dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
        dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
        dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());
        dubboTreeItem.setIsSelected(true); // Always save settings, extTitledPane is removed
        // if (!extTitledPane.isExpanded()) { // extTitledPane removed
        // extRequestType.clear();
        // }
        dubboTreeItem.setRequestType(extRequestType.getText());
        dubboTreeItem.setInputText(inputTextArea.getText());

        // 保存请求次数
        try {
            String countText = requestCountField.getText();
            if (countText != null && !countText.trim().isEmpty()) {
                dubboTreeItem.setRequestCount(Integer.parseInt(countText.trim()));
            }
            else {
                dubboTreeItem.setRequestCount(1);
            }
        }
        catch (NumberFormatException e) {
            dubboTreeItem.setRequestCount(1);
        }

        // 保存 Hook 脚本
        // Always save hook script if selected, as extTitledPane is removed
        String selectedHook = hookScriptPathField != null ? hookScriptPathField.getText() : null;
        dubboTreeItem.setHookScript(StringUtils.isEmpty(selectedHook) ? null : selectedHook);

        String versionText = versionField.getText();
        dubboTreeItem.setVersion(StringUtils.isEmpty(versionText) ? null : versionText);
        String dubboGroupText = dubboGroupField.getText();
        dubboTreeItem.setDubboGroup(StringUtils.isEmpty(dubboGroupText) ? null : dubboGroupText);
        dubboTreeItem.setAttachments(attachmentsArea.getText());

        // Phase 21: Save TLS Config (Add)
        dubboTreeItem.setTlsEnable(tlsEnableCheckbox.isSelected());
        // dubboTreeItem.setMutualAuth(false); // Implicit
        dubboTreeItem.setClientCertPath(tlsClientCertPathField.getText());
        dubboTreeItem.setClientKeyPath(tlsClientKeyPathField.getText());
        dubboTreeItem.setClientKeyPassword(tlsKeyPasswordField.getText());
        dubboTreeItem.setCaCertPath(tlsCaCertPathField.getText());

        try {
            dubboTreeItem.setTimeout(Integer.parseInt(timeoutField.getText()));
        }
        catch (NumberFormatException e) {
            dubboTreeItem.setTimeout(null);
        }

        try {
            dubboTreeItem.setRetries(Integer.parseInt(retriesField.getText()));
        }
        catch (NumberFormatException e) {
            dubboTreeItem.setRetries(null);
        }

        // Fetch group from Env Config
        try {
            String currentEnv = chooseEnvComboBox.getText();
            if (StringUtils.isNotEmpty(currentEnv)) {
                DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(currentEnv);
                if (config != null) {
                    dubboTreeItem.setGroup(config.getRegistryGroup());
                }
            }
        }
        catch (Exception ignore) {
            // Ignore env load failure
        }

        // Don't save providerUrl as requested by user
        dubboTreeItem.setProviderUrl(null);

        return insertAndGetTreeItem(dubboTreeItem);
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> importData(DubboTreeItem dubboTreeItem) {
        logger.info("begin import data {}", dubboTreeItem);
        return insertAndGetTreeItem(dubboTreeItem);
    }

    private CustomizeTreeItem<DubboTreeItem> insertAndGetTreeItem(DubboTreeItem dubboTreeItem) {
        try {
            Long newTreeItemId = dubboWidgetDao.insertData(dubboTreeItem);
            dubboTreeItem.setId(newTreeItemId);
            DialogUtil.showSuccessInfo("保存成功");
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return new CustomizeTreeItem<>(dubboTreeItem); // Return the newly created item
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> copy(DubboTreeItem item) {
        if (item == null)
            return null;
        try {
            // Deep copy using JSON
            DubboTreeItem copy = JSON.parseObject(JSON.toJSONString(item), DubboTreeItem.class);
            copy.setId(null);
            copy.setName("copy-" + item.getName() + "-" + FormatVariableUtil.getRandom(8));
            // Keep parentId from source (will be updated by caller if needed, or caller
            // sets it before)
            // TreeMenuFactory sets parentID on the value passed to add(), but here we
            // receive source.
            // Wait, TreeOperateService.copy(T item) receives the SOURCE item.
            // So we should just duplicate it.
            // The caller (TreeMenuFactory) decides where to put it and usually re-orders.
            // But TreeMenuFactory logic for fallback was: Clone -> Set Parent -> Add.
            // If we implement copy(), TreeMenuFactory expects us to return the PERSISTED
            // item?
            // TreeMenuFactory: CustomizeTreeItem<T> copy = service.copy(value);
            // Then it adds to UI.

            // We need to insert it.
            Long newId = dubboWidgetDao.insertData(copy);
            copy.setId(newId);

            return new CustomizeTreeItem<>(copy);
        }
        catch (Exception e) {
            logger.error("Copy failed", e);
            return null;
        }
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> delete(DubboTreeItem dubboTreeItem) {
        try {
            dubboWidgetDao.delLevelData(dubboTreeItem);
            return new CustomizeTreeItem<>(dubboTreeItem);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> update(DubboTreeItem dubboTreeItem) {
        try {
            dubboTreeItem.setName(dubboTreeItem.getName());
            // Only update fields if they are currently loaded in UI?
            // The update method is called by TreeMenuFactory.modifyNode -> uses dialog to
            // get name -> calls update.
            // But here update triggers a FULL UI scrape.
            // If the item being updated is NOT the one currently loaded in the right panel,
            // we just overwrote it with unrelated data!
            // This is DANGEROUS.
            // But for now, let's leave update as is (out of scope for "Copy" fix), ensuring
            // we don't break "Rename".
            // Rename calls modifyNode -> service.update(data).
            // If we rename a non-selected node, DubboWidgetController.update will define
            // its content based on currently visible fields.
            // This IS a bug, but maybe intended for "Save" button?
            // Let's stick to fixing Copy.

            dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
            dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
            dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());
            dubboTreeItem.setIsSelected(false); // Deprecated use of expanded check
            dubboTreeItem.setRequestType(extRequestType.getText());
            dubboTreeItem.setInputText(inputTextArea.getText());

            // 保存请求次数
            try {
                String countText = requestCountField.getText();
                if (countText != null && !countText.trim().isEmpty()) {
                    dubboTreeItem.setRequestCount(Integer.parseInt(countText.trim()));
                }
                else {
                    dubboTreeItem.setRequestCount(1);
                }
            }
            catch (NumberFormatException e) {
                dubboTreeItem.setRequestCount(1);
            }

            // 保存 Hook 脚本
            // Always save hook script if selected, as extTitledPane is removed
            String selectedHook = hookScriptPathField != null ? hookScriptPathField.getText() : null;
            dubboTreeItem.setHookScript(StringUtils.isEmpty(selectedHook) ? null : selectedHook);

            String versionText = versionField.getText();
            dubboTreeItem.setVersion(StringUtils.isEmpty(versionText) ? null : versionText);
            String dubboGroupText = dubboGroupField.getText();
            dubboTreeItem.setDubboGroup(StringUtils.isEmpty(dubboGroupText) ? null : dubboGroupText);
            dubboTreeItem.setAttachments(attachmentsArea.getText());

            try {
                dubboTreeItem.setTimeout(Integer.parseInt(timeoutField.getText()));
            }
            catch (NumberFormatException e) {
                dubboTreeItem.setTimeout(null);
            }

            try {
                dubboTreeItem.setRetries(Integer.parseInt(retriesField.getText()));
            }
            catch (NumberFormatException e) {
                dubboTreeItem.setRetries(null);
            }

            // Don't save providerUrl as requested by user
            dubboTreeItem.setProviderUrl(null);

            // Phase 17: Save TLS Config
            dubboTreeItem.setTlsEnable(tlsEnableCheckbox.isSelected());
            // dubboTreeItem.setMutualAuth(tlsMutualAuthCheckbox.isSelected());
            dubboTreeItem.setClientCertPath(tlsClientCertPathField.getText());
            dubboTreeItem.setClientKeyPath(tlsClientKeyPathField.getText());
            dubboTreeItem.setClientKeyPassword(tlsKeyPasswordField.getText());
            dubboTreeItem.setCaCertPath(tlsCaCertPathField.getText());

            // Fetch group from Env Config
            try {
                String currentEnv = chooseEnvComboBox.getText();
                if (StringUtils.isNotEmpty(currentEnv)) {
                    DubboEnvConfig config = dubboWidgetDao.queryEnvConfig(currentEnv);
                    if (config != null) {
                        dubboTreeItem.setGroup(config.getRegistryGroup());
                    }
                }
            }
            catch (Exception ignore) {
                // Ignore env load failure
            }

            dubboWidgetDao.updateData(dubboTreeItem);
            return new CustomizeTreeItem<>(dubboTreeItem);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void changeToDisplay(DubboTreeItem dubboTreeItem) {
        this.currentDisplayItem = dubboTreeItem; // Update current display item
        // Ensure Request View is visible (Header + Content)
        if (contentBorderPane.getTop() != originalTop) {
            contentBorderPane.setTop(originalTop);
            contentBorderPane.setCenter(originalCenter);
            historyDetailPane.setVisible(false);
        }

        try {
            chooseEnvComboBox.getItems().forEach(s -> {
                if (s.equals(dubboTreeItem.getEnvName())) {
                    chooseEnvComboBox.selectItem(s);
                }
            });
            chooseEnvComboBox.setText(dubboTreeItem.getEnvName());
            chooseIntComboBox.setText(dubboTreeItem.getInterfaceInfo());
            chooseMetComboBox.setText(dubboTreeItem.getMethodInfo());
            chooseProvideComboBox.clear();

            // Restore Timeout & Retries
            if (dubboTreeItem.getTimeout() != null) {
                this.timeoutField.setText(String.valueOf(dubboTreeItem.getTimeout()));
            }
            else {
                this.timeoutField.setText("100000"); // Default 100000
            }

            if (dubboTreeItem.getRetries() != null) {
                this.retriesField.setText(String.valueOf(dubboTreeItem.getRetries()));
            }
            else {
                this.retriesField.setText("0");
            }

            // Provider URL is not saved in TreeItem (null), so usually we don't restore it.
            // But if we wanted to support it later, code would go here.
            // For now, clear is correct.

            // Phase 17: Restore TLS Config
            tlsEnableCheckbox.setSelected(Boolean.TRUE.equals(dubboTreeItem.getTlsEnable()));
            // tlsMutualAuthCheckbox.setSelected(Boolean.TRUE.equals(dubboTreeItem.getMutualAuth()));
            tlsClientCertPathField.setText(
                StringUtils.isNotEmpty(dubboTreeItem.getClientCertPath()) ? dubboTreeItem.getClientCertPath() : "");
            tlsClientKeyPathField.setText(
                StringUtils.isNotEmpty(dubboTreeItem.getClientKeyPath()) ? dubboTreeItem.getClientKeyPath() : "");
            tlsKeyPasswordField.setText(
                StringUtils.isNotEmpty(dubboTreeItem.getClientKeyPassword()) ? dubboTreeItem.getClientKeyPassword()
                    : "");
            tlsCaCertPathField.setText(
                StringUtils.isNotEmpty(dubboTreeItem.getCaCertPath()) ? dubboTreeItem.getCaCertPath() : "");

            // 恢复请求次数
            if (dubboTreeItem.getRequestCount() != null) {
                requestCountField.setText(dubboTreeItem.getRequestCount().toString());
            }
            else {
                requestCountField.setText("1");
            }

            // 恢复 Version & Attachments & DubboGroup
            versionField.setText(StringUtils.defaultString(dubboTreeItem.getVersion()));
            dubboGroupField.setText(StringUtils.defaultString(dubboTreeItem.getDubboGroup()));
            attachmentsArea.setText(dubboTreeItem.getAttachments());

            // 无论 isSelected 是否为 true，都根据节点数据恢复 / 清空请求类型
            // 当节点上 requestType 为空时，应当清空右侧输入框，避免残留上一个节点的值
            extRequestType.setText(StringUtils.defaultString(dubboTreeItem.getRequestType()));

            inputTextArea.setText(dubboTreeItem.getInputText());

            // 恢复 Hook 脚本：同样不再依赖 isSelected，节点为空则清空输入框
            if (hookScriptPathField != null) {
                hookScriptPathField.setText(StringUtils.defaultString(dubboTreeItem.getHookScript()));
            }
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }

    @Override
    public List<DubboTreeItem> queryAll() {
        try {
            return dubboWidgetDao.queryAllData();
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void updatePositionOnly(DubboTreeItem dto) {
        try {
            dubboWidgetDao.updatePositionOnly(dto);
        }
        catch (Exception e) {
            logger.error("更新位置失败", e);
        }
    }

    @FXML
    public void testHookAction() {
        if (currentHook == null) {
            DialogUtil.showErrorInfo("Please select a hook script first");
            return;
        }

        try {
            HookContext context = new HookContext();
            context.setInterfaceName(chooseIntComboBox.getText());
            context.setMethodName(chooseMetComboBox.getText());
            context.setEnvironment(chooseEnvComboBox.getText());
            context.setProtocol("dubbo");
            context.put("log", org.slf4j.LoggerFactory.getLogger("HookScript"));
            context.put("history", com.opencgl.base.utils.OperationHisRecord.class);

            StringBuilder report = new StringBuilder("Hook Execution Report:\n\n");

            // Test Pre-Process
            String originalRequest = inputTextArea.getNonAnnotationText();
            if (StringUtils.isEmpty(originalRequest)) {
                report.append("[Pre-Process] Skipped (Empty Request)\n");
            }
            else {
                long start = System.currentTimeMillis();
                try {
                    String processedRequest = currentHook.preProcess(originalRequest, context);
                    long duration = System.currentTimeMillis() - start;
                    report.append("[Pre-Process] Executed in ").append(duration).append("ms\n");
                    if (!originalRequest.equals(processedRequest)) {
                        report.append("Result: Request MODIFIED\n");
                        report.append("Before:\n").append(formatIfJson(originalRequest)).append("\n");
                        report.append("After:\n").append(formatIfJson(processedRequest)).append("\n");
                    }
                    else {
                        report.append("Result: No Change\n");
                    }
                }
                catch (Exception e) {
                    report.append("[Pre-Process] Failed: ").append(e.getMessage()).append("\n");
                }
            }

            report.append("\n--------------------------------------------------\n\n");

            // Test Post-Process
            String originalResponse = outputTextArea.getText();
            if (StringUtils.isEmpty(originalResponse)) {
                report.append("[Post-Process] Skipped (Empty Response)\n");
            }
            else {
                long start = System.currentTimeMillis();
                try {
                    String processedResponse = currentHook.postProcess(originalResponse, context);
                    long duration = System.currentTimeMillis() - start;
                    report.append("[Post-Process] Executed in ").append(duration).append("ms\n");
                    if (!originalResponse.equals(processedResponse)) {
                        report.append("Result: Response MODIFIED\n");
                        report.append("Before:\n").append(originalResponse).append("\n");
                        report.append("After:\n").append(processedResponse).append("\n");
                    }
                    else {
                        report.append("Result: No Change\n");
                    }
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

            hookOutputTextArea.setText(report.toString());

        }
        catch (Exception e) {
            logger.error("Hook test failed", e);
            DialogUtil.showErrorInfo("Hook execution failed: " + e.getMessage());
        }
    }

    private String formatIfJson(String content) {
        if (content == null || content.trim().isEmpty())
            return "";
        try {
            String trimmed = content.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                Object json = JSON.parse(trimmed);
                return JSON.toJSONString(json, SerializerFeature.PrettyFormat,
                    SerializerFeature.WriteDateUseDateFormat);
            }
        }
        catch (Exception ignore) {
        }
        return content;
    }

    private Map<String, String> parseAttachments(String json) {
        if (json == null || json.trim().isEmpty())
            return null;
        try {
            return JSON.parseObject(json, new TypeReference<>() {
            });
        }
        catch (Exception e) {
            logger.error("Failed to parse attachments", e);
            throw new RuntimeException("隐式参数格式错误: " + e.getMessage());
        }
    }

    // Phase 17: TLS Browse Actions
    @FXML
    public void browseClientCertAction(MouseEvent event) {
        File file = chooseFile("选择客户端证书");
        if (file != null) {
            tlsClientCertPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void browseClientKeyAction(MouseEvent event) {
        File file = chooseFile("选择客户端私钥");
        if (file != null) {
            tlsClientKeyPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void browseCaCertAction(MouseEvent event) {
        File file = chooseFile("选择 CA 证书");
        if (file != null) {
            tlsCaCertPathField.setText(file.getAbsolutePath());
        }
    }

    private File chooseFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("证书/密钥文件", "*.pem", "*.crt", "*.key", "*.cer", "*.der"),
            new FileChooser.ExtensionFilter("所有文件", "*.*"));
        return fileChooser.showOpenDialog(mainStackPane.getScene().getWindow());
    }
    private void submitAsync(Runnable action) {
        if (disposed) return;
        Future<?> future = executor.submit(() -> { if (!disposed) action.run(); });
        if (disposed) future.cancel(true); else runningTasks.add(future);
    }

    private void runOnUi(Runnable action) {
        if (!disposed) Platform.runLater(() -> { if (!disposed) action.run(); });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Future<?> task : new java.util.ArrayList<>(runningTasks)) task.cancel(true);
        runningTasks.clear();
        executor.shutdownNow();
        dubboEnvConfigureController.dispose();
        DubboUtil.shutdownAll();
    }
}
