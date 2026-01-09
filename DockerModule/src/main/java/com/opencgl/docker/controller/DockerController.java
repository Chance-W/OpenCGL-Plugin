package com.opencgl.docker.controller;

import com.opencgl.docker.i18n.I18N;
import com.opencgl.docker.service.DockerService;
import com.opencgl.docker.service.DockerService.ContainerInfo;
import com.opencgl.docker.service.DockerService.ImageInfo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Docker 工具控制器
 *
 * @author OpenCGL
 */
public class DockerController implements Initializable {

    @FXML private TextField dockerHostField;
    @FXML private Button connectBtn;
    @FXML private Label connectionStatusLabel;
    @FXML private CheckBox showAllCheckBox;
    
    @FXML private TableView<ContainerInfo> containerTable;
    @FXML private TableColumn<ContainerInfo, String> containerIdCol;
    @FXML private TableColumn<ContainerInfo, String> containerImageCol;
    @FXML private TableColumn<ContainerInfo, String> containerNameCol;
    @FXML private TableColumn<ContainerInfo, String> containerStateCol;
    @FXML private TableColumn<ContainerInfo, String> containerStatusCol;
    
    @FXML private TableView<ImageInfo> imageTable;
    @FXML private TableColumn<ImageInfo, String> imageIdCol;
    @FXML private TableColumn<ImageInfo, String> imageTagCol;
    @FXML private TableColumn<ImageInfo, String> imageSizeCol;
    
    @FXML private TextArea logArea;

    private final DockerService dockerService = new DockerService();
    private boolean isConnected = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        dockerHostField.setText("unix:///var/run/docker.sock");
        
        // 配置容器表格列
        containerIdCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().id()));
        containerImageCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().image()));
        containerNameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name()));
        containerStateCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().state()));
        containerStatusCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().status()));
        
        // 配置镜像表格列
        imageIdCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().id()));
        imageTagCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().tag()));
        imageSizeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().size()));
        
        // 选中容器时获取日志
        containerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadContainerLogs(newVal.id());
            }
        });
        initI18n();
    }

    private void initI18n() {
        // Static texts are in FXML with %key; status messages use I18N.get() at runtime
    }

    @FXML
    private void onConnect() {
        String dockerHost = dockerHostField.getText();
        
        connectBtn.setDisable(true);
        connectionStatusLabel.setText(I18N.get("msg.connecting"));
        
        CompletableFuture.runAsync(() -> {
            dockerService.connect(dockerHost);
            boolean success = dockerService.testConnection();
            Platform.runLater(() -> {
                if (success) {
                    isConnected = true;
                    String info = dockerService.getInfo();
                    connectionStatusLabel.setText(I18N.get("msg.connected"));
                    connectionStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                    logArea.setText(info);
                    refreshContainers();
                    refreshImages();
                } else {
                    connectionStatusLabel.setText(I18N.get("msg.connectFailed"));
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                }
                connectBtn.setDisable(false);
            });
        });
    }

    @FXML
    private void onRefreshContainers() {
        refreshContainers();
    }

    private void refreshContainers() {
        if (!isConnected) return;
        boolean showAll = showAllCheckBox.isSelected();
        
        CompletableFuture.runAsync(() -> {
            try {
                List<ContainerInfo> containers = dockerService.listContainers(showAll);
                Platform.runLater(() -> {
                    containerTable.getItems().clear();
                    containerTable.getItems().addAll(containers);
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText(I18N.get("msg.listContainersFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onRefreshImages() {
        refreshImages();
    }

    private void refreshImages() {
        if (!isConnected) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                List<ImageInfo> images = dockerService.listImages();
                Platform.runLater(() -> {
                    imageTable.getItems().clear();
                    imageTable.getItems().addAll(images);
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText(I18N.get("msg.listImagesFailed", e.getMessage())));
            }
        });
    }

    private void loadContainerLogs(String containerId) {
        CompletableFuture.runAsync(() -> {
            try {
                String logs = dockerService.getContainerLogs(containerId, 100);
                Platform.runLater(() -> logArea.setText(logs));
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText(I18N.get("msg.logsFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onStartContainer() {
        ContainerInfo selected = containerTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                dockerService.startContainer(selected.id());
                Platform.runLater(() -> {
                    refreshContainers();
                    logArea.setText(I18N.get("msg.containerStarted", selected.id()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText(I18N.get("msg.startFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onStopContainer() {
        ContainerInfo selected = containerTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                dockerService.stopContainer(selected.id());
                Platform.runLater(() -> {
                    refreshContainers();
                    logArea.setText(I18N.get("msg.containerStopped", selected.id()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText(I18N.get("msg.stopFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onRestartContainer() {
        ContainerInfo selected = containerTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                dockerService.restartContainer(selected.id());
                Platform.runLater(() -> {
                    refreshContainers();
                    logArea.setText(I18N.get("msg.containerRestarted", selected.id()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText(I18N.get("msg.restartFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onRemoveContainer() {
        ContainerInfo selected = containerTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                dockerService.removeContainer(selected.id(), true);
                Platform.runLater(() -> {
                    refreshContainers();
                    logArea.setText(I18N.get("msg.containerRemoved", selected.id()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText(I18N.get("msg.removeFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onRemoveImage() {
        ImageInfo selected = imageTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                dockerService.removeImage(selected.id(), true);
                Platform.runLater(() -> {
                    refreshImages();
                    logArea.setText(I18N.get("msg.imageRemoved", selected.id()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> logArea.setText(I18N.get("msg.removeFailed", e.getMessage())));
            }
        });
    }

    public void dispose() {
        dockerService.close();
    }
}
