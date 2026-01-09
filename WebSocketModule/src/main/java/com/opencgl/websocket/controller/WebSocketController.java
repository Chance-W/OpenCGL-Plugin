package com.opencgl.websocket.controller;

import com.opencgl.websocket.i18n.I18N;
import com.opencgl.websocket.service.WebSocketService;
import com.opencgl.websocket.service.WebSocketService.MessageInfo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * WebSocket 测试控制器
 *
 * @author OpenCGL
 */
public class WebSocketController implements Initializable {

    @FXML private TextField urlField;
    @FXML private TextField headerKeyField;
    @FXML private TextField headerValueField;
    @FXML private Button connectBtn;
    @FXML private Button disconnectBtn;
    @FXML private Label connectionStatusLabel;
    
    @FXML private TextArea messageArea;
    @FXML private TextArea historyArea;

    private final WebSocketService wsService = new WebSocketService();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-websocket-connect");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Future<?> connectTask;
    private volatile boolean disposed;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        urlField.setText("ws://localhost:8080/ws");
        disconnectBtn.setDisable(true);
        
        wsService.setOnMessage(msg -> Platform.runLater(() -> {
            if (!disposed) appendMessage(msg);
        }));
        wsService.setOnStatus(status -> Platform.runLater(() -> {
            if (disposed) return;
            connectionStatusLabel.setText(status);
            boolean connected = wsService.isConnected();
            connectBtn.setDisable(connected);
            disconnectBtn.setDisable(!connected);
        }));
        initI18n();
    }

    private void initI18n() {
        // Static texts are in FXML with %key; status messages use I18N.get() at runtime
    }

    @FXML
    private void onConnect() {
        String wsUrl = urlField.getText();
        if (wsUrl.isEmpty()) {
            connectionStatusLabel.setText(I18N.get("msg.pleaseEnterUrl"));
            return;
        }
        
        connectBtn.setDisable(true);
        connectionStatusLabel.setText(I18N.get("msg.connecting"));
        
        connectTask = executor.submit(() -> {
            if (disposed) return;
            try {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                String key = headerKeyField.getText();
                String value = headerValueField.getText();
                if (key != null && !key.isEmpty()) {
                    headers.put(key, value);
                }
                
                wsService.connect(wsUrl, headers);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    connectionStatusLabel.setText("✗ " + e.getMessage());
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    connectBtn.setDisable(false);
                });
            }
        });
    }

    @FXML
    private void onDisconnect() {
        wsService.disconnect();
        connectionStatusLabel.setText(I18N.get("msg.disconnected"));
        connectBtn.setDisable(false);
        disconnectBtn.setDisable(true);
    }

    @FXML
    private void onSend() {
        String message = messageArea.getText();
        if (message == null || message.isEmpty()) {
            return;
        }
        
        try {
            wsService.send(message);
            messageArea.clear();
        } catch (Exception e) {
            connectionStatusLabel.setText(I18N.get("msg.sendFailed", e.getMessage()));
        }
    }

    @FXML
    private void onClearHistory() {
        historyArea.clear();
        wsService.clearHistory();
    }

    private void appendMessage(MessageInfo msg) {
        String time = Instant.ofEpochMilli(msg.timestamp())
            .atZone(ZoneId.systemDefault())
            .format(timeFmt);
        
        String prefix = "SEND".equals(msg.type()) ? ">>> " : "<<< ";
        String line = String.format("[%s] %s%s\n", time, prefix, msg.content());
        
        historyArea.appendText(line);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        Future<?> task = connectTask;
        connectTask = null;
        if (task != null) task.cancel(true);
        wsService.dispose();
        executor.shutdownNow();
    }
}
