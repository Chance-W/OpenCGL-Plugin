package com.opencgl.ssh.session;

import com.opencgl.ssh.dao.SshConnectionDao;
import com.opencgl.ssh.i18n.I18N;
import com.opencgl.ssh.lifecycle.LifecycleDisposer;
import com.opencgl.ssh.model.SshConnectionDto;
import com.opencgl.ssh.service.ITerminalService;
import com.opencgl.ssh.service.SshService;
import com.opencgl.ssh.terminal.TerminalBridge;
import com.opencgl.base.theme.ThemeManager;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Locale;

/**
 * SSH 会话 Tab
 * 布局：顶部工具栏（信息条 + 操作按钮）+ WebView 终端
 * 连接配置通过弹框编辑，Tab 名称自动生成为 user@host 格式
 */
public class SshSessionTab extends Tab {

    private static final Logger logger = LoggerFactory.getLogger(SshSessionTab.class);

    private final ITerminalService terminalService;
    private final SshConnectionDao dao = new SshConnectionDao();
    private final WebView terminalWebView;
    private final WebEngine webEngine;
    private TerminalBridge terminalBridge;
    private final LifecycleDisposer lifecycle = new LifecycleDisposer();
    private volatile Thread connectThread;
    private final ChangeListener<Locale> localeListener = (obs, oldVal, newVal) -> {
        if (terminalBridge != null) {
            terminalBridge.updateI18n();
        }
        updateUITexts();
    };
    private final ListChangeListener<String> stylesheetListener = change -> {
        boolean isDark = ThemeManager.getInstance().getCurrentTheme().isDark();
        if (terminalBridge != null) {
            terminalBridge.setTheme(isDark);
        }
    };
    private final ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> {
        if (oldScene != null) {
            oldScene.getStylesheets().removeListener(stylesheetListener);
        }
        if (newScene != null) {
            newScene.getStylesheets().addListener(stylesheetListener);
        }
    };

    // 工具栏控件
    private final Label infoLabel = new Label();
    private final Button editButton = new Button();
    private final Button connectButton = new Button();
    private final Button disconnectButton = new Button();
    private final Label statusLabel = new Label();

    private SshConnectionDto connectionData;

    /** 防抖：WebView 尺寸变化时延迟 fit，避免布局过程中频繁调用 */
    private Timeline fitDebounce;

    // ─────────────────────────────────────────────────────────────────────────
    // 构造器
    // ─────────────────────────────────────────────────────────────────────────

    public SshSessionTab(String title, ITerminalService terminalService) {
        super(title);
        this.terminalService = terminalService;
        this.terminalWebView = new WebView();
        this.webEngine = terminalWebView.getEngine();

        initUI();
        initTerminal();
        bindEvents();
        updateStatus();

        setOnClosed(e -> dispose());
    }

    public SshSessionTab(SshConnectionDto dto) {
        this(buildTabTitle(dto), new SshService());
        this.connectionData = dto;
        refreshInfoBar();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI 初始化
    // ─────────────────────────────────────────────────────────────────────────

    private void initUI() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: -theme-bg-primary;");

        // 顶部工具栏
        HBox toolbar = buildToolbar();

        // 终端 WebView（保证可获焦点、可接收 IME 输入法）
        terminalWebView.setStyle("-fx-background-color: -theme-bg-primary;");
        terminalWebView.setFocusTraversable(true);
        VBox.setVgrow(terminalWebView, Priority.ALWAYS);

        root.getChildren().addAll(toolbar, terminalWebView);
        setContent(root);
    }

    private HBox buildToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle(
                "-fx-background-color: -theme-bg-secondary;" +
                        "-fx-border-color: transparent transparent -theme-border-color transparent;" +
                        "-fx-border-width: 0 0 1 0;");

        // 连接信息区域（左侧，弹性占满）
        VBox infoBox = new VBox(2);
        updateUITexts();

