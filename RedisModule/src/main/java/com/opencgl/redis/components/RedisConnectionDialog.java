package com.opencgl.redis.components;

import com.opencgl.redis.model.RedisConnectionType;
import com.opencgl.redis.model.RedisWidgetDto;
import com.opencgl.redis.service.RedisConnectionManager;
import com.opencgl.redis.i18n.I18N;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXPasswordField;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.controls.MFXToggleButton;
import io.github.palexdev.materialfx.enums.FloatMode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

/**
 * Modernized Redis Connection Configuration Dialog using MaterialFX
 */
public class RedisConnectionDialog extends Dialog<RedisWidgetDto> {

    private final MFXTextField nameField = new MFXTextField();
    private final MFXComboBox<RedisConnectionType> typeCombo = new MFXComboBox<>();

    // Common/Standalone fields
    private final MFXTextField hostField = new MFXTextField();
    private final MFXTextField portField = new MFXTextField();
    private final MFXPasswordField passwordField = new MFXPasswordField();

    // For Standalone only
    private final MFXTextField databaseField = new MFXTextField();

    // For Cluster Mode
    private final MFXToggleButton clusterModeToggle = new MFXToggleButton();
    private final TextArea clusterNodesArea = new TextArea(); // fallback if manual
    private final MFXPasswordField clusterPwd = new MFXPasswordField();

    // Sentinel config
    private final MFXTextField sentinelMasterField = new MFXTextField();
    private final TextArea sentinelNodesArea = new TextArea();
    private final MFXPasswordField senPwd = new MFXPasswordField();

    // Panes
    private final StackPane configPane = new StackPane();
    private final VBox standaloneConfig = new VBox(16);
    private final VBox clusterConfig = new VBox(16);
    private final VBox sentinelConfig = new VBox(16);

    private final Label testResultLabel = new Label();

    public RedisConnectionDialog() {
        this(null);
    }

    public RedisConnectionDialog(RedisWidgetDto existingConfig) {
        setTitle(I18N.get("dialog.connection.title"));
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);

        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setPrefWidth(520);
        root.setStyle("-fx-background-color: -theme-bg-primary;");

        // 1. Connection Name
        setupMfxField(nameField, I18N.get("label.connection_name"), I18N.get("prompt.connection_name"));

        // 2. Connection Type
        typeCombo.setFloatingText(I18N.get("label.connection_type"));
        typeCombo.setFloatMode(FloatMode.BORDER);
        typeCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(RedisConnectionType type) {
                return type == null ? "" : type.getDisplayName();
            }

