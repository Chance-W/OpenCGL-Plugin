package com.opencgl.portscan.controller;

import com.opencgl.portscan.i18n.I18N;
import com.opencgl.portscan.service.LocalPortService;
import com.opencgl.portscan.service.LocalPortService.LocalPortInfo;
import com.opencgl.portscan.service.PortScanService;
import com.opencgl.portscan.service.PortScanService.*;
import com.opencgl.portscan.views.PortScannerView;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 端口扫描控制器
 */
public class PortScannerController extends PortScannerView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(PortScannerController.class);
    
    private final PortScanService portScanService = new PortScanService();
    private final LocalPortService localPortService = new LocalPortService();
    
    private final ObservableList<ScanResultRow> scanResults = FXCollections.observableArrayList();
    private final ObservableList<LocalPortRow> localPorts = FXCollections.observableArrayList();
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "port-scanner-background");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<CompletableFuture<?>> backgroundTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTables();
        bindEvents();
        initI18n();
        setStatus(I18N.get("status.ready"));
    }

    private void initI18n() {
    }
    
    @SuppressWarnings("unchecked")
    private void setupTables() {
        // 扫描结果表
        TableView<ScanResultRow> scanTable = (TableView<ScanResultRow>) scanResultTable;
        scanTable.setItems(scanResults);
        
        TableColumn<ScanResultRow, Integer> portCol = (TableColumn<ScanResultRow, Integer>) scanTable.getColumns().get(0);
        portCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().port()).asObject());
        
        TableColumn<ScanResultRow, String> statusCol = (TableColumn<ScanResultRow, String>) scanTable.getColumns().get(1);
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        
        TableColumn<ScanResultRow, Long> latencyCol = (TableColumn<ScanResultRow, Long>) scanTable.getColumns().get(2);
        latencyCol.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().latency()).asObject());
        
        TableColumn<ScanResultRow, String> noteCol = (TableColumn<ScanResultRow, String>) scanTable.getColumns().get(3);
        noteCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().note()));
        
        // 本机端口表
        TableView<LocalPortRow> localTable = (TableView<LocalPortRow>) localPortTable;
        localTable.setItems(localPorts);
        
        TableColumn<LocalPortRow, Integer> lpPortCol = (TableColumn<LocalPortRow, Integer>) localTable.getColumns().get(0);
        lpPortCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().port()).asObject());
        
        TableColumn<LocalPortRow, String> lpProtoCol = (TableColumn<LocalPortRow, String>) localTable.getColumns().get(1);
        lpProtoCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().protocol()));
        
        TableColumn<LocalPortRow, String> lpProcCol = (TableColumn<LocalPortRow, String>) localTable.getColumns().get(2);
        lpProcCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().process()));
        
        TableColumn<LocalPortRow, String> lpPidCol = (TableColumn<LocalPortRow, String>) localTable.getColumns().get(3);
        lpPidCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().pid()));
        
        TableColumn<LocalPortRow, String> lpUserCol = (TableColumn<LocalPortRow, String>) localTable.getColumns().get(4);
        lpUserCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().user()));
        
        TableColumn<LocalPortRow, String> lpStateCol = (TableColumn<LocalPortRow, String>) localTable.getColumns().get(5);
        lpStateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().state()));
    }
    
    private void bindEvents() {
        testButton.setOnAction(e -> onTestPort());
        pingButton.setOnAction(e -> onPing());
        scanButton.setOnAction(e -> onStartScan());
        stopScanButton.setOnAction(e -> onStopScan());
        refreshLocalButton.setOnAction(e -> onRefreshLocal());
    }
    
    private void onTestPort() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        
        if (host.isEmpty() || portStr.isEmpty()) {
            appendResult(I18N.get("msg.enterHostPort"));
            return;
        }
        
        try {
            int port = Integer.parseInt(portStr);
            setStatus(I18N.get("msg.connecting"));
            
            submitBackground(() -> portScanService.testPort(host, port))
                .thenAccept(result -> runOnFxIfActive(() -> {
                    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String statusStr = result.isOpen() ? I18N.get("msg.connectSuccess") : I18N.get("msg.connectFailed");
                    String msg = String.format("[%s] %s:%d - %s (延迟: %dms)%n",
                        time, result.host(), result.port(), statusStr, result.latency());
                    appendResult(msg);
                    setStatus(statusStr);
                })).exceptionally(ex -> {
                    runOnFxIfActive(() -> {
                        appendResult(I18N.get("msg.testError") + ": " + ex.getMessage() + "\n");
                        setStatus(I18N.get("msg.testError"));
                        logger.error("端口测试异常", ex);
                    });
                    return null;
                });
        } catch (NumberFormatException e) {
            appendResult(I18N.get("msg.portMustNumber"));
        }
    }
    
    private void onPing() {
        String host = pingHostField.getText().trim();
        if (host.isEmpty()) {
            appendPingResult(I18N.get("msg.enterHost"));
            return;
        }
        
        setStatus(I18N.get("msg.pinging"));
        pingResultArea.clear();
        appendPingResult("Ping " + host + " ...\n");
        
        submitBackground(() -> portScanService.ping(host, 4))
            .thenAccept(result -> runOnFxIfActive(() -> {
                String msg = String.format(
                    "--- %s ping 统计 ---\n" +
                    "发送: %d, 成功: %d, 丢失: %.0f%%\n" +
                    "平均延迟: %.2f ms\n",
                    result.host(), result.count(), result.success(),
                    result.packetLoss(), result.avgLatency());
                appendPingResult(msg);
                setStatus(I18N.get("msg.pingComplete"));
            })).exceptionally(ex -> {
                runOnFxIfActive(() -> {
                    appendPingResult(I18N.get("msg.pingError") + ": " + ex.getMessage() + "\n");
                    setStatus(I18N.get("msg.pingError"));
                    logger.error("Ping 异常", ex);
                });
                return null;
            });
    }
    
    private void onStartScan() {
        String host = scanHostField.getText().trim();
        int startPort, endPort;
        
        try {
            startPort = Integer.parseInt(startPortField.getText().trim());
            endPort = Integer.parseInt(endPortField.getText().trim());
        } catch (NumberFormatException e) {
            setStatus(I18N.get("msg.portMustNumber"));
            return;
        }
        
        if (startPort < 1 || endPort > 65535 || startPort > endPort) {
            setStatus(I18N.get("msg.portRangeInvalid"));
            return;
        }
        
        scanResults.clear();
        scanButton.setDisable(true);
        stopScanButton.setDisable(false);
        scanProgress.setProgress(0);
        setStatus(I18N.get("msg.scanning"));
        
        portScanService.scanPorts(host, startPort, endPort,
            progress -> runOnFxIfActive(() -> scanProgress.setProgress(progress.getProgress())),
            result -> runOnFxIfActive(() -> {
                String note = getPortService(result.port());
                scanResults.add(new ScanResultRow(result.port(), I18N.get("status.open"), result.latency(), note));
            }),
            () -> runOnFxIfActive(() -> {
                scanButton.setDisable(false);
                stopScanButton.setDisable(true);
                setStatus(I18N.get("msg.scanComplete", scanResults.size()));
            })
        );
    }
    
    private void onStopScan() {
        portScanService.stopScan();
        scanButton.setDisable(false);
        stopScanButton.setDisable(true);
        setStatus(I18N.get("msg.scanStopped"));
    }
    
    private void onRefreshLocal() {
        setStatus(I18N.get("msg.fetchingLocal"));
        localPorts.clear();
        
        submitBackground(() -> localPortService.getLocalPorts())
            .thenAccept(ports -> runOnFxIfActive(() -> {
                for (LocalPortInfo info : ports) {
                    localPorts.add(new LocalPortRow(info.port(), info.protocol(), 
                        info.process(), info.pid(), info.user(), info.state()));
                }
                setStatus(I18N.get("msg.localPortsFound", ports.size()));
            })).exceptionally(ex -> {
                runOnFxIfActive(() -> {
                    setStatus(I18N.get("msg.localPortsFailed"));
                    logger.error("获取本机端口异常", ex);
                });
                return null;
            });
    }
    
    private void appendResult(String text) {
        resultArea.appendText(text);
    }
    
    private void appendPingResult(String text) {
        pingResultArea.appendText(text);
    }
    
    private void setStatus(String status) {
        statusLabel.setText(status);
    }
    
    private String getPortService(int port) {
        return switch (port) {
            case 21 -> "FTP";
            case 22 -> "SSH";
            case 23 -> "Telnet";
            case 25 -> "SMTP";
            case 53 -> "DNS";
            case 80 -> "HTTP";
            case 110 -> "POP3";
            case 143 -> "IMAP";
            case 443 -> "HTTPS";
            case 3306 -> "MySQL";
            case 3389 -> "RDP";
            case 5432 -> "PostgreSQL";
            case 6379 -> "Redis";
            case 8080 -> "HTTP-Alt";
            case 27017 -> "MongoDB";
            default -> "";
        };
    }

    private <T> CompletableFuture<T> submitBackground(Supplier<T> operation) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(operation, backgroundExecutor);
        backgroundTasks.add(future);
        future.whenComplete((ignored, error) -> backgroundTasks.remove(future));
        return future;
    }

    private void runOnFxIfActive(Runnable action) {
        Platform.runLater(() -> {
            if (!disposed) action.run();
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            portScanService.dispose();
        } catch (RuntimeException e) {
            logger.warn("Failed to stop active scan", e);
        }
        try {
            localPortService.dispose();
        } catch (RuntimeException e) {
            logger.warn("Failed to stop local port inspection", e);
        }
        for (CompletableFuture<?> task : backgroundTasks) {
            try {
                task.cancel(true);
            } catch (RuntimeException e) {
                logger.warn("Failed to cancel background task", e);
            }
        }
        backgroundTasks.clear();
        backgroundExecutor.shutdownNow();
    }
    
    // 表格行数据类
    public record ScanResultRow(int port, String status, long latency, String note) {}
    public record LocalPortRow(int port, String protocol, String process, String pid, String user, String state) {}
}
