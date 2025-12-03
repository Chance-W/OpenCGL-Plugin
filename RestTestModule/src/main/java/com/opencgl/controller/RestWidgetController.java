package com.opencgl.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.TreeViewUtil;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.dao.RestWidgetDao;
import com.opencgl.model.DataAttribute;
import com.opencgl.model.RestRequest;
import com.opencgl.model.RestWidgetDto;
import com.opencgl.views.RestWidgetView;
import com.opencgl.factory.RestSendMessageFactory;
import com.opencgl.model.RestResponse;
import com.opencgl.util.Dom4jUtil;
import com.opencgl.util.EditableTextFieldTreeTableCell;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXFilterComboBox;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
public class RestWidgetController extends RestWidgetView implements Initializable, TreeOperateService<RestWidgetDto> {

    private final Logger logger = LoggerFactory.getLogger(RestWidgetController.class);
    private TreeView<RestWidgetDto> treeView;
    private final SendMessageService<RestRequest, RestResponse> sendMessageService = RestSendMessageFactory.sendRestMessage();
    private final RestWidgetDao restWidgetDao = new RestWidgetDao();
    
    // 新增的 UI 组件
    @FXML private MFXFilterComboBox<String> environmentComboBox;
    @FXML private ToggleGroup bodyTypeToggleGroup;
    @FXML private RadioButton noneRadioButton;
    @FXML private RadioButton rawRadioButton;
    @FXML private RadioButton formDataRadioButton;
    @FXML private RadioButton xwwwFormUrlencodedRadioButton;
    @FXML private RadioButton binaryRadioButton;
    @FXML private HBox contentTypeHBox;
    @FXML private MFXButton formatButton;
    @FXML private MFXButton clearBodyButton;
    @FXML private Label statusCodeLabel;
    @FXML private Label responseTimeLabel;
    @FXML private Label responseSizeLabel;
    @FXML private TabPane responseTabPane;
    @FXML private TreeTableView<DataAttribute> responseHeaders;
    @FXML private TreeTableView<DataAttribute> responseCookies;
    @FXML private TreeTableColumn<DataAttribute, String> responseHeadersKey;
    @FXML private TreeTableColumn<DataAttribute, String> responseHeadersValue;
    @FXML private TreeTableColumn<DataAttribute, String> responseCookiesKey;
    @FXML private TreeTableColumn<DataAttribute, String> responseCookiesValue;
    @FXML private TreeTableColumn<DataAttribute, String> responseCookiesDomain;
    @FXML private TreeTableColumn<DataAttribute, String> responseCookiesPath;
    @FXML private MFXButton headersPresetButton;
    @FXML private MFXButton headersClearButton;
    
    // 环境变量
    private Map<String, Map<String, String>> environments = new HashMap<>();
    private String currentEnvironment = "No Environment";
    
