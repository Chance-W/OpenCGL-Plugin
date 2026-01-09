package com.opencgl.redis.controller;

import com.opencgl.redis.components.RedisKeyBrowser;
import com.opencgl.redis.components.RedisTtyConnector;
import com.opencgl.redis.model.RedisWidgetDto;
import com.opencgl.redis.service.RedisConnectionManager;
import com.opencgl.redis.i18n.I18N;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;

/**
 * Controller for an individual Redis Connection Session Tab
 */
public class RedisSessionController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(RedisSessionController.class);

    @FXML private VBox root;
    @FXML private HBox statusBar;
    @FXML private Label connectionStatusLabel;
    @FXML private Label serverInfoLabel;
    @FXML private MFXButton disconnectBtn;
    
    @FXML private TabPane contentTabPane;
    @FXML private Tab dataBrowserTab;
    @FXML private Tab cliTab;
    @FXML private Tab serverInfoTab;
    @FXML private AnchorPane dataBrowserPane;
    @FXML private AnchorPane cliTerminalPane;
    @FXML private TextArea serverInfoArea;
    @FXML private MFXButton refreshInfoBtn;

    private RedisConnectionManager connectionManager;
    private RedisKeyBrowser keyBrowser;
    private JediTermFxWidget terminalWidget;
    private RedisWidgetDto currentConnection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-redis-session");
        thread.setDaemon(true);
        return thread;
    });
    private boolean disconnected;
    
    // Default constructor is called by FXML Loader
    public RedisSessionController() {
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        bindEvents();
    }

    private void initI18n() {
        // connectionStatusLabel 不能 bind，因为后续要动态 setText
        connectionStatusLabel.setText(I18N.get("status.unconnected"));
        disconnectBtn.textProperty().bind(I18N.getBinding("button.disconnect"));
        refreshInfoBtn.textProperty().bind(I18N.getBinding("button.refresh_info"));

        // Tab 标题通过 @FXML 字段绑定，避免与 FXML %key 冲突
        if (dataBrowserTab != null) dataBrowserTab.textProperty().bind(I18N.getBinding("tab.data_browser"));
        if (cliTab != null) cliTab.textProperty().bind(I18N.getBinding("tab.cli"));
        if (serverInfoTab != null) serverInfoTab.textProperty().bind(I18N.getBinding("tab.server_info"));
    }

    private void bindEvents() {
        disconnectBtn.setOnAction(e -> disconnect());
        refreshInfoBtn.setOnAction(e -> refreshServerInfo());
    }

    public void initSession(RedisWidgetDto connectionData, Runnable onDisconnect) {
        this.currentConnection = connectionData;
        this.serverInfoLabel.setText(connectionData.getDisplayName());
        this.connectionManager = new RedisConnectionManager();
        this.connectionManager.setConfig(connectionData);

        // Customize disconnect event if parent needs to know (e.g. to close the tab)
        disconnectBtn.setOnAction(e -> {
            disconnect();
            if (onDisconnect != null) {
                onDisconnect.run();
            }
        });

        connectToRedis();
    }

    private void connectToRedis() {
        executor.execute(() -> {
            boolean success = connectionManager.connect();
            Platform.runLater(() -> {
                if (disconnected) {
                    connectionManager.disconnect();
                    return;
                }
                if (success) {
                    connectionStatusLabel.setText(I18N.get("label.connected"));
                    connectionStatusLabel.setStyle("-fx-text-fill: -theme-success; -fx-font-weight: bold;");
                    disconnectBtn.setDisable(false);
                    initComponents();
                } else {
                    connectionStatusLabel.setText(I18N.get("status.unconnected"));
                    connectionStatusLabel.setStyle("-fx-text-fill: -theme-danger; -fx-font-weight: bold;");
                    disconnectBtn.setDisable(true);
                }
            });
        });
    }

    private void initComponents() {
        // Init Key Browser
        keyBrowser = new RedisKeyBrowser(connectionManager);
        dataBrowserPane.getChildren().clear();
        dataBrowserPane.getChildren().add(keyBrowser);
        AnchorPane.setTopAnchor(keyBrowser, 0.0);
        AnchorPane.setBottomAnchor(keyBrowser, 0.0);
        AnchorPane.setLeftAnchor(keyBrowser, 0.0);
        AnchorPane.setRightAnchor(keyBrowser, 0.0);
        keyBrowser.loadKeys("*");

        // Init CLI Terminal using JediTermFxWidget with theme-aware colors
        com.techsenger.jeditermfx.core.TerminalColor dynamicFg = new com.techsenger.jeditermfx.core.TerminalColor(() ->
            com.opencgl.base.theme.ThemeManager.getInstance().getCurrentTheme().isDark() ?
                new com.techsenger.jeditermfx.core.Color(204, 204, 204) :
                new com.techsenger.jeditermfx.core.Color(0, 0, 0)
        );
        com.techsenger.jeditermfx.core.TerminalColor dynamicBg = new com.techsenger.jeditermfx.core.TerminalColor(() ->
            com.opencgl.base.theme.ThemeManager.getInstance().getCurrentTheme().isDark() ?
                new com.techsenger.jeditermfx.core.Color(30, 30, 30) :
                new com.techsenger.jeditermfx.core.Color(240, 240, 240)
        );

        terminalWidget = new JediTermFxWidget(80, 24, new DefaultSettingsProvider() {
            @Override
            public com.techsenger.jeditermfx.core.TerminalColor getDefaultForeground() { return dynamicFg; }
            @Override
            public com.techsenger.jeditermfx.core.TerminalColor getDefaultBackground() { return dynamicBg; }
            @Override
            public com.techsenger.jeditermfx.core.TextStyle getDefaultStyle() {
                return new com.techsenger.jeditermfx.core.TextStyle(dynamicFg, dynamicBg);
            }
        });

        javafx.scene.layout.Pane termPane = terminalWidget.getPane();
        termPane.setStyle("-fx-background-color: -theme-bg-primary;");
        termPane.setFocusTraversable(true);

        // ① 先把 pane 加到 UI（此时确保其进入 Scene 树）
        cliTerminalPane.getChildren().clear();
        cliTerminalPane.getChildren().add(termPane);
        AnchorPane.setTopAnchor(termPane, 0.0);
        AnchorPane.setBottomAnchor(termPane, 0.0);
        AnchorPane.setLeftAnchor(termPane, 0.0);
        AnchorPane.setRightAnchor(termPane, 0.0);

        // ② 安全启动终端（修复输入法 NPE：确保 terminal.start() 一定被调用）
        Runnable starter = () -> {
            if (terminalWidget.getTtyConnector() != null) return; // 防止重复启动
            terminalWidget.setTtyConnector(new RedisTtyConnector(connectionManager));
            terminalWidget.start();
            javafx.scene.Node focusNode = terminalWidget.getPreferredFocusableNode();
            if (focusNode != null) {
                focusNode.requestFocus();
            } else {
                termPane.requestFocus();
            }
        };

        if (termPane.getScene() != null) {
            Platform.runLater(starter);
        } else {
            termPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    Platform.runLater(starter);
                }
            });
        }

        // ③ Tab 切换时也请求焦点
        contentTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && cliTerminalPane.equals(newTab.getContent())) {
                Platform.runLater(() -> {
                    javafx.scene.Node focusNode = terminalWidget.getPreferredFocusableNode();
                    if (focusNode != null) focusNode.requestFocus();
                    else termPane.requestFocus();
                });
            }
        });

        refreshServerInfo();
    }

    private void refreshServerInfo() {
        if (connectionManager != null && connectionManager.isConnected()) {
            String info = connectionManager.getServerInfo();
            serverInfoArea.setText(info);
        }
    }

    public void disconnect() {
        if (disconnected) {
            return;
        }
        disconnected = true;
        if (keyBrowser != null) {
            keyBrowser.dispose();
            keyBrowser = null;
        }
        if (terminalWidget != null) {
            terminalWidget.stop();
            try {
                terminalWidget.close();
            } catch (Exception e) {
                log.warn("Error closing terminal widget", e);
            }
        }
        if (connectionManager != null) {
            connectionManager.disconnect();
        }
        executor.shutdownNow();
        connectionStatusLabel.setText(I18N.get("status.unconnected"));
        connectionStatusLabel.setStyle("-fx-text-fill: -theme-danger; -fx-font-weight: bold;");
        disconnectBtn.setDisable(true);
    }

    public VBox getRoot() {
        return root;
    }
}
