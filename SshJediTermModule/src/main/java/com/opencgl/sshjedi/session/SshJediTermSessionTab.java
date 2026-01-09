package com.opencgl.sshjedi.session;

import com.opencgl.base.theme.ThemeManager;
import com.opencgl.sshjedi.dao.SshConnectionDao;
import com.opencgl.sshjedi.i18n.I18N;
import com.opencgl.sshjedi.lifecycle.LifecycleDisposer;
import com.opencgl.sshjedi.model.SshConnectionDto;
import com.opencgl.sshjedi.service.ITerminalService;
import com.opencgl.sshjedi.service.SshService;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;


import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * SSH JediTermFX 会话 Tab
 */
public class SshJediTermSessionTab extends Tab {

    private static final Logger logger = LoggerFactory.getLogger(SshJediTermSessionTab.class);

    private final ITerminalService terminalService;
    private final SshConnectionDao dao = new SshConnectionDao();
    private JediTermFxWidget jediTermWidget;

    // 工具栏控件
    private final Label infoLabel = new Label();
    private final Button editButton = new Button();
    private final Button connectButton = new Button();
    private final Button disconnectButton = new Button();
    private final Label statusLabel = new Label();

    private SshConnectionDto connectionData;
    private TtyConnector currentConnector;
    private final LifecycleDisposer lifecycle = new LifecycleDisposer();
    private volatile Thread connectThread;
    private final ChangeListener<Locale> localeListener = (obs, oldVal, newVal) -> updateUITexts();
    private final ListChangeListener<String> stylesheetListener = change -> Platform.runLater(() -> {
        if (!lifecycle.isDisposed() && jediTermWidget != null && jediTermWidget.getTerminalPanel() != null) {
            jediTermWidget.getTerminalPanel().repaint();
        }
    });
    private final ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> {
        if (oldScene != null) {
            oldScene.getStylesheets().removeListener(stylesheetListener);
        }
        if (newScene != null) {
            newScene.getStylesheets().addListener(stylesheetListener);
        }
    };

    public SshJediTermSessionTab(String title, ITerminalService terminalService) {
        super(title);
        this.terminalService = terminalService;
        initTerminal();
        initUI();
        bindEvents();
        updateStatus();

        setOnClosed(e -> dispose());
    }

    public SshJediTermSessionTab(SshConnectionDto dto) {
        this(buildTabTitle(dto), new SshService());
        this.connectionData = dto;
        refreshInfoBar();
    }

    private void initTerminal() {
        jediTermWidget = new JediTermFxWidget(80, 24, new DefaultSettingsProvider() {
            private final com.techsenger.jeditermfx.core.TerminalColor dynamicFg = new com.techsenger.jeditermfx.core.TerminalColor(() -> 
                ThemeManager.getInstance().getCurrentTheme().isDark() ? 
                    new com.techsenger.jeditermfx.core.Color(204, 204, 204) : 
                    new com.techsenger.jeditermfx.core.Color(0, 0, 0)
            );
            
            private final com.techsenger.jeditermfx.core.TerminalColor dynamicBg = new com.techsenger.jeditermfx.core.TerminalColor(() -> 
                ThemeManager.getInstance().getCurrentTheme().isDark() ? 
                    new com.techsenger.jeditermfx.core.Color(30, 30, 30) : 
                    new com.techsenger.jeditermfx.core.Color(240, 240, 240)
            );

            @Override
            public com.techsenger.jeditermfx.core.TerminalColor getDefaultForeground() {
                return dynamicFg;
            }
            @Override
            public com.techsenger.jeditermfx.core.TerminalColor getDefaultBackground() {
                return dynamicBg;
            }
            @Override
            public com.techsenger.jeditermfx.core.TextStyle getDefaultStyle() {
                return new com.techsenger.jeditermfx.core.TextStyle(dynamicFg, dynamicBg);
            }
        });
        
        jediTermWidget.getPane().setStyle("-fx-background-color: -theme-bg-primary;");
        jediTermWidget.getPane().setFocusTraversable(true);
        VBox.setVgrow(jediTermWidget.getPane(), Priority.ALWAYS);

        // 监听全局语言切换
        com.opencgl.base.utils.i18n.BaseI18N.localeProperty().addListener(localeListener);

        // 监听全局主题切换，并利用 JediTermFxWidget 的 repaint 进行重绘刷新缓存颜色
        jediTermWidget.getPane().sceneProperty().addListener(sceneListener);
    }

    private void initUI() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: -theme-bg-primary;");

