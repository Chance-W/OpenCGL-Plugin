package com.opencgl.mock;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Mock 服务器控制面板
 */
public class MockServerPanel extends VBox {
    
    private final MockServer mockServer;
    private final Button startStopButton;
    private final TextField portField;
    private final Label statusLabel;
    private final ListView<MockRule> rulesListView;
    private final ListView<MockRequestLog> logsListView;
    
    public MockServerPanel() {
        this(new MockServer());
    }
    
    public MockServerPanel(MockServer mockServer) {
        this.mockServer = mockServer;
        
        setSpacing(12);
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #f8f9fa;");
        
        // 控制区域
        HBox controlBox = new HBox(12);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        
        Label portLabel = new Label("Port:");
        portField = new TextField(String.valueOf(mockServer.getPort()));
        portField.setPrefWidth(80);
        portField.setStyle("-fx-background-color: white; -fx-border-color: #ced4da;");
        
        startStopButton = new Button("启动");
        startStopButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-padding: 8 20;");
        startStopButton.setOnAction(e -> toggleServer());
        
        statusLabel = new Label("已停止");
        statusLabel.setStyle("-fx-text-fill: #6c757d;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button addRuleButton = new Button("+ 添加规则");
        addRuleButton.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white;");
        addRuleButton.setOnAction(e -> showAddRuleDialog());
        
        controlBox.getChildren().addAll(portLabel, portField, startStopButton, statusLabel, spacer, addRuleButton);
        
        // 规则列表
        TitledPane rulesPane = new TitledPane();
        rulesPane.setText("Mock 规则");
        rulesPane.setCollapsible(false);
        
        rulesListView = new ListView<>(mockServer.getRules());
        rulesListView.setCellFactory(param -> new MockRuleCell());
        rulesListView.setPrefHeight(200);
        rulesPane.setContent(rulesListView);
        
        // 请求日志
        TitledPane logsPane = new TitledPane();
        logsPane.setText("请求日志");
        logsPane.setCollapsible(false);
        
        logsListView = new ListView<>(mockServer.getRequestLogs());
        logsListView.setCellFactory(param -> new MockLogCell());
        logsListView.setPrefHeight(200);
        logsPane.setContent(logsListView);
        
        VBox.setVgrow(logsPane, Priority.ALWAYS);
        
        getChildren().addAll(controlBox, rulesPane, logsPane);
    }
    
    private void toggleServer() {
        if (mockServer.isRunning()) {
            mockServer.stop();
            startStopButton.setText("启动");
            startStopButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-padding: 8 20;");
            statusLabel.setText("已停止");
            statusLabel.setStyle("-fx-text-fill: #6c757d;");
            portField.setDisable(false);
        } else {
            try {
                int port = Integer.parseInt(portField.getText());
                mockServer.setPort(port);
            } catch (NumberFormatException e) {
                showError("端口号无效");
                return;
            }
            
            if (mockServer.start()) {
                startStopButton.setText("停止");
                startStopButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-padding: 8 20;");
                statusLabel.setText("运行中 - http://localhost:" + mockServer.getPort());
                statusLabel.setStyle("-fx-text-fill: #28a745;");
                portField.setDisable(true);
            } else {
                showError("启动失败，端口可能被占用");
            }
        }
    }
    
    private void showAddRuleDialog() {
        Dialog<MockRule> dialog = new Dialog<>();
        dialog.setTitle("添加 Mock 规则");
        dialog.setHeaderText("配置请求匹配和响应");
        
        // 表单
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        TextField nameField = new TextField();
        nameField.setPromptText("规则名称");
        
        ComboBox<String> methodCombo = new ComboBox<>();
        methodCombo.getItems().addAll("GET", "POST", "PUT", "DELETE", "PATCH", "*");
        methodCombo.setValue("GET");
        
        TextField pathField = new TextField();
        pathField.setPromptText("/api/users");
        
        CheckBox regexCheck = new CheckBox("正则匹配");
        
        TextField statusField = new TextField("200");
        statusField.setPrefWidth(60);
        
        TextField contentTypeField = new TextField("application/json");
        
        TextArea bodyArea = new TextArea();
        bodyArea.setPromptText("{\"message\": \"Hello\"}");
        bodyArea.setPrefRowCount(5);
        
        TextField delayField = new TextField("0");
        delayField.setPrefWidth(60);
        
        grid.add(new Label("名称:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("方法:"), 0, 1);
        grid.add(methodCombo, 1, 1);
        grid.add(new Label("路径:"), 0, 2);
        grid.add(pathField, 1, 2);
        grid.add(regexCheck, 1, 3);
        grid.add(new Label("状态码:"), 0, 4);
        grid.add(statusField, 1, 4);
        grid.add(new Label("Content-Type:"), 0, 5);
        grid.add(contentTypeField, 1, 5);
        grid.add(new Label("响应体:"), 0, 6);
        grid.add(bodyArea, 1, 6);
        grid.add(new Label("延迟(ms):"), 0, 7);
        grid.add(delayField, 1, 7);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                try {
                    return MockRule.builder()
                        .name(nameField.getText())
                        .method(methodCombo.getValue())
                        .path(pathField.getText())
                        .regex(regexCheck.isSelected())
                        .enabled(true)
                        .statusCode(Integer.parseInt(statusField.getText()))
                        .contentType(contentTypeField.getText())
                        .responseBody(bodyArea.getText())
                        .delayMs(Long.parseLong(delayField.getText()))
                        .build();
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(mockServer::addRule);
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public MockServer getMockServer() {
        return mockServer;
    }

    public void dispose() {
        mockServer.stop();
    }
    
    /**
     * Mock 规则单元格
     */
    private class MockRuleCell extends ListCell<MockRule> {
        @Override
        protected void updateItem(MockRule item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            
            HBox box = new HBox(8);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(4, 8, 4, 8));
            
            CheckBox enabledCheck = new CheckBox();
            enabledCheck.setSelected(item.isEnabled());
            enabledCheck.selectedProperty().addListener((obs, old, val) -> item.setEnabled(val));
            
            Label methodLabel = new Label(item.getMethod());
            methodLabel.setStyle("-fx-background-color: #e3f2fd; -fx-padding: 2 6; -fx-background-radius: 3; -fx-font-weight: bold;");
            methodLabel.setMinWidth(50);
            
            Label pathLabel = new Label(item.getPath());
            pathLabel.setStyle("-fx-text-fill: #495057;");
            
            Label nameLabel = new Label("(" + item.getName() + ")");
            nameLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            Button deleteBtn = new Button("×");
            deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc3545; -fx-cursor: hand;");
            deleteBtn.setOnAction(e -> mockServer.removeRule(item));
            
            box.getChildren().addAll(enabledCheck, methodLabel, pathLabel, nameLabel, spacer, deleteBtn);
            setGraphic(box);
        }
    }
    
    /**
     * Mock 日志单元格
     */
    private class MockLogCell extends ListCell<MockRequestLog> {
        @Override
        protected void updateItem(MockRequestLog item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            
            HBox box = new HBox(8);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(4, 8, 4, 8));
            
            Label timeLabel = new Label(item.getDisplayTime());
            timeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
            
            Label methodLabel = new Label(item.getMethod());
            methodLabel.setStyle("-fx-background-color: #e3f2fd; -fx-padding: 2 6; -fx-background-radius: 3;");
            
            Label pathLabel = new Label(item.getDisplayPath());
            pathLabel.setStyle("-fx-text-fill: #495057;");
            
            Label matchLabel = new Label(item.isMatched() ? "✓ " + item.getMatchedRule() : "✗ 未匹配");
            matchLabel.setStyle(item.isMatched() ? "-fx-text-fill: #28a745;" : "-fx-text-fill: #dc3545;");
            
            box.getChildren().addAll(timeLabel, methodLabel, pathLabel, matchLabel);
            setGraphic(box);
        }
    }
}
