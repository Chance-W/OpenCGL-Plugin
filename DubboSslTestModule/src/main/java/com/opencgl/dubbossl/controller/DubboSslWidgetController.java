package com.opencgl.dubbossl.controller;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.controls.CustomMFXFilterComboBox;
import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.base.hook.HookContext;
import com.opencgl.base.hook.RequestHook;
import com.opencgl.base.hook.ScriptEngineManager;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.dubbossl.dao.DubboSslWidgetDao;
import com.opencgl.dubbossl.model.DubboEnvConfig;
import com.opencgl.dubbossl.model.DubboSslRequest;
import com.opencgl.dubbossl.model.DubboSslTreeItem;
import com.opencgl.dubbossl.utils.DubboSslUtil;
import com.opencgl.dubbossl.utils.ZkClientTestUtil;
import com.opencgl.dubbossl.i18n.I18N;
import com.opencgl.dubbossl.utils.DubboConfigFileParseUtil;
import com.opencgl.base.utils.LoadingMask;
import com.opencgl.base.utils.TooltipUtil;
import javafx.scene.control.Tooltip;

import org.apache.commons.lang.StringUtils;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dubbo SSL/TLS 控制器
 * 使用独立的 ApplicationName 与普通 Dubbo 插件隔离
 * 支持 Hook 脚本的 preProcess/postProcess 功能
 *
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DubboSslWidgetController implements Initializable, TreeOperateService<DubboSslTreeItem> {
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "dubbo-ssl-worker"); t.setDaemon(true); return t; });
    private final Set<Future<?>> runningTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;

    private final ClassLoader pluginCl;

    public DubboSslWidgetController() {
        this.pluginCl = this.getClass().getClassLoader();
    }
    
    private final LoadingMask loadingMask = new LoadingMask();

    @FXML
    protected SplitPane splitPane;
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    public StackPane contentInputAndOutputPane;
    @FXML
    protected MFXButton sendButton;
    @FXML
    protected MFXButton refreshButton;
    @FXML
    protected MFXButton saveButton;
    @FXML
    protected MFXButton settingButton;
    @FXML
    protected MFXButton copyButton;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseEnvComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseIntComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseMetComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseProvideComboBox;
    @FXML
    protected TitledPane extTitledPane;
    @FXML
    public VBox operateTopVbox;
    @FXML
    protected HBox extHbox;
    @FXML
    protected MFXTextField extRequestType;
    @FXML
    protected CustomTextArea outputTextArea;
    @FXML
    protected CustomTextArea inputTextArea;

    // TLS 证书配置字段
    @FXML
    protected MFXTextField clientCertField;
    @FXML
    protected MFXTextField clientKeyField;
    @FXML
    protected MFXTextField caCertField;
    @FXML
    protected MFXTextField keyPasswordField;

    private final Logger logger = LoggerFactory.getLogger(DubboSslWidgetController.class);
    private final String basePath = com.opencgl.base.model.Base.BASE_PATH + "conf/dubbo/";
    private final DubboSslWidgetDao dubboSslWidgetDao = new DubboSslWidgetDao();
    private final ZkClientTestUtil zkClientTest = new ZkClientTestUtil();

    // Hook 扩展支持
    private ScriptEngineManager scriptEngineManager;
    private RequestHook currentHook;
    @FXML
    protected MFXComboBox<String> hookScriptCombo;
    @FXML
    protected MFXTextField requestCountField;

    // 树视图
    private TreeView<DubboSslTreeItem> dubboSslTreeItemTreeView = null;

    // 环境配置对话框
    private final DubboSslEnvConfigureDialog dubboEnvConfigureDialog = new DubboSslEnvConfigureDialog();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
        initHookScripts();
        loadDefaultTlsConfig();
        setupKeyboardShortcuts();
        initI18n();
    }

    private void initI18n() {
        if (sendButton != null) {
            sendButton.textProperty().bind(I18N.getBinding("btn.send"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("btn.send"));
            sendButton.setTooltip(t);
        }
        if (refreshButton != null) {
            refreshButton.textProperty().bind(I18N.getBinding("btn.refresh"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("btn.refresh"));
            refreshButton.setTooltip(t);
        }
        if (copyButton != null) {
            copyButton.textProperty().bind(I18N.getBinding("btn.copy"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("btn.copy"));
            copyButton.setTooltip(t);
        }
        if (saveButton != null) {
            saveButton.textProperty().bind(I18N.getBinding("btn.save"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("btn.save"));
            saveButton.setTooltip(t);
        }
        if (settingButton != null) {
            settingButton.textProperty().bind(I18N.getBinding("btn.settings"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("btn.settings"));
            settingButton.setTooltip(t);
        }
        if (hookScriptCombo != null) {
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("tooltip.hookScript"));
            hookScriptCombo.setTooltip(t);
        }
        if (extTitledPane != null) {
            extTitledPane.textProperty().bind(I18N.getBinding("ext.title"));
        }
    }

    /**
     * 初始化 Hook 脚本列表
     */
    private void initHookScripts() {
        scriptEngineManager = new ScriptEngineManager();
        if (hookScriptCombo != null) {
            List<String> scripts = scriptEngineManager.listAvailableScripts();
            scripts.addFirst("");
            hookScriptCombo.getItems().addAll(scripts);
            hookScriptCombo.getSelectionModel().selectFirst();
            hookScriptCombo.setOnAction(e -> onHookScriptChanged());
        }
    }

    /**
     * Hook 脚本选择变化事件
     */
    private void onHookScriptChanged() {
        String selected = hookScriptCombo.getValue();
        if (selected == null || selected.isEmpty()) {
            currentHook = null;
            return;
        }
        try {
            Path scriptPath = Paths.get(
                System.getProperty("user.home"), ".opencgl", "hooks", selected
            );
            currentHook = scriptEngineManager.loadScript(scriptPath);
            logger.info("已加载 Hook 脚本: {}", selected);
        }
        catch (Exception ex) {
            logger.error("加载 Hook 脚本失败", ex);
            DialogUtil.showErrorInfo(I18N.get("msg.hookLoadFailed", ex.getMessage()));
            currentHook = null;
        }
    }

    /**
     * 加载默认 TLS 配置
     */
    private void loadDefaultTlsConfig() {
        String home = System.getProperty("user.home");
        String certsDir = home + "/.opencgl/certs/";

        // 设置默认路径（如果文件存在）
        File clientCert = new File(certsDir + "crt-zdubbo-client.crt");
        File clientKey = new File(certsDir + "crt-zdubbo-client.key");
        File caCert = new File(certsDir + "crt-zdubbo-ca.crt");

        if (clientCert.exists()) clientCertField.setText(clientCert.getAbsolutePath());
        if (clientKey.exists()) clientKeyField.setText(clientKey.getAbsolutePath());
        if (caCert.exists()) caCertField.setText(caCert.getAbsolutePath());
    }

    public void init() {
        chooseEnvComboBox.setOnMouseClicked(event -> {
            try {
                List<com.opencgl.dubbossl.model.DubboEnvConfig> configs = dubboSslWidgetDao.queryAllEnvConfig();
                List<String> names = configs.stream().map(com.opencgl.dubbossl.model.DubboEnvConfig::getEnvName).collect(Collectors.toList());
                chooseEnvComboBox.setItems(FXCollections.observableArrayList(names));
            }
            catch (Exception e) {
                logger.error("", e);
            }
        });

        chooseEnvComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (null == newValue) return;
            chooseIntComboBox.getItems().clear();
            chooseMetComboBox.getItems().clear();
            try {
                DubboEnvConfig config = dubboSslWidgetDao.queryEnvConfig(newValue);
                if (config != null && !StringUtils.isEmpty(config.getServiceData())) {
                    List<String> allContent = JSON.parseArray(config.getServiceData(), String.class);
                    List<String> infs = new ArrayList<>();
                    for (String line : allContent) {
                        infs.add(line.split(",")[0]);
                    }
                    chooseIntComboBox.setItems(FXCollections.observableArrayList(infs.stream().distinct().collect(Collectors.toList())));
                }
            }
            catch (Exception ex) {
                logger.error("", ex);
            }
        });

        chooseIntComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (null == newValue) return;
            chooseMetComboBox.getItems().clear();
            try {
                DubboEnvConfig config = dubboSslWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
                if (config != null && !StringUtils.isEmpty(config.getServiceData())) {
                    List<String> allContent = JSON.parseArray(config.getServiceData(), String.class);
                    for (String line : allContent) {
                        String[] arr = line.split(",");
                        if (arr[0].equals(newValue)) {
                            chooseMetComboBox.getItems().add(arr[2]);
                        }
                    }
                }
            }
            catch (Exception ex) {
                logger.error("", ex);
            }
        });

        chooseProvideComboBox.setOnMouseClicked(event -> {
            chooseProvideComboBox.getItems().clear();
            if (StringUtils.isEmpty(chooseIntComboBox.getText())) {
                return;
            }
            submitAsync(() -> {
                try {
                    com.opencgl.dubbossl.model.DubboEnvConfig config = dubboSslWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
                    if (config != null) {
                        String zkAddress = config.getRegistryAddress();
                        String dubboGroup = config.getRegistryGroup();

                        runOnUi(() -> loadingMask.show(contentInputAndOutputPane));
                        List<String> providers = DubboConfigFileParseUtil.getProviders("/" + dubboGroup + "/" + chooseIntComboBox.getText(), zkAddress);
                        runOnUi(() -> chooseProvideComboBox.setItems(FXCollections.observableArrayList(providers)));
                        chooseProvideComboBox.getItems().sorted();
                    }
                }
                catch (Throwable e) {
                    logger.error("", e);
                    runOnUi(() -> DialogUtil.showErrorInfo(e.getMessage()));
                }
                finally {
                    runOnUi(() -> loadingMask.hide());
                }
            });
        });

        // 初始化树视图
        initTreeView();
    }

    /**
     * 初始化树视图
     */
    private void initTreeView() {
        VBox dubboSslTreeItemTreeViewVbox = new TreeViewBuilder<DubboSslTreeItem>()
            .enableSearch(true)
            .onTreeCreated(tree -> this.dubboSslTreeItemTreeView = tree)
            .service(this)
            .dataType(DubboSslTreeItem.class)
            .enableDragDrop(true)
            .onSelect(this::changeToDisplay)
            .build();
        ThemeSwitchUtil.treeStyleSwitch(
            mainStackPane, contentBorderPane, dubboSslTreeItemTreeViewVbox);
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        // ⌘/Ctrl+Enter 发送请求
        KeyCodeCombination sendShortcut = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);
        // ⌘/Ctrl+S 保存
        KeyCodeCombination saveShortcut = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
        // ⌘/Ctrl+R 刷新
        KeyCodeCombination refreshShortcut = new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN);
        // F5 刷新
        KeyCodeCombination f5Shortcut = new KeyCodeCombination(KeyCode.F5);

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
        });
    }

    // TLS 证书选择方法
    @FXML
    public void selectClientCert() {
        File file = selectFile(I18N.get("filechooser.selectClientCert"), "*.crt", "*.pem");
        if (file != null) clientCertField.setText(file.getAbsolutePath());
    }

    @FXML
    public void selectClientKey() {
        File file = selectFile(I18N.get("filechooser.selectClientKey"), "*.key", "*.pem");
        if (file != null) clientKeyField.setText(file.getAbsolutePath());
    }

    @FXML
    public void selectCaCert() {
        File file = selectFile(I18N.get("filechooser.selectCaCert"), "*.crt", "*.pem");
        if (file != null) caCertField.setText(file.getAbsolutePath());
    }

    private File selectFile(String title, String... extensions) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filechooser.certFiles"), extensions));
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        return fileChooser.showOpenDialog(mainStackPane.getScene().getWindow());
    }

    @FXML
    public void sendLabelAction() {
        // 验证输入不为空
        if (inputTextArea.getNonAnnotationText() == null || inputTextArea.getNonAnnotationText().trim().isEmpty()) {
            DialogUtil.showErrorInfo(I18N.get("msg.pleaseInputRequest"));
            return;
        }

        // 验证 TLS 配置
        if (clientCertField.getText().isEmpty() || clientKeyField.getText().isEmpty()) {
            DialogUtil.showErrorInfo(I18N.get("msg.configClientCert"));
            return;
        }


        // Hook 上下文构建
        HookContext hookContext = new HookContext();
        hookContext.put("interface", chooseIntComboBox.getText());
        hookContext.put("method", chooseMetComboBox.getText());
        hookContext.put("provider", chooseProvideComboBox.getText());
        hookContext.put("log", org.slf4j.LoggerFactory.getLogger("HookScript"));
        hookContext.put("history", com.opencgl.base.utils.OperationHisRecord.class);

        // 获取请求类型
        String requestType = "";
        boolean isExtended = extTitledPane.isExpanded();
        if (isExtended && !extRequestType.getText().isEmpty()) {
            requestType = extRequestType.getText();
        }
        else {
            try {
                DubboEnvConfig config = dubboSslWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
                if (config != null && !StringUtils.isEmpty(config.getServiceData())) {
                    List<String> allContent = JSON.parseArray(config.getServiceData(), String.class);
                    for (String line : allContent) {
                        String[] arr = line.split(",");
                        if (arr.length > 2 && arr[0].equals(chooseIntComboBox.getText()) && arr[1].equals(chooseMetComboBox.getText())) {
                            requestType = arr[2];
                            break;
                        }
                    }
                }
            }
            catch (Exception ex) {
                logger.error("", ex);
            }
        }

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
        } catch (NumberFormatException e) {
            logger.warn("请求次数格式错误,使用默认值1", e);
            requestCount = 1;
        }

        AtomicReference<String> outResult = new AtomicReference<>("");
        final RequestHook hookRef = currentHook;
        final HookContext ctxRef = hookContext;
        final int finalRequestCount = requestCount;
        final String finalRequestType = requestType;

        submitAsync(() -> {
            // 清空输出并显示加载中
            runOnUi(() -> {
                outputTextArea.setText("");  // 清空旧结果
                loadingMask.show(contentInputAndOutputPane);
            });
            
            Thread.currentThread().setContextClassLoader(pluginCl);
            try {
                // 生成多个请求，每次请求都执行 Hook preProcess
                java.util.List<DubboSslRequest> requests = new java.util.ArrayList<>();
                for (int i = 0; i < finalRequestCount; i++) {
                    // 每次都重新格式化（支持动态变量如时间戳、随机数）
                    String formattedJson = FormatVariableUtil.format(inputTextArea.getNonAnnotationText());
                    String processedRequestJson = formattedJson;

                    // 每次请求都执行 Hook 前处理（支持动态参数）
                    if (hookRef != null) {
                        try {
                            logger.info("========== Hook 预处理开始 (请求 {}/{}) ==========", i + 1, finalRequestCount);
                            logger.info("原始请求:\n{}", formattedJson);
                            processedRequestJson = hookRef.preProcess(formattedJson, ctxRef);
                            logger.info("处理后请求:\n{}", processedRequestJson);
                            if (!formattedJson.equals(processedRequestJson)) {
                                logger.info("请求已被 Hook 修改");
                            }
                            logger.info("========== Hook 预处理完成 ==========");
                        }
                        catch (Exception e) {
                            logger.error("Hook 预处理失败 (请求 " + (i + 1) + "/" + finalRequestCount + "): " + e.getMessage(), e);
                        }
                    }
                    
                    // 构建单个请求
                    DubboSslRequest req = DubboSslRequest.builder()
                        .dubboRegistryAddr(getRegistryAddr())
                        .dubboRegistryGroup(getRegistryGroup())
                        .dubboProvidersUrl(chooseProvideComboBox.getText())
                        .interfaceName(chooseIntComboBox.getText())
                        .method(chooseMetComboBox.getText())
                        .reqType(finalRequestType)
                        .reqJsonMessage(processedRequestJson)
                        .clientCertPath(clientCertField.getText())
                        .clientKeyPath(clientKeyField.getText())
                        .clientKeyPassword(keyPasswordField.getText())
                        .caCertPath(caCertField.getText())
                        .build();
                    requests.add(req);
                }

                // 批量发送请求（复用连接），使用回调实现实时输出
                DubboSslUtil dubboSslUtil = new DubboSslUtil(requests.getFirst());
                
                // 实时回调：每完成一个请求就立即显示
                dubboSslUtil.sendBatch(requests, indexedResult -> {
                    try {
                        Integer reqIndex = (Integer) indexedResult.get("requestIndex");
                        Integer totalReqs = (Integer) indexedResult.get("totalRequests");
                        
                        StringBuilder resultText = new StringBuilder();
                        
                        // 添加分隔符
                        if (reqIndex > 1) {
                            resultText.append("\n\n");
                        }
                        
                        if (finalRequestCount > 1) {
                            resultText.append("==================== 请求 ").append(reqIndex).append(" / ").append(totalReqs).append(" ====================\n\n");
                        }
                        
                        if (indexedResult.containsKey("error")) {
                            // 错误情况
                            resultText.append("请求失败:\n");
                            resultText.append("错误: ").append(indexedResult.get("error")).append("\n");
                            resultText.append("类型: ").append(indexedResult.get("errorClass"));
                        } else {
                            // 成功情况
                            Object result = indexedResult.get("result");
                            String singleResult;
                            if (result == null) {
                                singleResult = "void (无返回值 / null)";
                            } else if (result instanceof String || result instanceof Number || result instanceof Boolean) {
                                singleResult = String.valueOf(result);
                            } else {
                                try {
                                    singleResult = JSON.toJSONString(result, SerializerFeature.PrettyFormat, SerializerFeature.WriteDateUseDateFormat);
                                } catch (Exception ex) {
                                    singleResult = String.valueOf(result);
                                }
                            }
                            
                            // Hook 后处理
                            if (hookRef != null) {
                                try {
                                    logger.info("========== Hook 后处理开始 (响应 {}/{}) ==========", reqIndex, totalReqs);
                                    String originalResponse = singleResult;
                                    singleResult = hookRef.postProcess(singleResult, ctxRef);
                                    if (!originalResponse.equals(singleResult)) {
                                        logger.info("响应已被 Hook 修改");
                                    }
                                    logger.info("========== Hook 后处理完成 ==========");
                                } catch (Exception e) {
                                    logger.error("Hook 后处理失败 (响应 " + reqIndex + "/" + totalReqs + "): " + e.getMessage(), e);
                                }
                            }
                            
                            resultText.append(singleResult);
                        }
                        
                        // 实时更新 UI（在 JavaFX 线程中）
                        String finalText = resultText.toString();
                        runOnUi(() -> {
                            // 追加到现有文本
                            String currentText = outputTextArea.getText();
                            outputTextArea.setText(currentText + finalText);
                            
                            // 滚动到底部（CodeArea 方法）
                            outputTextArea.moveTo(outputTextArea.getLength());
                            outputTextArea.requestFollowCaret();
                        });
                        
                    } catch (Exception e) {
                        logger.error("处理响应失败", e);
                    }
                });
                
                logger.info("所有请求已完成");
            }
            catch (Exception e) {
                logger.error("发送请求失败", e);
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                
                // 显示错误到 UI
                final String errorMessage = "请求失败:\n" + sw.toString();
                runOnUi(() -> {
                    outputTextArea.setText(errorMessage);
                });
            }
            finally {
                runOnUi(() -> {
                    loadingMask.hide();
                    
                    // 记录第一个请求的内容作为示例
                    String sampleInput = inputTextArea.getNonAnnotationText();
                    if (finalRequestCount > 1) {
                        sampleInput = "批量请求 x" + finalRequestCount + "，首个请求:\n" + sampleInput;
                    }
                    
                    OperationHisRecord.record("SEND DUBBO SSL MESSAGE:\n" + "环境名称:"
                        + chooseEnvComboBox.getText() + "\n"
                        + "zk地址:" + getRegistryAddr() + "\n"
                        + "dubbo分组:" + getRegistryGroup() + "\n"
                        + "接口:" + chooseIntComboBox.getText()
                        + "\n" + "方法:" + chooseMetComboBox.getText() + "\n"
                        + "提供者:" + chooseProvideComboBox.getText() + "\n"
                        + "请求次数:" + finalRequestCount + "\n"
                        + "输入:\n" + sampleInput + "\n"
                        + "输出:\n" + outputTextArea.getText() + "\t");
                });
            }
        });
    }

    private String getRegistryAddr() {
        try {
            com.opencgl.dubbossl.model.DubboEnvConfig config = dubboSslWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
            return config != null ? config.getRegistryAddress() : "";
        }
        catch (Exception e) {
            return "";
        }
    }

    private String getRegistryGroup() {
        try {
            com.opencgl.dubbossl.model.DubboEnvConfig config = dubboSslWidgetDao.queryEnvConfig(chooseEnvComboBox.getText());
            return config != null ? config.getRegistryGroup() : "";
        }
        catch (Exception e) {
            return "";
        }
    }

    @FXML
    public void refreshLabelAction() {
        outputTextArea.replaceText("");
        inputTextArea.replaceText("");
    }

    @FXML
    public void saveLabelAction() {
        CustomizeTreeItem<DubboSslTreeItem> selected = (CustomizeTreeItem<DubboSslTreeItem>) dubboSslTreeItemTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.getValue().getIsLeaf()) {
            DialogUtil.showErrorInfo(I18N.get("msg.selectLeafToSave"));
            return;
        }
        DubboSslTreeItem item = selected.getValue();
        item.setEnvName(chooseEnvComboBox.getText());
        item.setInterfaceInfo(chooseIntComboBox.getText());
        item.setMethodInfo(chooseMetComboBox.getText());

        boolean isExtended = extTitledPane.isExpanded();
        item.setIsSelected(isExtended);

        item.setRequestType(extRequestType.getText());
        item.setInputText(inputTextArea.getText());
        
        // 保存请求次数
        try {
            String countText = requestCountField.getText();
            if (countText != null && !countText.trim().isEmpty()) {
                item.setRequestCount(Integer.parseInt(countText.trim()));
            } else {
                item.setRequestCount(1);
            }
        } catch (NumberFormatException e) {
            item.setRequestCount(1);
        }

        // 保存 Hook 脚本
        if (isExtended) {
            String selectedHook = hookScriptCombo.getValue();
            item.setHookScript((selectedHook == null || selectedHook.isEmpty()) ? null : selectedHook);
        }
        else {
            item.setHookScript(null);
        }
        try {
            dubboSslWidgetDao.updateData(item);
            TooltipUtil.showToast(splitPane, I18N.get("msg.saveSuccess"));
        }
        catch (Exception e) {
            logger.error("保存失败", e);
            DialogUtil.showErrorInfo(I18N.get("msg.saveFailed", e.getMessage()));
        }
    }

    @FXML
    public void settingLabelAction() {
        try {
            javafx.stage.Window owner = (mainStackPane != null && mainStackPane.getScene() != null)
                ? mainStackPane.getScene().getWindow() : null;
            dubboEnvConfigureDialog.showAndWait(owner);
        }
        catch (Exception e) {
            logger.error("打开设置对话框失败", e);
            DialogUtil.showErrorInfo(I18N.get("msg.openSettingsFailed", e.getMessage()));
        }
    }

    @FXML
    public void copyLabelAction() {
//        String text = outputTextArea.getText();
//        if (text != null && !text.isEmpty()) {
//            Clipboard clipboard = Clipboard.getSystemClipboard();
//            ClipboardContent content = new ClipboardContent();
//            content.putString(text);
//            clipboard.setContent(content);
//            DialogUtil.showSuccessInfo("已复制到剪贴板");
//        }

        if (dubboSslTreeItemTreeView.getSelectionModel().getSelectedItem() == null
            || !dubboSslTreeItemTreeView.getSelectionModel().getSelectedItem().isLeaf()) {
            TooltipUtil.showToast(mainStackPane, I18N.get("msg.copyChildNotSelected"));
            return;
        }
        DubboSslTreeItem originDubboTreeItem = dubboSslTreeItemTreeView.getSelectionModel().getSelectedItem().getValue();

        DubboSslTreeItem dubboTreeItem = new DubboSslTreeItem();
        dubboTreeItem.setId(originDubboTreeItem.getId());
        dubboTreeItem.setParentId(dubboSslTreeItemTreeView.getSelectionModel().getSelectedItem().getParent().getValue().getId());
        dubboTreeItem.setIsLeaf(originDubboTreeItem.getIsLeaf());
        dubboTreeItem.setName("copy-" + originDubboTreeItem.getName() + "-" + FormatVariableUtil.getRandom(8));
        dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
        dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
        dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());
        dubboTreeItem.setIsSelected(extTitledPane.isExpanded());
        dubboTreeItem.setRequestType(extRequestType.getText());
        dubboTreeItem.setInputText(inputTextArea.getText());
        dubboTreeItem.setHookScript(hookScriptCombo.getValue());
        // 保存请求次数
        try {
            String countText = requestCountField.getText();
            if (countText != null && !countText.trim().isEmpty()) {
                dubboTreeItem.setRequestCount(Integer.parseInt(countText.trim()));
            } else {
                dubboTreeItem.setRequestCount(1);
            }
        } catch (NumberFormatException e) {
            dubboTreeItem.setRequestCount(1);
        }
        try {
            Long newTreeItemId = dubboSslWidgetDao.insertData(dubboTreeItem);
            dubboTreeItem.setId(newTreeItemId);
            TreeItem<DubboSslTreeItem> treeItem = new TreeItem<>(dubboTreeItem);
            dubboSslTreeItemTreeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(treeItem);
            dubboSslTreeItemTreeView.getSelectionModel().select(treeItem);
            TooltipUtil.showToast(splitPane, I18N.get("msg.copySuccess"));
        }
        catch (Exception e) {
            TooltipUtil.showToast(splitPane, e.getMessage());
            logger.error("", e);
        }
    }

    // TreeOperateService 实现
    @Override
    public CustomizeTreeItem<DubboSslTreeItem> add(DubboSslTreeItem dto) {
        try {
            Long id = dubboSslWidgetDao.insertData(dto);
            dto.setId(id);
            return new CustomizeTreeItem<>(dto);
        }
        catch (Exception e) {
            logger.error("添加失败", e);
            return null;
        }
    }

    @Override
    public CustomizeTreeItem<DubboSslTreeItem> importData(DubboSslTreeItem dto) {
        return add(dto);
    }

    @Override
    public CustomizeTreeItem<DubboSslTreeItem> delete(DubboSslTreeItem dto) {
        try {
            dubboSslWidgetDao.delLevelData(dto);
            return new CustomizeTreeItem<>(dto);
        }
        catch (Exception e) {
            logger.error("删除失败", e);
            return null;
        }
    }

    @Override
    public CustomizeTreeItem<DubboSslTreeItem> update(DubboSslTreeItem dto) {
        try {
            dubboSslWidgetDao.updateData(dto);
            return new CustomizeTreeItem<>(dto);
        }
        catch (Exception e) {
            logger.error("更新失败", e);
            return null;
        }
    }

    @Override
    public void changeToDisplay(DubboSslTreeItem dto) {
        // 先清空所有字段
        chooseEnvComboBox.clear();
        chooseIntComboBox.clear();
        chooseMetComboBox.clear();
        extRequestType.clear();
        inputTextArea.replaceText("");
        outputTextArea.replaceText("");

        if (dto == null) return;

        // 设置非空值
        if (dto.getEnvName() != null && !dto.getEnvName().isEmpty()) {
            chooseEnvComboBox.setText(dto.getEnvName());
        }
        if (dto.getInterfaceInfo() != null && !dto.getInterfaceInfo().isEmpty()) {
            chooseIntComboBox.setText(dto.getInterfaceInfo());
        }
        if (dto.getMethodInfo() != null && !dto.getMethodInfo().isEmpty()) {
            chooseMetComboBox.setText(dto.getMethodInfo());
        }
        if (dto.getRequestType() != null && !dto.getRequestType().isEmpty()) {
            extRequestType.setText(dto.getRequestType());
        }
        if (dto.getInputText() != null && !dto.getInputText().isEmpty()) {
            inputTextArea.replaceText(dto.getInputText());
        }
        
        // 恢复请求次数
        if (dto.getRequestCount() != null) {
            requestCountField.setText(dto.getRequestCount().toString());
        } else {
            requestCountField.setText("1");
        }

        // 恢复扩展模式状态
        if (Boolean.TRUE.equals(dto.getIsSelected())) {
            extTitledPane.setExpanded(true);

            // 恢复 Hook 脚本
            String savedHookScript = dto.getHookScript();
            if (savedHookScript != null && !savedHookScript.isEmpty()) {
                if (!hookScriptCombo.getItems().contains(savedHookScript)) {
                    hookScriptCombo.getItems().add(savedHookScript);
                }
                hookScriptCombo.selectItem(savedHookScript);
            }
            else {
                hookScriptCombo.selectItem("");
            }
        }
        else {
            extTitledPane.setExpanded(false);
            hookScriptCombo.selectItem("");
        }
    }

    @Override
    public List<DubboSslTreeItem> queryAll() {
        try {
            return dubboSslWidgetDao.queryAllData();
        }
        catch (Exception e) {
            logger.error("查询失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updatePositionOnly(DubboSslTreeItem dto) {
        try {
            dubboSslWidgetDao.updatePositionOnly(dto);
        }
        catch (Exception e) {
            logger.error("更新位置失败", e);
        }
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
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
        dubboEnvConfigureDialog.dispose();
        DubboSslUtil.shutdownAll();
    }
}
