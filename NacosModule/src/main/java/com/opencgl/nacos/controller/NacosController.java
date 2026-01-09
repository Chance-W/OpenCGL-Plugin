package com.opencgl.nacos.controller;

import com.opencgl.nacos.i18n.I18N;
import com.opencgl.nacos.service.NacosService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Nacos 工具控制器
 *
 * @author OpenCGL
 */
public class NacosController implements Initializable {

    @FXML private TextField serverAddrField;
    @FXML private TextField namespaceField;
    @FXML private Button connectBtn;
    @FXML private Label connectionStatusLabel;
    
    // 配置管理
    @FXML private TextField dataIdField;
    @FXML private TextField groupField;
    @FXML private TextArea configContentArea;
    @FXML private Label configStatusLabel;
    
    // 服务发现
    @FXML private TextField serviceGroupField;
    @FXML private ListView<String> serviceListView;
    @FXML private TextArea instancesArea;

    private final NacosService nacosService = new NacosService();
    private boolean isConnected = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        serverAddrField.setText("localhost:8848");
        groupField.setText("DEFAULT_GROUP");
        serviceGroupField.setText("DEFAULT_GROUP");
        
        serviceListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadInstances(newVal);
            }
        });
        initI18n();
    }

    private void initI18n() {
        // FXML text is resolved via %key; status labels set at runtime with I18N
    }

    @FXML
    private void onConnect() {
        String serverAddr = serverAddrField.getText();
        String namespace = namespaceField.getText();
        
        connectBtn.setDisable(true);
        connectionStatusLabel.setText(I18N.get("status.connecting"));
        
        CompletableFuture.runAsync(() -> {
            try {
                nacosService.connect(serverAddr, namespace);
                boolean success = nacosService.testConnection();
                Platform.runLater(() -> {
                    if (success) {
                        isConnected = true;
                        connectionStatusLabel.setText(I18N.get("status.connected", nacosService.getServerStatus()));
                        connectionStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                    } else {
                        connectionStatusLabel.setText(I18N.get("status.connect_failed"));
                        connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    }
                    connectBtn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText(I18N.get("msg.connect_error", e.getMessage() != null ? e.getMessage() : ""));
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    connectBtn.setDisable(false);
                });
            }
        });
    }

    // ==================== 配置管理 ====================

    @FXML
    private void onGetConfig() {
        if (!isConnected) {
            configStatusLabel.setText(I18N.get("msg.please_connect"));
            return;
        }
        
        String dataId = dataIdField.getText();
        String group = groupField.getText();
        
        if (dataId.isEmpty()) {
            configStatusLabel.setText(I18N.get("msg.enter_data_id"));
            return;
        }
        
        configStatusLabel.setText(I18N.get("status.fetching"));
        
        CompletableFuture.runAsync(() -> {
            try {
                String config = nacosService.getConfig(dataId, group);
                Platform.runLater(() -> {
                    configContentArea.setText(config != null ? config : I18N.get("msg.config_not_exists"));
                    configStatusLabel.setText(I18N.get("status.get_ok"));
                    configStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    configStatusLabel.setText(I18N.get("msg.connect_error", e.getMessage() != null ? e.getMessage() : ""));
                    configStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onPublishConfig() {
        if (!isConnected) {
            configStatusLabel.setText(I18N.get("msg.please_connect"));
            return;
        }
        
        String dataId = dataIdField.getText();
        String group = groupField.getText();
        String content = configContentArea.getText();
        
        if (dataId.isEmpty() || content.isEmpty()) {
            configStatusLabel.setText(I18N.get("msg.enter_data_id_content"));
            return;
        }
        
        configStatusLabel.setText(I18N.get("status.publishing"));
        
        CompletableFuture.runAsync(() -> {
            try {
                boolean success = nacosService.publishConfig(dataId, group, content);
                Platform.runLater(() -> {
                    configStatusLabel.setText(success ? I18N.get("status.publish_ok") : I18N.get("status.publish_fail"));
                    configStatusLabel.setStyle(success ? "-fx-text-fill: #4caf50;" : "-fx-text-fill: #f44336;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    configStatusLabel.setText(I18N.get("msg.connect_error", e.getMessage() != null ? e.getMessage() : ""));
                    configStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onDeleteConfig() {
        if (!isConnected) return;
        
        String dataId = dataIdField.getText();
        String group = groupField.getText();
        
        CompletableFuture.runAsync(() -> {
            try {
                boolean success = nacosService.removeConfig(dataId, group);
                Platform.runLater(() -> {
                    configStatusLabel.setText(success ? I18N.get("status.delete_ok") : I18N.get("status.delete_fail"));
                    configContentArea.clear();
                });
            } catch (Exception e) {
                Platform.runLater(() -> configStatusLabel.setText(I18N.get("msg.connect_error", e.getMessage() != null ? e.getMessage() : "")));
            }
        });
    }

    // ==================== 服务发现 ====================

    @FXML
    private void onRefreshServices() {
        if (!isConnected) return;
        
        String group = serviceGroupField.getText();
        
        CompletableFuture.runAsync(() -> {
            try {
                List<ServiceInfo> services = nacosService.listServices(group, 1, 100);
                Platform.runLater(() -> {
                    serviceListView.getItems().clear();
                    for (ServiceInfo info : services) {
                        serviceListView.getItems().add(info.getName());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> instancesArea.setText(I18N.get("msg.list_failed", e.getMessage() != null ? e.getMessage() : "")));
            }
        });
    }

    private void loadInstances(String serviceName) {
        String group = serviceGroupField.getText();
        
        CompletableFuture.runAsync(() -> {
            try {
                List<Instance> instances = nacosService.getInstances(serviceName, group);
                StringBuilder sb = new StringBuilder();
                sb.append("服务: ").append(serviceName).append("\n实例数: ").append(instances.size()).append("\n\n");
                for (Instance inst : instances) {
                    sb.append(String.format("  %s:%d (healthy=%s, weight=%.1f)\n",
                        inst.getIp(), inst.getPort(), inst.isHealthy(), inst.getWeight()));
                    if (inst.getMetadata() != null && !inst.getMetadata().isEmpty()) {
                        sb.append("    metadata: ").append(inst.getMetadata()).append("\n");
                    }
                }
                Platform.runLater(() -> instancesArea.setText(sb.toString()));
            } catch (Exception e) {
                Platform.runLater(() -> instancesArea.setText(I18N.get("msg.instances_failed", e.getMessage() != null ? e.getMessage() : "")));
            }
        });
    }

    @FXML
    private void onClear() {
        configContentArea.clear();
        instancesArea.clear();
        configStatusLabel.setText("");
    }

    public void dispose() {
        nacosService.close();
    }
}