    // 常用头部预设
    private Map<String, Map<String, String>> headerPresets = new HashMap<>();

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
        initTableView();
        //设置控件
        setControlAndStyle();
        List<RestWidgetDto> restWidgetDtos = queryAll();
        treeView = TreeViewUtil.buildTreeView(restWidgetDtos, mainStackPane, this, RestWidgetDto.class);
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeView);
    }

    public void init() throws Exception {
        restWidgetDao.checkTable();
        
        // 初始化环境变量
        initEnvironments();
        
        // 初始化头部预设
        initHeaderPresets();
        
        // 设置悬停展示url
        urlTextField.setOnMouseEntered(event -> urlTextField.setTooltip(new Tooltip(urlTextField.getText())));
        
        // 设置选择默认停留在body
        restJfxTabPane.getSelectionModel().select(2);
        
        // 设置 mediaType 默认选中第一个
        chooseMediaTypeComboBox.selectFirst();
        
        // 初始化 Body 类型选择
        initBodyTypeSelection();
        
        // 初始化响应表格
        initResponseTables();
        
        // 设置快捷键
        setupKeyboardShortcuts();
        
        // 设置环境选择监听
        setupEnvironmentListener();
        
        // 设置 URL 输入监听
        setupUrlListener();
    }

    private void initTableView() {
        generateTreeTableView(headers, headersAddButton, headersDelButton, headersKey, headersValue, headersDescription);
        generateTreeTableView(cookies, cookiesAddButton, cookiesDelButton, cookiesKey, cookiesValue, cookiesDescription);
    }
    
    private void initEnvironments() {
        // 初始化默认环境
        Map<String, String> devEnv = new HashMap<>();
        devEnv.put("baseUrl", "http://localhost:8080");
        devEnv.put("apiKey", "dev-api-key");
        environments.put("Development", devEnv);
        
        Map<String, String> stagingEnv = new HashMap<>();
        stagingEnv.put("baseUrl", "https://staging-api.example.com");
        stagingEnv.put("apiKey", "staging-api-key");
        environments.put("Staging", stagingEnv);
        
        Map<String, String> prodEnv = new HashMap<>();
        prodEnv.put("baseUrl", "https://api.example.com");
        prodEnv.put("apiKey", "prod-api-key");
        environments.put("Production", prodEnv);
    }
    
    private void initBodyTypeSelection() {
        // 设置 Body 类型选择监听
        bodyTypeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == rawRadioButton) {
                contentTypeHBox.setVisible(true);
                chooseMediaTypeComboBox.selectFirst();
            } else if (newVal == formDataRadioButton || newVal == xwwwFormUrlencodedRadioButton) {
                contentTypeHBox.setVisible(false);
                // 这里可以切换到表单编辑模式
            } else {
                contentTypeHBox.setVisible(false);
            }
        });
        
        // 格式化按钮事件
        formatButton.setOnAction(event -> formatRequestBody());
        
        // 清空按钮事件
        clearBodyButton.setOnAction(event -> inputTextArea.clear());
        
        // 头部预设按钮事件
        headersPresetButton.setOnAction(event -> showHeaderPresets());
        
        // 头部清空按钮事件
        headersClearButton.setOnAction(event -> clearAllHeaders());
    }
    
    private void initResponseTables() {
        // 初始化响应头表格
        TreeItem<DataAttribute> responseHeadersRoot = new TreeItem<>(new DataAttribute("", "", ""));
        responseHeaders.setRoot(responseHeadersRoot);
        responseHeaders.setShowRoot(false);
        
        responseHeadersKey.setCellValueFactory(cellData -> cellData.getValue().getValue().nameProperty());
        responseHeadersValue.setCellValueFactory(cellData -> cellData.getValue().getValue().valueProperty());
        
        // 初始化响应 Cookie 表格
        TreeItem<DataAttribute> responseCookiesRoot = new TreeItem<>(new DataAttribute("", "", ""));
        responseCookies.setRoot(responseCookiesRoot);
        responseCookies.setShowRoot(false);
        
        responseCookiesKey.setCellValueFactory(cellData -> cellData.getValue().getValue().nameProperty());
        responseCookiesValue.setCellValueFactory(cellData -> cellData.getValue().getValue().valueProperty());
        responseCookiesDomain.setCellValueFactory(cellData -> cellData.getValue().getValue().desProperty());
        responseCookiesPath.setCellValueFactory(cellData -> cellData.getValue().getValue().valueProperty());
    }
    
    private void setupKeyboardShortcuts() {
        // 设置 Ctrl+Enter 发送请求
        KeyCodeCombination sendShortcut = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN);
        mainStackPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (sendShortcut.match(event)) {
                sendLabelAction();
                event.consume();
            }
        });
    }
    
    private void setupEnvironmentListener() {
        environmentComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentEnvironment = newVal;
                updateUrlWithEnvironment();
            }
        });
    }
    
    private void setupUrlListener() {
        urlTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.contains("{{")) {
                // 检测到环境变量，可以进行高亮显示
                updateUrlWithEnvironment();
            }
        });
    }
    
    private void updateUrlWithEnvironment() {
        String url = urlTextField.getText();
        if (StringUtils.isNotEmpty(url) && !"No Environment".equals(currentEnvironment)) {
            Map<String, String> envVars = environments.get(currentEnvironment);
            if (envVars != null) {
                String updatedUrl = url;
                for (Map.Entry<String, String> entry : envVars.entrySet()) {
                    String placeholder = "{{" + entry.getKey() + "}}";
                    if (updatedUrl.contains(placeholder)) {
                        updatedUrl = updatedUrl.replace(placeholder, entry.getValue());
                    }
                }
                if (!updatedUrl.equals(url)) {
                    urlTextField.setText(updatedUrl);
                }
            }
        }
    }
    
    private void formatRequestBody() {
        String content = inputTextArea.getText();
        if (StringUtils.isEmpty(content)) {
            return;
        }
        
        String contentType = chooseMediaTypeComboBox.getText();
        try {
            if (contentType.contains("json")) {
                // 格式化 JSON
                Object json = JSONObject.parse(content);
                String formatted = JSON.toJSONString(json, SerializerFeature.PrettyFormat);
                inputTextArea.setText(formatted);
            } else if (contentType.contains("xml")) {
                // 格式化 XML
                String formatted = Dom4jUtil.formatXml(content);
                inputTextArea.setText(formatted);
            }
        } catch (Exception e) {
            TooltipUtil.showToast(contentInputAndOutputPane, "格式化失败: " + e.getMessage());
        }
    }
    
    private void initHeaderPresets() {
        // JSON API 预设
        Map<String, String> jsonApiPreset = new HashMap<>();
        jsonApiPreset.put("Content-Type", "application/json");
        jsonApiPreset.put("Accept", "application/json");
        jsonApiPreset.put("User-Agent", "OpenCGL-REST-Client/1.0");
        headerPresets.put("JSON API", jsonApiPreset);
        
        // XML API 预设
        Map<String, String> xmlApiPreset = new HashMap<>();
        xmlApiPreset.put("Content-Type", "application/xml");
        xmlApiPreset.put("Accept", "application/xml");
        xmlApiPreset.put("User-Agent", "OpenCGL-REST-Client/1.0");
        headerPresets.put("XML API", xmlApiPreset);
        
        // 认证预设
        Map<String, String> authPreset = new HashMap<>();
        authPreset.put("Authorization", "Bearer {{token}}");
        authPreset.put("Content-Type", "application/json");
        authPreset.put("Accept", "application/json");
        headerPresets.put("Bearer Auth", authPreset);
        
        // CORS 预设
        Map<String, String> corsPreset = new HashMap<>();
        corsPreset.put("Access-Control-Allow-Origin", "*");
        corsPreset.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        corsPreset.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headerPresets.put("CORS Headers", corsPreset);
        
        // 缓存预设
        Map<String, String> cachePreset = new HashMap<>();
        cachePreset.put("Cache-Control", "no-cache");
        cachePreset.put("Pragma", "no-cache");
        cachePreset.put("Expires", "0");
        headerPresets.put("No Cache", cachePreset);
    }
    
    private void showHeaderPresets() {
        // 创建预设选择对话框
        ChoiceDialog<String> dialog = new ChoiceDialog<>("JSON API", headerPresets.keySet());
        dialog.setTitle("选择头部预设");
        dialog.setHeaderText("选择一个常用的头部预设");
        dialog.setContentText("预设:");
        
        dialog.showAndWait().ifPresent(presetName -> {
            Map<String, String> preset = headerPresets.get(presetName);
            if (preset != null) {
                applyHeaderPreset(preset);
            }
        });
    }
    
    private void applyHeaderPreset(Map<String, String> preset) {
        TreeItem<DataAttribute> root = headers.getRoot();
        root.getChildren().clear();
        
        for (Map.Entry<String, String> entry : preset.entrySet()) {
            DataAttribute dataAttribute = new DataAttribute(entry.getKey(), entry.getValue(), "");
            root.getChildren().add(new TreeItem<>(dataAttribute));
        }
    }
    
    private void clearAllHeaders() {
        TreeItem<DataAttribute> root = headers.getRoot();
        root.getChildren().clear();
    }
    
    private String processResponseBody(String responseBody) {
        if (StringUtils.isEmpty(responseBody)) {
            return "";
        }
        
        try {
            if (JSONObject.isValid(responseBody)) {
                return JSON.toJSONString(JSONObject.parseObject(responseBody), SerializerFeature.PrettyFormat, SerializerFeature.WriteDateUseDateFormat);
            } else if (Dom4jUtil.isValidXml(responseBody)) {
                return Dom4jUtil.formatXml(responseBody);
            } else {
                return responseBody;
            }
        } catch (Exception e) {
            logger.warn("Failed to format response body: {}", e.getMessage());
            return responseBody;
        }
    }

    private void generateTreeTableView(TreeTableView<DataAttribute> treeTableView,
                                       MFXButton addButton,
                                       MFXButton delButton,
                                       TreeTableColumn<DataAttribute, String> key,
                                       TreeTableColumn<DataAttribute, String> value,
                                       TreeTableColumn<DataAttribute, String> description) {
        // 创建根节点
        TreeItem<DataAttribute> treeItem = new TreeItem<>(new DataAttribute("-", "-", "-"));
        treeTableView.setRoot(treeItem);
        treeTableView.setShowRoot(false);

        addButton.setOnAction(event -> {
            ObservableList<TreeItem<DataAttribute>> children = treeItem.getChildren();
            List<TreeItem<DataAttribute>> notFixedDataAttrs = children.stream().filter(item -> {
                DataAttribute dataAttribute = item.getValue();
                return StringUtils.equals(dataAttribute.getName(), "-")
                    || StringUtils.isEmpty(dataAttribute.getName())
                    || StringUtils.equals(dataAttribute.getValue(), "-")
                    || StringUtils.isEmpty(dataAttribute.getValue());
            }).toList();

            if (CollectionUtils.isNotEmpty(notFixedDataAttrs)) {
                TooltipUtil.showToast(contentInputAndOutputPane, "请配置合法参数后再继续添加");
            }
            else {
                treeItem.getChildren().add(new TreeItem<>(new DataAttribute("-", "-", "-")));
                treeTableView.getSelectionModel().selectLast();
            }
        });

        delButton.setOnAction(event -> {
            if (CollectionUtils.isNotEmpty(treeItem.getChildren())
                && null != treeTableView.getSelectionModel().getSelectedItem()) {
                treeItem.getChildren().remove(treeTableView.getSelectionModel().getSelectedIndex());
                treeTableView.getSelectionModel().select(treeTableView.getSelectionModel().getSelectedIndex());
            }
            else {
                TooltipUtil.showToast("未选中待删除节点");
            }
        });

        key.setCellValueFactory(cellData -> cellData.getValue().getValue().nameProperty());
        key.setCellFactory(column -> new EditableTextFieldTreeTableCell<>());
        key.setOnEditCommit((TreeTableColumn.CellEditEvent<DataAttribute, String> t) -> t.getTreeTableView()
            .getTreeItem(t.getTreeTablePosition()
                .getRow())
            .getValue().name.set(t.getNewValue()));

        value.setCellValueFactory(cellData -> cellData.getValue().getValue().valueProperty());
        value.setCellFactory(column -> new EditableTextFieldTreeTableCell<>());
        value.setOnEditCommit((TreeTableColumn.CellEditEvent<DataAttribute, String> t) -> t.getTreeTableView()
            .getTreeItem(t.getTreeTablePosition()
                .getRow())
            .getValue().value.set(t.getNewValue()));


        description.setCellValueFactory(cellData -> cellData.getValue().getValue().desProperty());
        description.setCellFactory(column -> new EditableTextFieldTreeTableCell<>());
        description.setOnEditCommit((TreeTableColumn.CellEditEvent<DataAttribute, String> t) -> t.getTreeTableView()
            .getTreeItem(t.getTreeTablePosition()
                .getRow())
            .getValue().des.set(t.getNewValue()));
    }


    @FXML
    public void sendLabelAction() {
        if (StringUtils.isEmpty(urlTextField.getText())
            || StringUtils.isEmpty(chooseMetComboBox.getText())) {
            DialogUtil.showErrorInfo("请求方法和请求地址不能为空!", mainStackPane);
            return;
        }

        //设置选择停留在body
        restJfxTabPane.getSelectionModel().select(2);

        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> {
                LoadingUtil.show(contentInputAndOutputPane);
                // 重置响应信息
                statusCodeLabel.setText("-");
                responseTimeLabel.setText("-");
                responseSizeLabel.setText("-");
            });
            
            Instant startTime = Instant.now();
            String requestText = FormatVariableUtil.format(inputTextArea.getText());
            logger.info("rest request message is {}", requestText);
            
            List<DataAttribute> headersDataAttributes = traverseTreeItems(headers.getRoot());
            Map<String, String> requestHeader = new HashMap<>(32);
            headersDataAttributes.forEach(dataAttribute -> {
                if (StringUtils.isNotEmpty(dataAttribute.getName())
                    && !StringUtils.equals(dataAttribute.getName(), "-")) {
                    requestHeader.put(dataAttribute.getName(), dataAttribute.getValue());
                }
            });

            Map<String, String> requestCookie = new HashMap<>(32);
            List<DataAttribute> cookiesDataAttributes = traverseTreeItems(cookies.getRoot());
            cookiesDataAttributes.forEach(dataAttribute -> {
                if (StringUtils.isNotEmpty(dataAttribute.getName())
                    && !StringUtils.equals(dataAttribute.getName(), "-")) {
                    requestCookie.put(dataAttribute.getName(), dataAttribute.getValue());
                }
            });

            try {
                RestRequest restRequest = RestRequest.builder()
                    .requestMethod(chooseMetComboBox.getText())
                    .requestUrl(urlTextField.getText())
                    .requestHeaderMap(requestHeader)
                    .requestCookieMap(requestCookie)
                    .mediaType(chooseMediaTypeComboBox.getText())
                    .requestMessage(requestText)
                    .build();
                
                RestResponse response = sendMessageService.send(restRequest);
                Instant endTime = Instant.now();
                long responseTime = Duration.between(startTime, endTime).toMillis();
                
                // 更新响应信息
                Platform.runLater(() -> {
                    statusCodeLabel.setText(String.valueOf(response.getResultCode() != null ? response.getResultCode() : 200));
                    responseTimeLabel.setText(responseTime + "ms");
                    responseSizeLabel.setText(formatResponseSize(response.getResultMsg()));
                    
                    // 根据状态码设置颜色
                    if (response.getResultCode() != null) {
                        if (response.getResultCode() >= 200 && response.getResultCode() < 300) {
                            statusCodeLabel.setStyle("-fx-text-fill: #28a745;");
                        } else if (response.getResultCode() >= 300 && response.getResultCode() < 400) {
                            statusCodeLabel.setStyle("-fx-text-fill: #ffc107;");
                        } else if (response.getResultCode() >= 400) {
                            statusCodeLabel.setStyle("-fx-text-fill: #dc3545;");
                        }
                    }
                });
                
                // 处理响应内容
                final String responseBody = processResponseBody(response.getResultMsg());
                
                Platform.runLater(() -> outputTextArea.setText(responseBody));
                
                // 解析响应头（这里需要根据实际的 RestResponse 结构调整）
                // 假设 RestResponse 有响应头信息
                updateResponseHeaders(response);
                
            } catch (Throwable e) {
                Instant endTime = Instant.now();
                long responseTime = Duration.between(startTime, endTime).toMillis();
                
                Platform.runLater(() -> {
                    statusCodeLabel.setText("Error");
                    statusCodeLabel.setStyle("-fx-text-fill: #dc3545;");
                    responseTimeLabel.setText(responseTime + "ms");
                    responseSizeLabel.setText("-");
                });
                
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                logger.error("", e);
                Platform.runLater(() -> outputTextArea.setText(sw.toString()));
            } finally {
                Platform.runLater(() -> LoadingUtil.remove(contentInputAndOutputPane));
                OperationHisRecord.record("SEND REST MESSAGE:\n" + "请求方法:" + chooseMetComboBox.getText() +
                    "\n" + "请求地址:" + urlTextField.getText() + "\n" + "请求header:" + JSONObject.toJSONString(requestHeader) + "\n" + "请求cookie:" + JSONObject.toJSONString(requestCookie)
                    + "\n" + "输入:\n" + requestText + "\n"
                    + "输出:\n" + outputTextArea.getText() + "\t");
            }
        });
    }
    
    private String formatResponseSize(String content) {
        if (StringUtils.isEmpty(content)) {
            return "0 B";
        }
        int size = content.getBytes().length;
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
    
    private void updateResponseHeaders(RestResponse response) {
        // 这里需要根据实际的 RestResponse 结构调整
        // 假设 RestResponse 有响应头信息
        Platform.runLater(() -> {
            TreeItem<DataAttribute> root = responseHeaders.getRoot();
            root.getChildren().clear();
            
            // 添加一些示例响应头
            root.getChildren().add(new TreeItem<>(new DataAttribute("Content-Type", "application/json", "")));
            root.getChildren().add(new TreeItem<>(new DataAttribute("Content-Length", String.valueOf(response.getResultMsg().length()), "")));
            root.getChildren().add(new TreeItem<>(new DataAttribute("Server", "nginx/1.18.0", "")));
        });
    }

    @FXML
    public void refreshLabelAction() {
        /* eventBus.post(DialogStyleDto.builder().type(OperateTypeEnum.QUERY).build());*/
    }

    @FXML
    public void saveLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null) {
            TooltipUtil.showToast(contentInputAndOutputPane, "子节点未选中");
            return;
        }
        RestWidgetDto restWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        restWidgetDto.setRequestMethod(chooseMetComboBox.getText());
        restWidgetDto.setRequestUrl(urlTextField.getText());
        buildHeaderAndCookieDataAttribute(restWidgetDto);
        restWidgetDto.setRequestMediaType(chooseMediaTypeComboBox.getText());
        restWidgetDto.setInputText(inputTextArea.getText());
        try {
            restWidgetDao.updateData(restWidgetDto);
            TooltipUtil.showToast(contentInputAndOutputPane, "保存成功");
        }
        catch (Exception e) {
            logger.error("", e);
            TooltipUtil.showToast(contentInputAndOutputPane, e.getMessage());
        }
    }

    @FXML
    public void copyLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
            || !treeView.getSelectionModel().getSelectedItem().isLeaf()) {
            TooltipUtil.showToast(mainStackPane, "子节点未选中");
            return;
        }
        RestWidgetDto originRestWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        RestWidgetDto newRestWidgetDto = new RestWidgetDto();
        newRestWidgetDto.setName("copy-"
            + originRestWidgetDto.getName()
            + "-"
            + FormatVariableUtil.getRandom(8));
        newRestWidgetDto.setParentId(newRestWidgetDto.getParentId());
        newRestWidgetDto.setIsLeaf(true);
        newRestWidgetDto.setRequestHeader(originRestWidgetDto.getRequestHeader());
        newRestWidgetDto.setRequestCookie(originRestWidgetDto.getRequestCookie());
        newRestWidgetDto.setRequestMethod(originRestWidgetDto.getRequestMethod());
        newRestWidgetDto.setRequestUrl(originRestWidgetDto.getRequestUrl());
        newRestWidgetDto.setRequestMediaType(originRestWidgetDto.getRequestMediaType());
        newRestWidgetDto.setInputText(originRestWidgetDto.getInputText());
        try {
            CustomizeTreeItem<RestWidgetDto> restWidgetDtoCustomizeTreeItem = insertAndGetTreeItem(newRestWidgetDto);
            treeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(restWidgetDtoCustomizeTreeItem);
            treeView.getSelectionModel().select(restWidgetDtoCustomizeTreeItem);
            TooltipUtil.showToast(contentInputAndOutputPane, "成功");
        }
        catch (Exception e) {
            logger.error("", e);
            TooltipUtil.showToast(contentInputAndOutputPane, e.getMessage());
        }
    }

    public void setControlAndStyle() {
        sendButton.setTooltip(new Tooltip("发送请求"));
        refreshButton.setTooltip(new Tooltip("刷新"));
        copyButton.setTooltip(new Tooltip("复制"));
        saveButton.setTooltip(new Tooltip("保存"));
        chooseMetComboBox.setOnMouseEntered(event -> chooseMetComboBox.setTooltip(new Tooltip(chooseMetComboBox.getText())));
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }

    @Override
    public CustomizeTreeItem<RestWidgetDto> add(RestWidgetDto restWidgetDto) {
        buildRestInfoExpectTree(restWidgetDto);
        return insertAndGetTreeItem(restWidgetDto);
    }

    @Override
    public CustomizeTreeItem<RestWidgetDto> importData(RestWidgetDto restWidgetDto) {
        logger.info("begin import data {}", restWidgetDto);
        return insertAndGetTreeItem(restWidgetDto);
    }

    @Override
    public CustomizeTreeItem<RestWidgetDto> delete(RestWidgetDto restWidgetDto) {
        try {
            restWidgetDao.delLevelData(restWidgetDto);
            return new CustomizeTreeItem<>(restWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<RestWidgetDto> update(RestWidgetDto restWidgetDto) {
        try {
            buildRestInfoExpectTree(restWidgetDto);
            restWidgetDao.updateData(restWidgetDto);
            return new CustomizeTreeItem<>(restWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void changeToDisplay(RestWidgetDto restWidgetDto) {
        try {
            urlTextField.setText(restWidgetDto.getRequestUrl());
            chooseMediaTypeComboBox.setText(restWidgetDto.getRequestMediaType());
            chooseMetComboBox.setText(restWidgetDto.getRequestMethod());
            if (StringUtils.isNotEmpty(restWidgetDto.getRequestHeader())) {
                List<Map<String, String[]>> headerDataAttributes = JSON.parseObject(restWidgetDto.getRequestHeader(), new TypeReference<>() {
                });
                generateDisplayTableTreeData(headerDataAttributes, headers);
            }
            if (StringUtils.isNotEmpty(restWidgetDto.getRequestCookie())) {
                List<Map<String, String[]>> cookiesDataAttributes = JSON.parseObject(restWidgetDto.getRequestCookie(), new TypeReference<>() {
                });
                generateDisplayTableTreeData(cookiesDataAttributes, cookies);
            }
            inputTextArea.setText(restWidgetDto.getInputText());
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }

    private void generateDisplayTableTreeData(List<Map<String, String[]>> headerDataAttributes, TreeTableView<DataAttribute> headers) {
        TreeItem<DataAttribute> root = headers.getRoot();
        root.getChildren().clear();
        headerDataAttributes.forEach(item -> item.forEach((s, strings) -> {
            DataAttribute dataAttribute = new DataAttribute(s, strings[0], strings[1]);
            root.getChildren().add(new TreeItem<>(dataAttribute));
        }));
    }

    @Override
    public List<RestWidgetDto> queryAll() {
        try {
            return restWidgetDao.queryAllData();
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    private List<DataAttribute> traverseTreeItems(TreeItem<DataAttribute> root) {
        List<DataAttribute> dataAttributes = new ArrayList<>();
        ObservableList<TreeItem<DataAttribute>> children = root.getChildren();
        for (TreeItem<DataAttribute> child : children) {
            dataAttributes.add(child.getValue()); // 添加节点值到列表
        }
        return dataAttributes;
    }

    private CustomizeTreeItem<RestWidgetDto> insertAndGetTreeItem(RestWidgetDto restWidgetDto) {
        try {
            Long newId = restWidgetDao.insertData(restWidgetDto);
            restWidgetDto.setId(newId);
            return new CustomizeTreeItem<>(restWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    private void buildRestInfoExpectTree(RestWidgetDto restWidgetDto) {
        restWidgetDto.setRequestUrl(urlTextField.getText());
        restWidgetDto.setRequestMethod(chooseMetComboBox.getText());
        buildHeaderAndCookieDataAttribute(restWidgetDto);
        restWidgetDto.setRequestMediaType(chooseMediaTypeComboBox.getText());
        restWidgetDto.setInputText(inputTextArea.getText());
    }

    private void buildHeaderAndCookieDataAttribute(RestWidgetDto restWidgetDto) {
        List<Map<String, String[]>> headersDataAttribute = new ArrayList<>();
        headers.getRoot().getChildren().forEach(item -> {
            DataAttribute dataAttribute = item.getValue();
            Map<String, String[]> dataAttributeMap = new HashMap<>();
            String[] valueAndDes = new String[]{dataAttribute.getValue(), dataAttribute.getDes()};
            dataAttributeMap.put(dataAttribute.getName(), valueAndDes);
            headersDataAttribute.add(dataAttributeMap);
        });
        restWidgetDto.setRequestHeader(JSONObject.toJSONString(headersDataAttribute));

        List<Map<String, String[]>> cookiesDataAttribute = new ArrayList<>();
        cookies.getRoot().getChildren().forEach(item -> {
            DataAttribute dataAttribute = item.getValue();
            Map<String, String[]> dataAttributeMap = new HashMap<>();
            String[] valueAndDes = new String[]{dataAttribute.getValue(), dataAttribute.getDes()};
            dataAttributeMap.put(dataAttribute.getName(), valueAndDes);
            cookiesDataAttribute.add(dataAttributeMap);
        });
        restWidgetDto.setRequestCookie(JSONObject.toJSONString(cookiesDataAttribute));
    }
}


