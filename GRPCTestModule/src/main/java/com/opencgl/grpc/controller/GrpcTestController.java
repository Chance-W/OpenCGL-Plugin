package com.opencgl.grpc.controller;

import com.opencgl.grpc.i18n.I18N;
import com.opencgl.grpc.service.GrpcService;
import com.opencgl.grpc.service.GrpcService.MethodInfo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * gRPC 测试工具控制器
 *
 * @author OpenCGL
 */
public class GrpcTestController implements Initializable {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private CheckBox plaintextCheckBox;
    @FXML private Button connectBtn;
    @FXML private Label connectionStatusLabel;
    
    @FXML private ListView<String> serviceListView;
    @FXML private ListView<String> methodListView;
    @FXML private Label methodInfoLabel;
    
    @FXML private TextArea requestArea;
    @FXML private TextArea responseArea;
    @FXML private TextField timeoutField;
    @FXML private Label callStatusLabel;

    private final GrpcService grpcService = new GrpcService();
    private boolean isConnected = false;
    private String selectedService;
    private String selectedMethod;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        hostField.setText("localhost");
        portField.setText("50051");
        timeoutField.setText("30");
        plaintextCheckBox.setSelected(true);
        
        serviceListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedService = newVal;
                refreshMethods();
            }
        });
        
        methodListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedMethod = newVal;
                showMethodInfo();
                loadRequestTemplate();
            }
        });
        initI18n();
    }

    private void initI18n() {
        // Static texts are in FXML with %key; status messages use I18N.get() at runtime
    }

    @FXML
    private void onConnect() {
        String host = hostField.getText();
        int port = Integer.parseInt(portField.getText());
        boolean usePlaintext = plaintextCheckBox.isSelected();
        
        connectBtn.setDisable(true);
        connectionStatusLabel.setText(I18N.get("msg.connecting"));
        
        CompletableFuture.runAsync(() -> {
            grpcService.connect(host, port, usePlaintext);
            boolean success = grpcService.testConnection();
            Platform.runLater(() -> {
                if (success) {
                    isConnected = true;
                    connectionStatusLabel.setText(I18N.get("msg.connected"));
                    connectionStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                } else {
                    connectionStatusLabel.setText(I18N.get("msg.connectFailed"));
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                }
                connectBtn.setDisable(false);
            });
        });
    }

    @FXML
    private void onLoadProto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.get("msg.selectProtoFile"));
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(I18N.get("msg.protoFilter"), "*.desc", "*.pb", "*.bin")
        );
        
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    grpcService.loadProtoDescriptor(bytes);
                    List<String> services = grpcService.listServices();
                    Platform.runLater(() -> {
                        serviceListView.getItems().clear();
                        serviceListView.getItems().addAll(services);
                        connectionStatusLabel.setText(I18N.get("msg.loadedServices", services.size()));
                        connectionStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        responseArea.setText(I18N.get("msg.loadProtoFailed", e.getMessage()));
                    });
                }
            });
        }
    }

    private void refreshMethods() {
        if (selectedService == null) return;
        
        List<MethodInfo> methods = grpcService.listMethods(selectedService);
        methodListView.getItems().clear();
        for (MethodInfo m : methods) {
            methodListView.getItems().add(m.name());
        }
    }

    private void showMethodInfo() {
        if (selectedService == null || selectedMethod == null) return;
        
        List<MethodInfo> methods = grpcService.listMethods(selectedService);
        for (MethodInfo m : methods) {
            if (m.name().equals(selectedMethod)) {
                String info = I18N.get("methodInfo.format",
                    m.inputType(), m.outputType(), m.clientStreaming(), m.serverStreaming());
                methodInfoLabel.setText(info);
                break;
            }
        }
    }

    private void loadRequestTemplate() {
        if (selectedService == null || selectedMethod == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String template = grpcService.getRequestTemplate(selectedService, selectedMethod);
                Platform.runLater(() -> requestArea.setText(template));
            } catch (Exception e) {
                Platform.runLater(() -> requestArea.setText("{}"));
            }
        });
    }

    @FXML
    private void onCall() {
        if (!isConnected) {
            callStatusLabel.setText(I18N.get("msg.pleaseConnect"));
            return;
        }
        if (selectedService == null || selectedMethod == null) {
            callStatusLabel.setText(I18N.get("msg.pleaseSelectService"));
            return;
        }
        
        String requestJson = requestArea.getText();
        int timeout = parseTimeout();
        
        callStatusLabel.setText(I18N.get("msg.calling"));
        responseArea.setText(I18N.get("msg.requesting"));
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture.runAsync(() -> {
            try {
                String response = grpcService.callUnary(selectedService, selectedMethod, requestJson, timeout);
                long elapsed = System.currentTimeMillis() - startTime;
                Platform.runLater(() -> {
                    responseArea.setText(response);
                    callStatusLabel.setText(I18N.get("msg.success", elapsed));
                    callStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                Platform.runLater(() -> {
                    responseArea.setText(I18N.get("msg.callFailed", e.getMessage()));
                    callStatusLabel.setText(I18N.get("msg.failed", elapsed));
                    callStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onClear() {
        requestArea.clear();
        responseArea.clear();
        callStatusLabel.setText("");
    }

    private int parseTimeout() {
        try {
            return Integer.parseInt(timeoutField.getText());
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    public void dispose() {
        grpcService.close();
    }
}