        Label titleLabel = new Label();
        titleLabel.textProperty().bind(com.opencgl.ssh.i18n.I18N.getBinding("welcome.title"));
        titleLabel.setStyle("-fx-text-fill: -theme-text-secondary; -fx-font-size: 11px;");
        infoLabel.setStyle("-fx-text-fill: -theme-text-primary; -fx-font-size: 12px; -fx-font-family: monospace;");
        infoBox.getChildren().addAll(titleLabel, infoLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        // 按钮组
        styleEditButton(editButton);
        stylePrimaryButton(connectButton, "#27ae60", 75);
        stylePrimaryButton(disconnectButton, "#e74c3c", 75);

        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 13px;");
        statusLabel.setMinWidth(85);

        toolbar.getChildren().addAll(infoBox, editButton, new Separator(javafx.geometry.Orientation.VERTICAL),
                connectButton, disconnectButton, statusLabel);

        return toolbar;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 弹框编辑连接配置
    // ─────────────────────────────────────────────────────────────────────────

    private void showEditDialog() {
        com.opencgl.ssh.views.SshConfigDialog.showDialog(connectionData, updatedDto -> {
            this.connectionData = updatedDto;
            setText(buildTabTitle(updatedDto));
            refreshInfoBar();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 信息条刷新
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshInfoBar() {
        if (connectionData == null) {
            infoLabel.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("msg.unconfigured_hint", "未配置  —  点击「✏️ 编辑」填写连接信息"));
            return;
        }
        
        if (connectionData.getHost() != null && connectionData.getHost().startsWith("LOCAL:")) {
            String name = nvl(connectionData.getName(), connectionData.getHost().substring(6));
            infoLabel.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("msg.local_terminal", "本机终端: {0}", name));
            editButton.setVisible(false);
            editButton.setManaged(false);
            // 本地终端不需要连接/断开按钮在信息条上（直接由Tab关闭管理同步进程）
            connectButton.setVisible(false);
            connectButton.setManaged(false);
            disconnectButton.setVisible(false);
            disconnectButton.setManaged(false);
            return;
        }

        String host = nvl(connectionData.getHost(), "?");
        String user = nvl(connectionData.getUsername(), "?");
        String port = connectionData.getPort() != null ? String.valueOf(connectionData.getPort()) : "22";
        infoLabel.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("msg.remote_terminal", "user: {0}   ip: {1}   port: {2}", user, host, port));
    }

    private static String nvl(String v, String fallback) {
        return (v != null && !v.trim().isEmpty()) ? v.trim() : fallback;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 终端初始化
    // ─────────────────────────────────────────────────────────────────────────

    private void initTerminal() {
        terminalBridge = new TerminalBridge(webEngine, terminalService);
        terminalBridge.setOnReadyCallback(() -> {
            logger.info("xterm.js 终端已就绪");
            boolean isDark = ThemeManager.getInstance().getCurrentTheme().isDark();
            terminalBridge.setTheme(isDark);
        });
        URL url = getClass().getResource("/com/opencgl/ssh/terminal/terminal.html");
        if (url != null) {
            webEngine.load(url.toExternalForm());
        } else {
            logger.error("无法找到 terminal.html");
        }

        // 选中本 Tab 时把焦点给终端并重新 fit，避免 vi 只显示半屏（初次显示时 WebView 可能尚未获得正确尺寸）
        selectedProperty().addListener((o, wasSelected, isSelected) -> {
            if (Boolean.TRUE.equals(isSelected)) {
                Platform.runLater(() -> {
                    terminalWebView.requestFocus();
                    if (terminalBridge != null) terminalBridge.fit();
                });
            }
        });

        // WebView 尺寸变化时延迟 fit（防抖），否则 HTML 的 window.resize 在 JavaFX WebView 中不会触发，vi 等会一直用初始小尺寸
        Runnable scheduleFit = () -> {
            if (fitDebounce != null) fitDebounce.stop();
            fitDebounce = new Timeline(new KeyFrame(Duration.millis(150), e -> {
                if (terminalBridge != null) terminalBridge.fit();
            }));
            fitDebounce.play();
        };
        terminalWebView.widthProperty().addListener((o, oldVal, newVal) -> {
            if (newVal != null && newVal.doubleValue() > 0) scheduleFit.run();
        });
        terminalWebView.heightProperty().addListener((o, oldVal, newVal) -> {
            if (newVal != null && newVal.doubleValue() > 0) scheduleFit.run();
        });

        // 监听全局主题切换
        terminalWebView.sceneProperty().addListener(sceneListener);

        // 监听全局语言切换以更新右键菜单
        com.opencgl.base.utils.i18n.BaseI18N.localeProperty().addListener(localeListener);
    }

    private void updateUITexts() {
        editButton.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("btn.edit", "✏️ 编辑"));
        connectButton.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("btn.connect", "🔗 连接"));
        disconnectButton.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("btn.disconnect", "❌ 断开"));
        if (terminalService != null && terminalService.isConnected()) {
            statusLabel.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("status.connected", "🟢 已连接"));
        } else {
            statusLabel.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("status.unconnected", "🔴 未连接"));
        }
        refreshInfoBar();
        // 如果 connectionData 为空，说明还在构造器初期（initUI 阶段），不应覆盖构造器传入的初始 title
        if (this.connectionData != null) {
            setText(buildTabTitle(this.connectionData));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 事件绑定
    // ─────────────────────────────────────────────────────────────────────────

    private void bindEvents() {
        editButton.setOnAction(e -> showEditDialog());
        connectButton.setOnAction(e -> connect());
        disconnectButton.setOnAction(e -> disconnect());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 连接操作
    // ─────────────────────────────────────────────────────────────────────────

    public void connect() {
        if (lifecycle.isDisposed()) {
            return;
        }
        if (terminalService instanceof SshService) {
            connectAsSsh();
        } else if (terminalService instanceof com.opencgl.ssh.service.LocalShellServiceImpl localService) {
            connectAsLocalShell(localService);
        } else {
            logger.warn("未知的 TerminalService 类型");
        }
    }

    private void connectAsSsh() {
        if (connectionData.getHost() == null || connectionData.getHost().trim().isEmpty()) {
            infoLabel.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("msg.unconfigured_hint", "未配置  —  点击「✏️ 编辑」填写连接信息"));
            editButton.setVisible(true);
            return; // Added return to prevent further execution if not configured
        }
        connectButton.setDisable(true);
        startConnectThread("ssh-terminal-connect", () -> {
            try {
                String host = connectionData.getHost();
                String user = connectionData.getUsername();
                int port = connectionData.getPort() != null ? connectionData.getPort() : 22;

                terminalBridge.write("\r\n\u001B[33m" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.connecting", "[正在连接 {0}@{1}:{2}]", user, host, String.valueOf(port)) + "\u001B[0m\r\n");

                String password = connectionData.getPassword();
                String key = connectionData.getPrivateKeyPath();

                SshService sshService = (SshService) terminalService;

                if (key != null && !key.trim().isEmpty()) {
                    sshService.connectWithKey(host, port, user, key, password, terminalBridge::write);
                } else {
                    sshService.connect(host, port, user, password, terminalBridge::write);
                }

                if (lifecycle.isDisposed()) {
                    terminalService.disconnect();
                    return;
                }

                // 连接成功后，立即下发哪怕是一瞬间早就结算出来的当前前端屏幕尺寸，以免 vi 显示残缺
                terminalService.setPtySize(terminalBridge.getCols(), terminalBridge.getRows());

                Platform.runLater(() -> {
                    if (lifecycle.isDisposed()) {
                        return;
                    }
                    terminalBridge.write("\u001B[32m" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.connected", "[连接成功]") + "\u001B[0m\r\n");
                    updateStatus();
                });
            } catch (Exception e) {
                if (lifecycle.isDisposed()) {
                    return;
                }
                logger.error("连接失败", e);
                Platform.runLater(() -> {
                    terminalBridge.write("\r\n\u001B[31m" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.connect_failed", "[连接失败: {0}]", e.getMessage()) + "\u001B[0m\r\n");
                    updateStatus();
                });
            }
        });
    }

    private void connectAsLocalShell(com.opencgl.ssh.service.LocalShellServiceImpl localService) {
        connectButton.setDisable(true);
        startConnectThread("local-terminal-connect", () -> {
            try {
                String shellName = nvl(connectionData.getHost(), "cmd.exe");
                if (shellName.startsWith("LOCAL:")) {
                    shellName = shellName.substring(6);
                }
                
                String[] cmd = new String[]{shellName};
                if (shellName.endsWith("bash") || shellName.endsWith("zsh")) {
                    cmd = new String[]{shellName, "-l"};
                }
                
                terminalBridge.write("\r\n\u001B[33m" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.starting_local", "[正在启动本地终端: {0}]", shellName) + "\u001B[0m\r\n");
                
                localService.connect(cmd, terminalBridge::write);

                if (lifecycle.isDisposed()) {
                    terminalService.disconnect();
                    return;
                }

                terminalService.setPtySize(terminalBridge.getCols(), terminalBridge.getRows());

                Platform.runLater(() -> {
                    if (lifecycle.isDisposed()) {
                        return;
                    }
                    terminalBridge.write("\u001B[32m" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.local_ready", "[本地终端就绪]") + "\u001B[0m\r\n");
                    updateStatus();
                    // 隐藏编辑按钮，因为本地终端不需要编辑连接信息
                    editButton.setVisible(false);
                    editButton.setManaged(false);
                });
            } catch (Exception e) {
                if (lifecycle.isDisposed()) {
                    return;
                }
                logger.error("启动本地终端失败", e);
                Platform.runLater(() -> {
                    terminalBridge.write("\r\n\u001B[31m" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.local_failed", "[启动失败: {0}]", e.getMessage()) + "\u001B[0m\r\n");
                    updateStatus();
                });
            }
        });
    }

    private synchronized void startConnectThread(String name, Runnable connectionTask) {
        if (lifecycle.isDisposed()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                connectionTask.run();
            } finally {
                synchronized (SshSessionTab.this) {
                    if (connectThread == Thread.currentThread()) {
                        connectThread = null;
                    }
                }
            }
        }, name);
        thread.setDaemon(true);
        connectThread = thread;
        thread.start();
    }

    public void disconnect() {
        terminalService.disconnect();
        if (terminalBridge != null) {
            terminalBridge.write("\r\n\u001B[33m" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.disconnected", "[已断开连接]") + "\u001B[0m\r\n");
        }
        updateStatus();
    }

    public void dispose() {
        lifecycle.dispose(error -> logger.warn("Failed to dispose SSH session resource", error),
                this::interruptConnectThread,
                terminalService::disconnect,
                this::awaitConnectThreadExit,
                () -> {
                    if (fitDebounce != null) {
                        fitDebounce.stop();
                        fitDebounce = null;
                    }
                },
                () -> com.opencgl.base.utils.i18n.BaseI18N.localeProperty().removeListener(localeListener),
                () -> {
                    Scene scene = terminalWebView.getScene();
                    if (scene != null) {
                        scene.getStylesheets().removeListener(stylesheetListener);
                    }
                    terminalWebView.sceneProperty().removeListener(sceneListener);
                },
                () -> {
                    if (terminalBridge != null) {
                        terminalBridge.dispose();
                        terminalBridge = null;
                    }
                },
                () -> setContent(null));
    }

    private void interruptConnectThread() {
        if (connectThread != null) {
            connectThread.interrupt();
        }
    }

    private void awaitConnectThreadExit() throws InterruptedException {
        Thread thread = connectThread;
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        thread.join(500L);
        if (thread.isAlive()) {
            logger.warn("SSH connection thread did not exit within 500 ms: {}", thread.getName());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 外部调用：填充配置（来自双击树节点）
    // ─────────────────────────────────────────────────────────────────────────

    public void fillConnectionFields(SshConnectionDto dto) {
        this.connectionData = dto;
        refreshInfoBar();
        setText(buildTabTitle(dto));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 状态更新
    // ─────────────────────────────────────────────────────────────────────────

    private void updateStatus() {
        Platform.runLater(() -> {
            if (terminalService.isConnected()) {
                statusLabel.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("status.connected", "🟢 已连接"));
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 13px;");
                connectButton.setDisable(true);
                disconnectButton.setDisable(false);
            } else {
                statusLabel.setText(com.opencgl.ssh.i18n.I18N.getOrDefault("status.unconnected", "🔴 未连接"));
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 13px;");
                connectButton.setDisable(false);
                disconnectButton.setDisable(true);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 样式辅助
    // ─────────────────────────────────────────────────────────────────────────

    private void styleEditButton(Button btn) {
        btn.setPrefHeight(30);
        if (!btn.getStyleClass().contains("secondary-button")) {
            btn.getStyleClass().add("secondary-button");
        }
    }

    private void stylePrimaryButton(Button btn, String color, double width) {
        btn.setPrefWidth(width);
        btn.setPrefHeight(30);
        btn.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 4;" +
                        "-fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
    }

    private void styleSecondaryButton(Button btn, double width) {
        btn.setPrefWidth(width);
        btn.setPrefHeight(30);
        if (!btn.getStyleClass().contains("secondary-button")) {
            btn.getStyleClass().add("secondary-button");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────────

    private static String buildTabTitle(SshConnectionDto dto) {
        if (dto == null) {
            return I18N.getOrDefault("tab.new_connection", "新连接");
        }
        
        // 1. 优先使用用户定义的名称（如果是本地终端，HOST 以 LOCAL: 开头，也优先用名字）
        String name = nvl(dto.getName(), "");
        if (!name.isEmpty()) {
            return name;
        }

        // 2. 本地终端兜底名：去掉 LOCAL: 前缀
        if (dto.getHost() != null && dto.getHost().startsWith("LOCAL:")) {
            return dto.getHost().substring(6);
        }

        // 3. 远程连接兜底名：user@host 或 host
        String user = nvl(dto.getUsername(), "");
        String host = nvl(dto.getHost(), "");
        if (!user.isEmpty() && !host.isEmpty())
            return user + "@" + host;
        if (!host.isEmpty())
            return host;
            
        // 4. 最终兜底
        return I18N.getOrDefault("tab.new_connection", "新连接");
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public boolean isConnected() {
        return terminalService.isConnected();
    }

    public SshConnectionDto getConnectionData() {
        return connectionData;
    }
}