            @Override
            public RedisConnectionType fromString(String string) {
                return null;
            }
        });
        typeCombo.getItems().addAll(RedisConnectionType.values());
        typeCombo.setValue(RedisConnectionType.STANDALONE);
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> switchConfigPane(newVal));

        // 3. Build Sub-Panes
        buildStandaloneConfig();
        buildClusterConfig();
        buildSentinelConfig();

        configPane.getChildren().addAll(standaloneConfig, clusterConfig, sentinelConfig);
        switchConfigPane(RedisConnectionType.STANDALONE);

        // 4. Test Button
        MFXButton testButton = new MFXButton(I18N.get("button.test_connection"));
        testButton.setButtonType(io.github.palexdev.materialfx.enums.ButtonType.RAISED);
        testButton.setStyle("-fx-background-color: -theme-accent; -fx-text-fill: -theme-text-inverse; -fx-font-weight: bold;");
        testButton.setOnAction(e -> testConnection());

        testResultLabel.setStyle("-fx-font-size: 13px;");

        HBox testBox = new HBox(12, testButton, testResultLabel);
        testBox.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(nameField, typeCombo, new Separator(), configPane, new Separator(), testBox);

        getDialogPane().setContent(root);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Ensure standard theme background on dialog
        getDialogPane().setStyle("-fx-background-color: -theme-bg-primary; -fx-base: -theme-bg-primary;");

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return buildConfig();
            }
            return null;
        });

        if (existingConfig != null) {
            loadConfig(existingConfig);
        }
    }

    private void setupMfxField(MFXTextField field, String label, String placeholder) {
        field.setFloatingText(label);
        field.setPromptText(placeholder);
        field.setFloatMode(FloatMode.BORDER);
        field.setMaxWidth(Double.MAX_VALUE);
    }

    private void setupMfxPasswordField(MFXPasswordField field, String label, String placeholder) {
        field.setFloatingText(label);
        field.setPromptText(placeholder);
        field.setFloatMode(FloatMode.BORDER);
        field.setMaxWidth(Double.MAX_VALUE);
    }

    private void buildStandaloneConfig() {
        hostField.setText("localhost");
        setupMfxField(hostField, I18N.get("label.host_address"), "127.0.0.1");

        portField.setText("6379");
        setupMfxField(portField, I18N.get("label.port"), "6379");

        setupMfxPasswordField(passwordField, I18N.get("label.password"), "*****");

        databaseField.setText("0");
        setupMfxField(databaseField, I18N.get("label.database"), "0");

        // Cluster Toggle for single-node cluster discovery
        clusterModeToggle.setStyle("-mfx-main-color: -theme-accent;");
        Label clusterToggleLabel = new Label(I18N.get("label.cluster_mode_discovery"));
        clusterToggleLabel.setStyle("-fx-text-fill: -theme-text;");

        HBox hostPortBox = new HBox(12, hostField, portField);
        HBox.setHgrow(hostField, Priority.ALWAYS);
        portField.setPrefWidth(120);

        HBox dbClusterBox = new HBox(12, databaseField, clusterModeToggle, clusterToggleLabel);
        dbClusterBox.setAlignment(Pos.CENTER_LEFT);
        databaseField.setPrefWidth(120);

        standaloneConfig.getChildren().addAll(hostPortBox, passwordField, dbClusterBox);
    }

    private void buildClusterConfig() {
        clusterNodesArea.setPromptText(I18N.get("prompt.cluster_nodes"));
        clusterNodesArea.setPrefRowCount(4);
        clusterNodesArea.setStyle("-fx-control-inner-background: -theme-bg-secondary; -fx-text-fill: -theme-text;");

        setupMfxPasswordField(clusterPwd, I18N.get("label.password"), "*****");

        Label lbl = new Label(I18N.get("label.cluster_nodes"));
        lbl.setStyle("-fx-text-fill: -theme-text-secondary;");

        clusterConfig.getChildren().addAll(lbl, clusterNodesArea, clusterPwd);
    }

    private void buildSentinelConfig() {
        setupMfxField(sentinelMasterField, I18N.get("label.sentinel_master"), "mymaster");

        sentinelNodesArea.setPromptText(I18N.get("prompt.sentinel_nodes"));
        sentinelNodesArea.setPrefRowCount(3);
        sentinelNodesArea.setStyle("-fx-control-inner-background: -theme-bg-secondary; -fx-text-fill: -theme-text;");

        setupMfxPasswordField(senPwd, I18N.get("label.password"), "*****");

        Label lbl = new Label(I18N.get("label.sentinel_nodes"));
        lbl.setStyle("-fx-text-fill: -theme-text-secondary;");

        sentinelConfig.getChildren().addAll(sentinelMasterField, lbl, sentinelNodesArea, senPwd);
    }

    private void switchConfigPane(RedisConnectionType type) {
        standaloneConfig.setVisible(type == RedisConnectionType.STANDALONE);
        standaloneConfig.setManaged(type == RedisConnectionType.STANDALONE);
        clusterConfig.setVisible(type == RedisConnectionType.CLUSTER);
        clusterConfig.setManaged(type == RedisConnectionType.CLUSTER);
        sentinelConfig.setVisible(type == RedisConnectionType.SENTINEL);
        sentinelConfig.setManaged(type == RedisConnectionType.SENTINEL);

        testResultLabel.setText("");
    }

    private void testConnection() {
        testResultLabel.setText(I18N.get("message.connecting"));
        testResultLabel.setStyle("-fx-text-fill: -theme-text-secondary;");

        RedisWidgetDto config = buildConfig();

        new Thread(() -> {
            RedisConnectionManager manager = new RedisConnectionManager(config);
            boolean success = false;
            String message;

            try {
                success = manager.connect();
                message = success ? I18N.get("message.connection_success") : I18N.get("message.connection_failed");
                manager.disconnect();
            }
            catch (Exception e) {
                message = I18N.get("message.connection_failed") + ": " + e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalMessage = message;

            Platform.runLater(() -> {
                testResultLabel.setText(finalMessage);
                testResultLabel.setStyle(finalSuccess
                    ? "-fx-text-fill: -theme-success; -fx-font-weight: bold;"
                    : "-fx-text-fill: -theme-danger;");
            });
        }).start();
    }

    private RedisWidgetDto buildConfig() {
        RedisWidgetDto dto = new RedisWidgetDto();
        dto.setConnectionName(nameField.getText());
        dto.setIsLeaf(true);

        RedisConnectionType type = typeCombo.getValue();

        if (type == RedisConnectionType.STANDALONE) {
            // Check if they toggled cluster mode inside standalone UI
            if (clusterModeToggle.isSelected()) {
                dto.setConnectionType(RedisConnectionType.CLUSTER);
                // In generic cluster config, we set nodes as Host:Port
                dto.setClusterNodes(hostField.getText() + ":" + portField.getText());
                dto.setPassword(passwordField.getText());
            }
            else {
                dto.setConnectionType(RedisConnectionType.STANDALONE);
                dto.setHost(hostField.getText());
                dto.setPort(Integer.parseInt(portField.getText().isEmpty() ? "6379" : portField.getText()));
                dto.setPassword(passwordField.getText());
                dto.setDatabase(Integer.parseInt(databaseField.getText().isEmpty() ? "0" : databaseField.getText()));
            }
        }
        else if (type == RedisConnectionType.CLUSTER) {
            dto.setConnectionType(RedisConnectionType.CLUSTER);
            dto.setClusterNodes(clusterNodesArea.getText().replace("\n", ","));
            dto.setPassword(clusterPwd.getText());
        }
        else if (type == RedisConnectionType.SENTINEL) {
            dto.setConnectionType(RedisConnectionType.SENTINEL);
            dto.setSentinelMaster(sentinelMasterField.getText());
            dto.setSentinelNodes(sentinelNodesArea.getText().replace("\n", ","));
            dto.setPassword(senPwd.getText());
        }

        return dto;
    }

    private void loadConfig(RedisWidgetDto config) {
        nameField.setText(config.getConnectionName());

        // If it was a cluster but has only one node, we can map it back to Standalone Mode + Toggle
        if (config.getConnectionType() == RedisConnectionType.CLUSTER) {
            String nodes = config.getClusterNodes();
            if (nodes != null && !nodes.contains(",")) { // Single node cluster
                typeCombo.setValue(RedisConnectionType.STANDALONE);
                clusterModeToggle.setSelected(true);
                String[] parts = nodes.split(":");
                if (parts.length == 2) {
                    hostField.setText(parts[0]);
                    portField.setText(parts[1]);
                }
                passwordField.setText(config.getPassword() == null ? "" : config.getPassword());
            }
            else {
                typeCombo.setValue(RedisConnectionType.CLUSTER);
                clusterNodesArea.setText(config.getClusterNodes() != null
                    ? config.getClusterNodes().replace(",", "\n")
                    : "");
                clusterPwd.setText(config.getPassword() == null ? "" : config.getPassword());
            }
        }
        else {
            typeCombo.setValue(config.getConnectionType());
            if (config.getConnectionType() == RedisConnectionType.STANDALONE) {
                clusterModeToggle.setSelected(false);
                hostField.setText(config.getHost() == null ? "" : config.getHost());
                portField.setText(config.getPort() != null ? String.valueOf(config.getPort()) : "6379");
                passwordField.setText(config.getPassword() == null ? "" : config.getPassword());
                databaseField.setText(config.getDatabase() != null ? String.valueOf(config.getDatabase()) : "0");
            }
            else if (config.getConnectionType() == RedisConnectionType.SENTINEL) {
                sentinelMasterField.setText(config.getSentinelMaster() == null ? "" : config.getSentinelMaster());
                sentinelNodesArea.setText(config.getSentinelNodes() != null
                    ? config.getSentinelNodes().replace(",", "\n")
                    : "");
                senPwd.setText(config.getPassword() == null ? "" : config.getPassword());
            }
        }
    }

    public static RedisWidgetDto showDialog() {
        return showDialog(null, null);
    }

    public static RedisWidgetDto showDialog(RedisWidgetDto existingConfig) {
        return showDialog(existingConfig, null);
    }

    public static RedisWidgetDto showDialog(RedisWidgetDto existingConfig, javafx.stage.Window owner) {
        RedisConnectionDialog dialog = new RedisConnectionDialog(existingConfig);
        // 注册主题 & 设置 owner 使 Dialog 能继承 CSS
        if (owner != null) {
            dialog.initOwner(owner);
        }
        // 将 DialogPane 所在 Scene 注册到 ThemeManager
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                com.opencgl.base.theme.ThemeManager.getInstance().registerScene(newScene);
                dialog.setOnHidden(e -> com.opencgl.base.theme.ThemeManager.getInstance().unregisterScene(newScene));
            }
        });
        return dialog.showAndWait().orElse(null);
    }
}
