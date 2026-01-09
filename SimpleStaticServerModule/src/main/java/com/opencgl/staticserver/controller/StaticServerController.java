package com.opencgl.staticserver.controller;

import com.opencgl.staticserver.i18n.I18N;
import com.opencgl.staticserver.service.HttpServerService;
import com.opencgl.staticserver.views.StaticServerView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.Initializable;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;
import java.util.ResourceBundle;

/**
 * 静态文件服务器控制器
 */
public class StaticServerController extends StaticServerView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(StaticServerController.class);
    
    private final HttpServerService serverService = new HttpServerService();
    private final ObservableList<String> directories = FXCollections.observableArrayList();
    private volatile boolean disposed;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化目录列表
        directoryListView.setItems(directories);
        
        // 初始化IP列表
        initializeIpList();
        
        // 默认端口
        portField.setText("8080");
        
        // 初始状态
        updateStatus(false);
        qrCodeBox.setVisible(false);
        
        // 绑定事件
        selectDirButton.setOnAction(e -> selectDirectory());
        //selectMultiDirButton.setOnAction(e -> selectMultipleDirectories());
        removeDirButton.setOnAction(e -> removeSelectedDirectory());
        startButton.setOnAction(e -> startServer());
        stopButton.setOnAction(e -> stopServer());
        copyUrlButton.setOnAction(e -> copyUrl());
        urlLink.setOnAction(e -> openUrl());
        initI18n();
    }

    private void initI18n() {
        // FXML text resolved via %key; statusLabel and messages set at runtime with I18N
    }
    
    private void selectDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18N.get("dialog.choose_dir"));
        File dir = chooser.showDialog(rootPane.getScene().getWindow());
        if (dir != null && dir.isDirectory()) {
            String path = dir.getAbsolutePath();
            if (!directories.contains(path)) {
                directories.add(path);
            }
        }
    }
    
    private void selectMultipleDirectories() {
        // 使用FileChooser选择多个文件，然后获取其父目录
        // 或者循环让用户多次选择目录
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18N.get("dialog.choose_dir_multi"));
        while (true) {
            File dir = chooser.showDialog(rootPane.getScene().getWindow());
            if (dir == null) break; // 用户取消则退出循环
            if (dir.isDirectory()) {
                String path = dir.getAbsolutePath();
                if (!directories.contains(path)) {
                    directories.add(path);
                }
                chooser.setInitialDirectory(dir.getParentFile()); // 记住上次选择的位置
            }
        }
    }
    
    private void initializeIpList() {
        ObservableList<String> ipList = FXCollections.observableArrayList();
        ipList.add("0.0.0.0 (所有网卡)");
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                
                Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        String displayName = ni.getDisplayName();
                        ipList.add(ip + " (" + displayName + ")");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("获取网络接口失败", e);
        }
        
        ipComboBox.setItems(ipList);
        ipComboBox.getSelectionModel().selectFirst(); // 默认选择0.0.0.0
    }
    
    private void removeSelectedDirectory() {
        int idx = directoryListView.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            directories.remove(idx);
        }
    }
    
    private void startServer() {
        if (directories.isEmpty()) {
            showError(I18N.get("msg.select_one_dir"));
            return;
        }
        
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                showError(I18N.get("msg.port_range"));
                return;
            }
            
            // 获取选中的IP
            String selectedIp = "0.0.0.0";
            String ipSelection = ipComboBox.getSelectionModel().getSelectedItem();
            if (ipSelection != null) {
                // 提取IP地址（格式："192.168.1.100 (eth0)"）
                String[] parts = ipSelection.split(" ");
                if (parts.length > 0) {
                    selectedIp = parts[0];
                }
            }
            
            serverService.setBindAddress(selectedIp);
            serverService.setPort(port);
            serverService.setRootDirs(directories);
            serverService.start();
            
            updateStatus(true);
            generateQrCode();
            
        } catch (NumberFormatException e) {
            showError(I18N.get("msg.invalid_port"));
        } catch (Exception e) {
            logger.error("启动服务器失败", e);
            showError(I18N.get("msg.start_failed", e.getMessage() != null ? e.getMessage() : ""));
        }
    }
    
    private void stopServer() {
        try {
            serverService.stop();
            updateStatus(false);
            qrCodeBox.setVisible(false);
        } catch (Exception e) {
            logger.error("停止服务器失败", e);
            showError(I18N.get("msg.stop_failed", e.getMessage() != null ? e.getMessage() : ""));
        }
    }
    
    private void updateStatus(boolean running) {
        Platform.runLater(() -> {
            if (disposed) return;
            if (running) {
                String url = serverService.getAccessUrl();
                statusLabel.setText(I18N.get("status.running"));
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                urlLink.setText(url);
                urlLink.setVisible(true);
                copyUrlButton.setVisible(true);
                startButton.setDisable(true);
                stopButton.setDisable(false);
                portField.setDisable(true);
                selectDirButton.setDisable(true);
               // selectMultiDirButton.setDisable(true);
                removeDirButton.setDisable(true);
            } else {
                statusLabel.setText(I18N.get("status.stopped"));
                statusLabel.setStyle("-fx-text-fill: #7f8c8d;");
                urlLink.setVisible(false);
                copyUrlButton.setVisible(false);
                startButton.setDisable(false);
                stopButton.setDisable(true);
                portField.setDisable(false);
                selectDirButton.setDisable(false);
               // selectMultiDirButton.setDisable(false);
                removeDirButton.setDisable(false);
            }
        });
    }

    public void dispose() {
        disposed = true;
        try {
            serverService.stop();
        } catch (RuntimeException e) {
            logger.error("释放静态文件服务器失败", e);
        }
    }
    
    private void generateQrCode() {
        try {
            String url = serverService.getAccessUrl();
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 200, 200);
            Image image = SwingFXUtils.toFXImage(MatrixToImageWriter.toBufferedImage(matrix), null);
            
            Platform.runLater(() -> {
                qrCodeImage.setImage(image);
                qrCodeBox.setVisible(true);
            });
        } catch (WriterException e) {
            logger.error("生成二维码失败", e);
        }
    }
    
    private void copyUrl() {
        String url = serverService.getAccessUrl();
        ClipboardContent content = new ClipboardContent();
        content.putString(url);
        Clipboard.getSystemClipboard().setContent(content);
        showToast(I18N.get("msg.copied"));
    }
    
    private void openUrl() {
        try {
            String url = serverService.getAccessUrl();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            logger.error("打开浏览器失败", e);
            showError(I18N.get("msg.cannot_open_browser"));
        }
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("⚠️ " + message);
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
        });
    }
    
    private void showToast(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("✓ " + message);
            statusLabel.setStyle("-fx-text-fill: #27ae60;");
        });
    }
}
