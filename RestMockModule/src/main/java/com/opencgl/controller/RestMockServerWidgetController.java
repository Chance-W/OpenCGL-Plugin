package com.opencgl.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.restmock.i18n.I18N;
import com.opencgl.dao.RestMockServerWidgetDao;
import com.opencgl.model.RestMockServerWidgetDto;
import com.opencgl.model.RestMockTableBean;
import com.opencgl.views.RestMockServerWidgetView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
public class RestMockServerWidgetController extends RestMockServerWidgetView implements Initializable {

    private final static Logger logger = LoggerFactory.getLogger(RestMockServerWidgetController.class);
    private final RestMockServerWidgetDao restMockServerWidgetDao = new RestMockServerWidgetDao();
    private final ObservableList<RestMockTableBean> tableData = FXCollections.observableArrayList();
    /** 过滤列表，用于搜索功能 */
    private FilteredList<RestMockTableBean> filteredData;
    private static final Pattern P = Pattern.compile("\\s*|\\t|\\r|\\n");

    private ServerSocket serverSocket = null;
    private volatile boolean serverRunning = false;
    private Thread serverThread = null;

    public void dispose() {
        serverRunning = false;
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.debug("Failed to close RestMock server socket", e);
            }
        }
        Thread thread = serverThread;
        serverThread = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** 当前正在编辑的规则（null 表示新建模式） */
    private RestMockTableBean currentEditingBean = null;
    /** 是否正在新建规则 */
    private boolean isCreatingNew = false;

    /** HTTP 方法列表 */
    private static final List<String> HTTP_METHODS = Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD",
            "OPTIONS");
    /** 常用状态码列表 */
    private static final List<String> STATUS_CODES = Arrays.asList("200", "201", "204", "301", "302", "400", "401",
            "403", "404", "405", "500", "502", "503");

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        restMockServerWidgetDao.checkTable();
        initData();
        initView();
        if (portTextField != null && (portTextField.getText() == null || portTextField.getText().trim().isEmpty())) {
            portTextField.setText("8080");
        }
        initTableView();
        initRightPanel();
        initEvent();
        initI18n();
    }

    private void initI18n() {
        Tooltip tStart = new Tooltip();
        tStart.textProperty().bind(I18N.getBinding("tooltip.start"));
        startButton.setTooltip(tStart);
        Tooltip tStop = new Tooltip();
        tStop.textProperty().bind(I18N.getBinding("tooltip.stop"));
        stopButton.setTooltip(tStop);
        if (statusLabel != null)
            statusLabel.setText(I18N.get("status.stopped"));
    }

    public void initData() {
        try {
            List<RestMockTableBean> list = new ArrayList<>();
            for (RestMockServerWidgetDto dto : restMockServerWidgetDao.queryAllData()) {
                list.add(new RestMockTableBean(
                        dto.getIsEnable(), dto.getContentPath(),
                        dto.getResponseHeader(), replaceBlank(dto.getResponseContent()),
                        dto.getDescription(),
                        dto.getHttpMethod(), dto.getStatusCode(), dto.getDelayMs()));
            }
            tableData.addAll(list);
        } catch (Exception e) {
            DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
        }
    }

    @SneakyThrows
    private void initView() {
        java.net.URL css = getClass().getResource("/css/Style.css");
        if (css != null) {
            mainStackPane.getStylesheets().add(css.toExternalForm());
        }
    }

    private void initTableView() {
        // 启用列 — 使用 CheckBox
        isEnabledTableColumn.setCellValueFactory(new PropertyValueFactory<>("isEnable"));
        isEnabledTableColumn.setCellFactory(col -> new TableCell<RestMockTableBean, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            private boolean listening = false;
            {
                checkBox.getStyleClass().add("rest-mock-checkbox");
                checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (!listening)
                        return;
                    RestMockTableBean bean = getTableRow() != null ? getTableRow().getItem() : null;
                    if (bean != null) {
                        bean.setIsEnable(newVal);
                        try {
                            restMockServerWidgetDao.updateData(RestMockServerWidgetDto.builder()
                                    .isEnable(newVal)
                                    .contentPath(bean.getContentTextPath())
                                    .responseHeader(bean.getResponseHeader())
                                    .responseContent(bean.getResponseContent())
                                    .description(bean.getDes())
                                    .httpMethod(bean.getHttpMethod())
                                    .statusCode(bean.getStatusCode())
                                    .delayMs(bean.getDelayMs())
                                    .build(), bean.getContentTextPath());
                        } catch (Exception ex) {
                            logger.error("更新启用状态失败", ex);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    listening = false;
                    checkBox.setSelected(item);
                    listening = true;
                    setGraphic(checkBox);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });

        // HTTP 方法列 - 带彩色徽章
        methodTableColumn.setCellValueFactory(new PropertyValueFactory<>("httpMethod"));
        methodTableColumn.setCellFactory(col -> new TableCell<RestMockTableBean, String>() {
            private final Label badge = new Label();
            {
                badge.getStyleClass().add("rest-mock-method-badge");
                badge.setMinWidth(48);
                badge.setAlignment(javafx.geometry.Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    badge.setText(item);
                    // 移除旧的方法样式
                    badge.getStyleClass().removeIf(s -> s.startsWith("method-"));
                    badge.getStyleClass().add("method-" + item.toLowerCase());
                    setGraphic(badge);
                }
            }
        });

        // 路径列
        contentPathTableColumn.setCellValueFactory(new PropertyValueFactory<>("contentTextPath"));

        // 描述列
        desTableColumn.setCellValueFactory(new PropertyValueFactory<>("des"));

        // 设置过滤列表
        filteredData = new FilteredList<>(tableData, p -> true);
        tableViewMain.setItems(filteredData);

        // 搜索过滤
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(bean -> {
                if (newVal == null || newVal.isEmpty())
                    return true;
                String lower = newVal.toLowerCase();
                return (bean.getContentTextPath() != null && bean.getContentTextPath().toLowerCase().contains(lower))
                        || (bean.getDes() != null && bean.getDes().toLowerCase().contains(lower))
                        || (bean.getHttpMethod() != null && bean.getHttpMethod().toLowerCase().contains(lower));
            });
        });

        // 点击列表行 → 右侧面板显示详情
        tableViewMain.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isCreatingNew) {
                showRuleDetail(newVal);
            }
        });
    }

    /**
     * 初始化右侧面板的 ComboBox 等组件
     */
    private void initRightPanel() {
        httpMethodCombo.setItems(FXCollections.observableArrayList(HTTP_METHODS));
        statusCodeCombo.setItems(FXCollections.observableArrayList(STATUS_CODES));
        // 默认显示空状态
        showEmptyState();
    }

    /**
     * 显示空状态（右侧提示选择规则）
     */
    private void showEmptyState() {
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        ruleDetailPane.setVisible(false);
        ruleDetailPane.setManaged(false);
        currentEditingBean = null;
        isCreatingNew = false;
    }

    /**
     * 显示规则详情编辑面板
     */
    private void showRuleDetail(RestMockTableBean bean) {
        currentEditingBean = bean;
        isCreatingNew = false;

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        ruleDetailPane.setVisible(true);
        ruleDetailPane.setManaged(true);

        ruleDetailTitle.setText(I18N.get("label.ruleConfig"));

        // 填充表单
        httpMethodCombo.setValue(bean.getHttpMethod() != null ? bean.getHttpMethod() : "GET");
        statusCodeCombo.setValue(String.valueOf(bean.getStatusCode()));
        delayMsField.setText(String.valueOf(bean.getDelayMs()));
        contentPath.setText(bean.getContentTextPath());
        des.setText(bean.getDes());
        responseHeader.setText(bean.getResponseHeader());
        responseContent.setText(bean.getResponseContent());
    }

    /**
     * 显示新建规则面板
     */
    private void showNewRuleForm() {
        currentEditingBean = null;
        isCreatingNew = true;

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        ruleDetailPane.setVisible(true);
        ruleDetailPane.setManaged(true);

        ruleDetailTitle.setText(I18N.get("label.addRule"));

        // 清空表单
        httpMethodCombo.setValue("GET");
        statusCodeCombo.setValue("200");
        delayMsField.setText("0");
        contentPath.clear();
        des.clear();
        responseHeader.setText("application/json");
        responseContent.clear();

        // 取消列表选中
        tableViewMain.getSelectionModel().clearSelection();
    }

    private void initEvent() {
        if (statusProcessBar != null)
            statusProcessBar.setVisible(false);

        // 添加规则按钮
        addRuleButton.setOnAction(event -> showNewRuleForm());

        // 保存按钮
        saveRuleButton.setOnAction(event -> saveCurrentRule());

        // 删除按钮
        deleteRuleButton.setOnAction(event -> deleteCurrentRule());

        // 启动服务器
        startButton.setOnAction(event -> {
            String portStr = portTextField.getText();
            if (portStr == null || portStr.trim().isEmpty() || !isNumeric(portStr.trim())) {
                DialogUtil.showErrorInfo(I18N.get("msg.portRange"), mainStackPane);
                return;
            }
            int port = Integer.parseInt(portStr.trim());
            if (port < 1 || port > 65535) {
                DialogUtil.showErrorInfo(I18N.get("msg.portRange"), mainStackPane);
                return;
            }
            outputTextArea.clear();
            serverRunning = true;
            final int bindPort = port;
            serverThread = new Thread(() -> {
                try {
                    serverSocket = new ServerSocket(bindPort);
                    serverSocket.setReuseAddress(true);
                    logger.info("RestMock server started on port {}", bindPort);
                    while (serverRunning && serverSocket != null && !serverSocket.isClosed()) {
                        Socket clientSocket = null;
                        try {
                            clientSocket = serverSocket.accept();
                        } catch (IOException e) {
                            if (serverRunning)
                                logger.debug("Accept: {}", e.getMessage());
                            break;
                        }
                        if (clientSocket == null)
                            break;
                        final Socket cs = clientSocket;
                        try {
                            InputStream in = cs.getInputStream();
                            byte[] b = new byte[8192];
                            int len = in.read(b);
                            if (len <= 0)
                                continue;
                            String raw = new String(b, 0, len);
                            String requestLine = raw.split("\r\n|\n", 2)[0].trim();
                            // 提取请求方法
                            String reqMethod = requestLine.split("\\s+")[0].toUpperCase();

                            // 提取请求路径（从请求行中解析，如 "GET /api/user HTTP/1.1" → "/api/user"）
                            String[] requestParts = requestLine.split("\\s+");
                            String requestPath = requestParts.length > 1 ? requestParts[1] : "";
                            // 去掉查询参数
                            if (requestPath.contains("?")) {
                                requestPath = requestPath.substring(0, requestPath.indexOf("?"));
                            }

                            // 仿 Spring AntPathMatcher 策略匹配（分越小优先级越高）：
                            //   0        精确匹配
                            //   1~9999   无通配符前缀匹配（前缀越长优先级越高）
                            //   10000+   含通配符（* 比 ** 优先，字面前缀越长优先）
                            //   MAX      未匹配
                            String normalizedReqPath = normalizePath(requestPath);
                            RestMockTableBean matchedBean = null;
                            int bestScore = Integer.MAX_VALUE;
                            for (RestMockTableBean bean : tableData) {
                                if (!bean.getIsEnable())
                                    continue;
                                String rulePath = bean.getContentTextPath();
                                if (rulePath == null || rulePath.isEmpty())
                                    continue;
                                String normalizedRule = normalizePath(rulePath);
                                boolean methodMatch = bean.getHttpMethod() == null
                                        || bean.getHttpMethod().isEmpty()
                                        || bean.getHttpMethod().equalsIgnoreCase(reqMethod);
                                if (!methodMatch)
                                    continue;
                                int score = computeMatchScore(normalizedRule, normalizedReqPath);
                                if (score < bestScore) {
                                    bestScore = score;
                                    matchedBean = bean;
                                }
                            }

                            String res = "";
                            if (matchedBean != null) {
                                // 模拟延迟
                                if (matchedBean.getDelayMs() > 0) {
                                    try {
                                        Thread.sleep(matchedBean.getDelayMs());
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                                int sc = matchedBean.getStatusCode() > 0 ? matchedBean.getStatusCode() : 200;
                                String statusText = getStatusText(sc);
                                res = "HTTP/1.1 " + sc + " " + statusText + "\r\nContent-Type: "
                                        + matchedBean.getResponseHeader() + "\r\n\r\n"
                                        + matchedBean.getResponseContent();
                            }
                            if (res.isEmpty()) {
                                res = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\nNo mock matched.";
                            }
                            final String finalRes = res;
                            final String logReq = requestLine;
                            OutputStream out = cs.getOutputStream();
                            out.write(res.getBytes());
                            out.flush();
                            Platform.runLater(() -> {
                                outputTextArea.appendText(cs.getRemoteSocketAddress() + " | " + logReq + "\r\n");
                                outputTextArea.appendText(
                                        "  -> " + (finalRes.length() > 80 ? finalRes.substring(0, 80) + "..."
                                                : finalRes.replace("\r\n", " ")) + "\r\n\r\n");
                            });
                        } catch (Exception e) {
                            logger.warn("Handle request: {}", e.getMessage());
                        } finally {
                            try {
                                cs.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                } catch (IOException e) {
                    if (serverRunning)
                        logger.error("RestMock server error", e);
                } finally {
                    serverRunning = false;
                    if (serverSocket != null) {
                        try {
                            serverSocket.close();
                        } catch (IOException ignored) {
                        }
                        serverSocket = null;
                    }
                    Platform.runLater(() -> {
                        startButton.setDisable(false);
                        if (statusProcessBar != null)
                            statusProcessBar.setVisible(false);
                        if (statusLabel != null)
                            statusLabel.setText(I18N.get("status.stopped"));
                    });
                }
            }, "RestMock-Server");
            serverThread.setDaemon(true);
            serverThread.start();
            startButton.setDisable(true);
            if (statusProcessBar != null)
                statusProcessBar.setVisible(true);
            if (statusLabel != null)
                statusLabel.setText(I18N.get("status.running", String.valueOf(bindPort)));
        });

        // 停止服务器
        stopButton.setOnAction(event -> {
            serverRunning = false;
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
                serverSocket = null;
            }
            startButton.setDisable(false);
            if (statusProcessBar != null)
                statusProcessBar.setVisible(false);
            if (statusLabel != null)
                statusLabel.setText(I18N.get("status.stopped"));
        });

        // 右键菜单
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editMenuItem = new MenuItem(I18N.get("menu.editReturn"));
        MenuItem delMenuItem = new MenuItem(I18N.get("menu.deleteNode"));
        MenuItem copyMenuItem = new MenuItem(I18N.get("menu.copyNode"));
        contextMenu.getItems().addAll(editMenuItem, delMenuItem, copyMenuItem);
        tableViewMain.setContextMenu(contextMenu);

        editMenuItem.setOnAction(event -> {
            RestMockTableBean selected = tableViewMain.getSelectionModel().getSelectedItem();
            if (selected != null)
                showRuleDetail(selected);
        });

        delMenuItem.setOnAction(event -> {
            RestMockTableBean selected = tableViewMain.getSelectionModel().getSelectedItem();
            if (selected == null)
                return;
            try {
                restMockServerWidgetDao.delLevelData(RestMockServerWidgetDto.builder()
                        .contentPath(selected.getContentTextPath()).build());
                tableData.remove(selected);
                showEmptyState();
            } catch (Exception e) {
                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
            }
        });

        copyMenuItem.setOnAction(event -> {
            RestMockTableBean bean = tableViewMain.getSelectionModel().getSelectedItem();
            if (bean == null)
                return;
            String newPath = bean.getContentTextPath() + "-copy-" + System.currentTimeMillis();
            RestMockServerWidgetDto dto = RestMockServerWidgetDto.builder()
                    .isEnable(false)
                    .contentPath(newPath)
                    .responseHeader(bean.getResponseHeader())
                    .responseContent(bean.getResponseContent())
                    .description(bean.getDes())
                    .httpMethod(bean.getHttpMethod())
                    .statusCode(bean.getStatusCode())
                    .delayMs(bean.getDelayMs())
                    .build();
            try {
                restMockServerWidgetDao.insertData(dto);
                tableData.add(new RestMockTableBean(false, newPath, bean.getResponseHeader(),
                        bean.getResponseContent(), bean.getDes(),
                        bean.getHttpMethod(), bean.getStatusCode(), bean.getDelayMs()));
            } catch (Exception e) {
                logger.error("复制节点失败", e);
                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
            }
        });

    }

    /**
     * 保存当前规则（新建 或 编辑）
     */
    private void saveCurrentRule() {
        if (StringUtils.isEmpty(contentPath.getText())) {
            TooltipUtil.showToast(mainStackPane, I18N.get("msg.paramsRequired"));
            return;
        }

        String method = httpMethodCombo.getValue() != null ? httpMethodCombo.getValue() : "GET";
        int sc = 200;
        try {
            sc = Integer.parseInt(statusCodeCombo.getValue());
        } catch (Exception ignored) {
        }
        int delay = 0;
        try {
            delay = Integer.parseInt(delayMsField.getText());
        } catch (Exception ignored) {
        }

        String headerVal = responseHeader.getText() != null ? responseHeader.getText() : "application/json";

        if (isCreatingNew) {
            // 新建模式
            RestMockServerWidgetDto dto = RestMockServerWidgetDto.builder()
                    .isEnable(true)
                    .contentPath(contentPath.getText())
                    .responseHeader(headerVal)
                    .responseContent(responseContent.getText())
                    .description(des.getText())
                    .httpMethod(method)
                    .statusCode(sc)
                    .delayMs(delay)
                    .build();
            try {
                restMockServerWidgetDao.insertData(dto);
                RestMockTableBean newBean = new RestMockTableBean(true,
                        contentPath.getText(), headerVal, responseContent.getText(),
                        des.getText(), method, sc, delay);
                tableData.add(newBean);
                tableViewMain.getSelectionModel().select(newBean);
                isCreatingNew = false;
                currentEditingBean = newBean;
                TooltipUtil.showToast(mainStackPane, I18N.get("msg.saveSuccess"));
            } catch (Exception e) {
                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
            }
        } else if (currentEditingBean != null) {
            // 编辑模式
            String oldPath = currentEditingBean.getContentTextPath();
            RestMockServerWidgetDto dto = RestMockServerWidgetDto.builder()
                    .isEnable(currentEditingBean.getIsEnable())
                    .contentPath(contentPath.getText())
                    .responseHeader(headerVal)
                    .responseContent(responseContent.getText())
                    .description(des.getText())
                    .httpMethod(method)
                    .statusCode(sc)
                    .delayMs(delay)
                    .build();
            try {
                restMockServerWidgetDao.updateData(dto, oldPath);
                // 更新 bean
                currentEditingBean.setContentTextPath(contentPath.getText());
                currentEditingBean.setResponseHeader(headerVal);
                currentEditingBean.setResponseContent(responseContent.getText());
                currentEditingBean.setDes(des.getText());
                currentEditingBean.setHttpMethod(method);
                currentEditingBean.setStatusCode(sc);
                currentEditingBean.setDelayMs(delay);
                tableViewMain.refresh();
                TooltipUtil.showToast(mainStackPane, I18N.get("msg.saveSuccess"));
            } catch (Exception e) {
                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
            }
        }
    }

    /**
     * 删除当前编辑的规则
     */
    private void deleteCurrentRule() {
        if (currentEditingBean == null)
            return;
        try {
            restMockServerWidgetDao.delLevelData(RestMockServerWidgetDto.builder()
                    .contentPath(currentEditingBean.getContentTextPath()).build());
            tableData.remove(currentEditingBean);
            showEmptyState();
        } catch (Exception e) {
            DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
        }
    }

    /**
     * 获取 HTTP 状态码对应的文本
     */
    private String getStatusText(int code) {
        switch (code) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 204:
                return "No Content";
            case 301:
                return "Moved Permanently";
            case 302:
                return "Found";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 500:
                return "Internal Server Error";
            case 502:
                return "Bad Gateway";
            case 503:
                return "Service Unavailable";
            default:
                return "OK";
        }
    }

    /**
     * 归一化路径：确保以 "/" 开头，去除末尾多余的 "/"（根路径 "/" 除外）。
     * 例：" 1 " → "/1"，"/1/" → "/1"，"http://host/1" → "/1"
     */
    static String normalizePath(String path) {
        if (path == null) return "/";
        path = path.trim();
        // 如果是完整 URL（含 scheme），提取路径部分
        int schemeEnd = path.indexOf("://");
        if (schemeEnd >= 0) {
            int pathStart = path.indexOf('/', schemeEnd + 3);
            path = (pathStart >= 0) ? path.substring(pathStart) : "/";
        }
        // 去掉查询参数（路径归一化中不需要，由调用方在请求路径里提前去除）
        // 确保前导 /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // 去掉末尾 /（保留根路径 "/"）
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    // ─────────────────────────────────────────────
    //  Ant 风格路径匹配引擎（仿 Spring AntPathMatcher）
    // ─────────────────────────────────────────────

    /**
     * 计算规则路径与请求路径的匹配分（越小优先级越高）。
     *
     * <pre>
     *  0          精确匹配，如 /api/user == /api/user
     *  10000+     含通配符：字面前缀越长分越低；* 比 ** 优先；** 比前缀回退优先
     *  200000+    无通配符隐式前缀回退（兜底），前缀越长分越低
     *  MAX_VALUE  不匹配
     * </pre>
     *
     * 典型优先级示例（请求 /1/3）：
     *   /1/3    → 0        精确
     *   /1/*    → 10970    单段通配（不匹配 /1/3，不参与）
     *   /1/**   → 19970    多段通配   ← 比前缀回退更优先
     *   /**     → 19990    全局兜底通配
     *   /1      → 199998   隐式前缀回退（最后手段）
     *
     * 设计原则：显式书写了通配符规则（/* / /**）的意图明确，
     * 优先级高于未加通配符的"隐式前缀回退"行为。
     */
    static int computeMatchScore(String normalizedRule, String normalizedReqPath) {
        boolean hasWildcard = normalizedRule.contains("*");

        if (!hasWildcard) {
            // 精确匹配
            if (normalizedReqPath.equals(normalizedRule)) return 0;
            // 无通配符隐式前缀回退：放到分数最高区间（兜底），
            // 确保任何显式通配符规则（/* / /**）都比它优先
            if (normalizedReqPath.startsWith(normalizedRule + "/")
                    || normalizedReqPath.startsWith(normalizedRule + "?")) {
                return 200000 - normalizedRule.length(); // 前缀越长分越低，越优先
            }
            return Integer.MAX_VALUE;
        }

        // 通配符模式：先判断是否命中
        if (!antMatch(normalizedRule, normalizedReqPath)) return Integer.MAX_VALUE;

        // 字面前缀长度（第一个 * 之前的字符数）
        int firstStar = normalizedRule.indexOf('*');
        int literalPrefixLen = firstStar >= 0 ? firstStar : normalizedRule.length();

        // 统计 ** 和 * 数量（先替换 ** 再数 *，避免重复计数）
        String tmp = normalizedRule;
        int doubleStars = 0;
        int dIdx;
        while ((dIdx = tmp.indexOf("**")) >= 0) {
            doubleStars++;
            tmp = tmp.substring(0, dIdx) + "  " + tmp.substring(dIdx + 2);
        }
        int singleStars = (int) tmp.chars().filter(c -> c == '*').count();

        // 字面前缀越长 → 分越低 → 优先级越高；** 比 * 优先级更低
        // 范围控制在 10000~190000，低于隐式前缀回退的 200000 起点
        return 10000 + doubleStars * 10000 + singleStars * 1000 - literalPrefixLen * 10;
    }

    /**
     * Ant 风格路径匹配（分段实现，不依赖正则）。
     *
     * <ul>
     *   <li>{@code *}  匹配单个路径段内任意字符（不跨越 {@code /}），如 {@code /api/*} 匹配 {@code /api/user}</li>
     *   <li>{@code **} 匹配零个或多个路径段（可跨越 {@code /}），如 {@code /api/**} 匹配 {@code /api/a/b/c}</li>
     *   <li>段内部分通配，如 {@code /api/use*} 匹配 {@code /api/user}、{@code /api/users}</li>
     * </ul>
     */
    static boolean antMatch(String pattern, String path) {
        String[] pp = splitPathSegments(pattern);
        String[] sp = splitPathSegments(path);
        return matchSegments(pp, 0, sp, 0);
    }

    private static String[] splitPathSegments(String p) {
        if (p == null || p.isEmpty()) return new String[0];
        String s = p.startsWith("/") ? p.substring(1) : p;
        if (s.isEmpty()) return new String[0];
        return s.split("/", -1);
    }

    /**
     * 递归分段匹配。
     * pp = 规则段数组，pi = 当前规则段下标；
     * sp = 请求路径段数组，si = 当前请求段下标。
     */
    private static boolean matchSegments(String[] pp, int pi, String[] sp, int si) {
        while (pi < pp.length) {
            String seg = pp[pi];

            if ("**".equals(seg)) {
                // ** 在末尾：匹配所有剩余段（含零个）
                if (pi == pp.length - 1) return true;
                // ** 不在末尾：尝试从 sp 各位置继续匹配后续 pattern
                for (int i = si; i <= sp.length; i++) {
                    if (matchSegments(pp, pi + 1, sp, i)) return true;
                }
                return false;

            } else if ("*".equals(seg)) {
                // 纯 *：精确匹配一个段（内容不限）
                if (si >= sp.length) return false;
                pi++;
                si++;

            } else if (seg.contains("*")) {
                // 段内部分通配，如 "use*"、"*Controller"
                if (si >= sp.length) return false;
                if (!globSegmentMatch(seg, sp[si])) return false;
                pi++;
                si++;

            } else {
                // 精确段比对
                if (si >= sp.length || !seg.equals(sp[si])) return false;
                pi++;
                si++;
            }
        }
        return si == sp.length;
    }

    /**
     * 段内 glob 匹配：将 {@code *} 视为"任意字符序列"，不跨越 {@code /}。
     * 例：{@code "use*"} 匹配 {@code "user"}、{@code "users"}；
     *     {@code "*Controller"} 匹配 {@code "UserController"}。
     */
    private static boolean globSegmentMatch(String pattern, String segment) {
        // 拆分 pattern 为各字面量片段，依次在 segment 中查找
        String[] parts = pattern.split("\\*", -1);
        int idx = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            int found = segment.indexOf(part, idx);
            if (found < 0) return false;
            // 第一个字面量必须从 segment 开头开始（pattern 不以 * 开头时）
            if (i == 0 && !pattern.startsWith("*") && found != 0) return false;
            idx = found + part.length();
        }
        // pattern 不以 * 结尾时，匹配必须消耗完整个 segment
        if (!pattern.endsWith("*") && idx != segment.length()) return false;
        return true;
    }

    public static String replaceBlank(String str) {
        String dest = "";
        if (str != null) {
            Matcher m = P.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }

    public static boolean isNumeric(String s) {
        if (s != null && !s.trim().isEmpty()) {
            return s.matches("^[0-9]*$");
        } else {
            return false;
        }
    }
}