        HBox toolbar = buildToolbar();
        root.getChildren().addAll(toolbar, jediTermWidget.getPane());
        setContent(root);
        
        // Tab选中时赋予焦点
        selectedProperty().addListener((o, wasSelected, isSelected) -> {
            if (Boolean.TRUE.equals(isSelected)) {
                Platform.runLater(() -> {
                    if (jediTermWidget.getPreferredFocusableNode() != null) {
                        jediTermWidget.getPreferredFocusableNode().requestFocus();
                    } else {
                        jediTermWidget.getPane().requestFocus();
                    }
                });
            }
        });
    }

    private HBox buildToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle(
                "-fx-background-color: -theme-bg-secondary;" +
                        "-fx-border-color: transparent transparent -theme-border-color transparent;" +
                        "-fx-border-width: 0 0 1 0;");

        VBox infoBox = new VBox(2);
        updateUITexts();

        Label titleLabel = new Label();
        titleLabel.textProperty().bind(com.opencgl.sshjedi.i18n.I18N.getBinding("welcome.title"));
        titleLabel.setStyle("-fx-text-fill: -theme-text-secondary; -fx-font-size: 11px;");
        infoLabel.setStyle("-fx-text-fill: -theme-text-primary; -fx-font-size: 12px; -fx-font-family: monospace;");
        infoBox.getChildren().addAll(titleLabel, infoLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        styleEditButton(editButton);
        stylePrimaryButton(connectButton, "#27ae60", 75);
        stylePrimaryButton(disconnectButton, "#e74c3c", 75);

        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 13px;");
        statusLabel.setMinWidth(85);

        toolbar.getChildren().addAll(infoBox, editButton, new Separator(javafx.geometry.Orientation.VERTICAL),
                connectButton, disconnectButton, statusLabel);

        return toolbar;
    }

    private void showEditDialog() {
        com.opencgl.sshjedi.views.SshConfigDialog.showDialog(connectionData, updatedDto -> {
            this.connectionData = updatedDto;
            setText(buildTabTitle(updatedDto));
            refreshInfoBar();
        });
    }

    private void refreshInfoBar() {
        if (connectionData == null) {
            infoLabel.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("msg.unconfigured_hint", "未配置  —  点击「✏️ 编辑」填写连接信息"));
            return;
        }
        
        if (connectionData.getHost() != null && connectionData.getHost().startsWith("LOCAL:")) {
            String name = nvl(connectionData.getName(), connectionData.getHost().substring(6));
            infoLabel.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("msg.local_terminal", "本机终端: {0}", name));
            editButton.setVisible(false);
            editButton.setManaged(false);
            connectButton.setVisible(false);
            connectButton.setManaged(false);
            disconnectButton.setVisible(false);
            disconnectButton.setManaged(false);
            return;
        }

        String host = nvl(connectionData.getHost(), "?");
        String user = nvl(connectionData.getUsername(), "?");
        String port = connectionData.getPort() != null ? String.valueOf(connectionData.getPort()) : "22";
        infoLabel.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("msg.remote_terminal", "user: {0}   ip: {1}   port: {2}", user, host, port));
    }

    private static String nvl(String v, String fallback) {
        return (v != null && !v.trim().isEmpty()) ? v.trim() : fallback;
    }

    private void updateUITexts() {
        editButton.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("btn.edit", "✏️ 编辑"));
        connectButton.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("btn.connect", "🔗 连接"));
        disconnectButton.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("btn.disconnect", "❌ 断开"));
        if (currentConnector != null && currentConnector.isConnected()) {
            statusLabel.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("status.connected", "🟢 已连接"));
        } else {
            statusLabel.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("status.unconnected", "🔴 未连接"));
        }
        refreshInfoBar();
        if (this.connectionData != null) {
            setText(buildTabTitle(this.connectionData));
        }
    }

    private void bindEvents() {
        editButton.setOnAction(e -> showEditDialog());
        connectButton.setOnAction(e -> connect());
        disconnectButton.setOnAction(e -> disconnect());
    }

    public void connect() {
        if (lifecycle.isDisposed()) {
            return;
        }
        if (connectionData == null || (connectionData.getHost() == null || connectionData.getHost().trim().isEmpty())) {
            infoLabel.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("msg.unconfigured_hint", "未配置  —  点击「✏️ 编辑」填写连接信息"));
            editButton.setVisible(true);
            return;
        }
        connectButton.setDisable(true);
        startConnectThread("ssh-jediterm-connect", () -> {
            try {
                TtyConnector connector = terminalService.createTtyConnector(connectionData);
                synchronized (SshJediTermSessionTab.this) {
                    if (lifecycle.isDisposed()) {
                        connector.close();
                        return;
                    }
                    this.currentConnector = connector;
                }
                
                Platform.runLater(() -> {
                    if (lifecycle.isDisposed() || currentConnector != connector) {
                        return;
                    }
                    jediTermWidget.setTtyConnector(connector);
                    jediTermWidget.start();
                    if (jediTermWidget.getPreferredFocusableNode() != null) {
                        jediTermWidget.getPreferredFocusableNode().requestFocus();
                    } else {
                        jediTermWidget.getPane().requestFocus();
                    }
                    updateStatus();
                });
            } catch (Exception e) {
                if (lifecycle.isDisposed()) {
                    return;
                }
                logger.error("连接失败", e);
                Platform.runLater(() -> {
                    updateStatus();
                    showAlert(Alert.AlertType.ERROR, "连接失败: " + e.getMessage());
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
                synchronized (SshJediTermSessionTab.this) {
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
        closeConnectorSafely();
        stopWidgetSafely();
        closeWidgetSafely();
        updateStatus();
    }

    public void dispose() {
        lifecycle.dispose(error -> logger.warn("Failed to dispose SSH JediTerm session resource", error),
                this::interruptConnectThread,
                this::closeConnector,
                this::stopWidget,
                () -> com.opencgl.base.utils.i18n.BaseI18N.localeProperty().removeListener(localeListener),
                () -> {
                    if (jediTermWidget != null) {
                        Scene scene = jediTermWidget.getPane().getScene();
                        if (scene != null) {
                            scene.getStylesheets().removeListener(stylesheetListener);
                        }
                        jediTermWidget.getPane().sceneProperty().removeListener(sceneListener);
                    }
                },
                this::closeWidget,
                this::awaitConnectThreadExit,
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
            logger.warn("SSH JediTerm connection thread did not exit within 500 ms: {}", thread.getName());
        }
    }

    private synchronized void closeConnector() {
        TtyConnector connector = currentConnector;
        currentConnector = null;
        if (connector != null) {
            connector.close();
        }
    }

    private void stopWidget() {
        if (jediTermWidget != null) {
            jediTermWidget.stop();
        }
    }

    private void closeWidget() throws Exception {
        if (jediTermWidget != null) {
            jediTermWidget.close();
            jediTermWidget = null;
        }
    }

    private void closeConnectorSafely() {
        try {
            closeConnector();
        } catch (RuntimeException error) {
            logger.warn("Error closing SSH terminal connector", error);
        }
    }

    private void stopWidgetSafely() {
        try {
            stopWidget();
        } catch (RuntimeException error) {
            logger.warn("Error stopping SSH terminal widget", error);
        }
    }

    private void closeWidgetSafely() {
        try {
            closeWidget();
        } catch (Exception error) {
            logger.warn("Error closing SSH terminal widget", error);
        }
    }

    public void fillConnectionFields(SshConnectionDto dto) {
        this.connectionData = dto;
        refreshInfoBar();
        setText(buildTabTitle(dto));
    }

    private void updateStatus() {
        Platform.runLater(() -> {
            boolean connected = currentConnector != null && currentConnector.isConnected();
            if (connected) {
                statusLabel.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("status.connected", "🟢 已连接"));
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 13px;");
                connectButton.setDisable(true);
                disconnectButton.setDisable(false);
            } else {
                statusLabel.setText(com.opencgl.sshjedi.i18n.I18N.getOrDefault("status.unconnected", "🔴 未连接"));
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 13px;");
                connectButton.setDisable(false);
                disconnectButton.setDisable(true);
            }
        });
    }

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

    private static String buildTabTitle(SshConnectionDto dto) {
        if (dto == null) {
            return I18N.getOrDefault("tab.new_connection", "新连接");
        }
        
        String name = nvl(dto.getName(), "");
        if (!name.isEmpty()) return name;

        if (dto.getHost() != null && dto.getHost().startsWith("LOCAL:")) {
            return dto.getHost().substring(6);
        }

        String user = nvl(dto.getUsername(), "");
        String host = nvl(dto.getHost(), "");
        if (!user.isEmpty() && !host.isEmpty()) return user + "@" + host;
        if (!host.isEmpty()) return host;
            
        return I18N.getOrDefault("tab.new_connection", "新连接");
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public boolean isConnected() {
        return currentConnector != null && currentConnector.isConnected();
    }

    public SshConnectionDto getConnectionData() {
        return connectionData;
    }
}
