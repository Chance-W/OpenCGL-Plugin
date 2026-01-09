package com.opencgl.lanmsg.controller;

import com.opencgl.lanmsg.i18n.I18N;
import com.opencgl.lanmsg.model.Message;
import com.opencgl.lanmsg.model.User;
import com.opencgl.lanmsg.service.DiscoveryService;
import com.opencgl.lanmsg.service.FileTransferService;
import com.opencgl.lanmsg.service.MessageService;
import com.opencgl.lanmsg.views.LanMessengerView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.stage.FileChooser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ResourceBundle;

/**
 * 局域网通讯控制器
 */
public class LanMessengerController extends LanMessengerView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(LanMessengerController.class);
    
    private final DiscoveryService discoveryService = new DiscoveryService();
    private final MessageService messageService = new MessageService();
    private final FileTransferService fileTransferService = new FileTransferService();
    
    private final ObservableList<String> userList = FXCollections.observableArrayList();
    private final Map<String, User> userMap = new ConcurrentHashMap<>();
    // Change chatHistory to store ObservableList for UI binding
    private final Map<String, ObservableList<Message>> chatHistory = new ConcurrentHashMap<>();
    
    private User selectedUser;
    
    // ... (existing code)

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化用户列表
        userListView.setItems(userList);
        
        // 初始化用户名
        usernameField.setText(discoveryService.getMyUsername());
        
        // 初始化 IP 选择列表
        List<String> ips = DiscoveryService.getAllLocalIps();
        ipComboBox.getItems().addAll(ips);
        ipComboBox.setValue(discoveryService.getMyIp());
        
        // 注入依赖
        fileTransferService.setMessageService(messageService);
        
        setupChatListView();
        // 设置回调
        setupCallbacks();
        setupDragDrop();
        
        // 绑定事件
        refreshButton.setOnAction(e -> refreshUsers());
        sendButton.setOnAction(e -> sendMessage());
        sendFileButton.setOnAction(e -> sendFile());
        startServiceButton.setOnAction(e -> onStartService());
        stopServiceButton.setOnAction(e -> onStopService());
        
        // ... (existing code handling selection, enter key etc)
        
        // 用户选择事件
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                selectUser(newVal);
            }
        });
        
        // 回车发送
        messageInput.setOnAction(e -> sendMessage());
        
        // 不自动启动，等待用户手动启动
        updateButtonStates(false);
        updateMyInfo();
        initI18n();
        appendStatus(I18N.get("status.readyConfig"));
    }

    private void initI18n() {
        if (refreshButton != null) refreshButton.textProperty().bind(I18N.getBinding("btn.refreshUsers"));
        if (startServiceButton != null) startServiceButton.textProperty().bind(I18N.getBinding("btn.startService"));
        if (stopServiceButton != null) stopServiceButton.textProperty().bind(I18N.getBinding("btn.stopService"));
        if (sendButton != null) sendButton.textProperty().bind(I18N.getBinding("btn.send"));
        if (settingsLabel != null) settingsLabel.textProperty().bind(I18N.getBinding("label.settings"));
        if (usernameLabel != null) usernameLabel.textProperty().bind(I18N.getBinding("label.username"));
        if (ipLabel != null) ipLabel.textProperty().bind(I18N.getBinding("label.localIp"));
        if (portLabel != null) portLabel.textProperty().bind(I18N.getBinding("label.port"));
        if (messageInput != null) messageInput.promptTextProperty().bind(I18N.getBinding("prompt.messageInput"));
    }
    
    private void setupChatListView() {
        chatListView.setCellFactory(lv -> new ChatMessageCell());
    }

    private void setupDragDrop() {
        chatListView.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && selectedUser != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        
        chatListView.setOnDragDropped(event -> {
            if (event.getDragboard().hasFiles() && selectedUser != null) {
                List<File> files = event.getDragboard().getFiles();
                if (!files.isEmpty()) {
                    File file = files.get(0);
                    sendFile(file);
                }
                event.setDropCompleted(true);
            }
            event.consume();
        });
        
        // 也允许拖拽到输入框
        messageInput.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && selectedUser != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        
        messageInput.setOnDragDropped(event -> {
            if (event.getDragboard().hasFiles() && selectedUser != null) {
                List<File> files = event.getDragboard().getFiles();
                if (!files.isEmpty()) {
                    File file = files.get(0);
                    sendFile(file);
                }
                event.setDropCompleted(true);
            }
            event.consume();
        });
    }

    private void onStartService() {
        String username = usernameField.getText().trim();
        String ip = ipComboBox.getValue();
        String portStr = portField.getText().trim();
        
        if (username.isEmpty()) {
            appendStatus(I18N.get("status.usernameEmpty"));
            return;
        }
        
        int port = 2425; // 默认端口
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                appendStatus(I18N.get("status.portRange"));
                return;
            }
        } catch (NumberFormatException e) {
            appendStatus(I18N.get("status.portNumber"));
            return;
        }
        
        // 应用设置
        discoveryService.setMyUsername(username);
        if (ip != null && !ip.isEmpty()) {
            discoveryService.setMyIp(ip);
        }
        discoveryService.setMyPort(port);
        
        startServices();
        updateButtonStates(true);
    }
    
    private void onStopService() {
        stopServices();
        updateButtonStates(false);
        appendStatus(I18N.get("status.serviceStopped"));
    }
    
    private void updateButtonStates(boolean running) {
        Platform.runLater(() -> {
            startServiceButton.setDisable(running);
            stopServiceButton.setDisable(!running);
            usernameField.setDisable(running);
            ipComboBox.setDisable(running);
            portField.setDisable(running);
            refreshButton.setDisable(!running);
            sendButton.setDisable(!running);
            sendFileButton.setDisable(!running);
            messageInput.setDisable(!running);
        });
    }
    
    private void setupCallbacks() {
        discoveryService.setOnUserOnline(user -> Platform.runLater(() -> {
            userMap.put(user.getKey(), user);
            updateUserList();
            appendStatus(I18N.get("status.userOnline", user.getUsername()));
        }));
        
        discoveryService.setOnUserOffline(user -> Platform.runLater(() -> {
            userMap.remove(user.getKey());
            updateUserList();
            appendStatus(I18N.get("status.userOffline", user.getUsername()));
        }));
        
        messageService.setOnMessageReceived(msg -> Platform.runLater(() -> {
            String content = msg.getContent();
            boolean isFileReq = content.startsWith("FILE_REQ|");
            
            // For file request, we treat it as a special message
            // But we need to parse it to a clean object if possible, or just parse in cell
            
            String key = msg.getFromIp() + ":" + discoveryService.getMyPort();
            chatHistory.computeIfAbsent(key, k -> FXCollections.observableArrayList()).add(msg);
            
            // If current user, list updates automatically via binding
            // Scroll to bottom if selected
            if (selectedUser != null && selectedUser.getKey().equals(key)) {
                chatListView.scrollTo(chatListView.getItems().size() - 1);
            }
            
            appendStatus(isFileReq ? I18N.get("status.fileRequest") : I18N.get("status.messageReceived", msg.getFrom()));
        }));
        
        fileTransferService.setOnProgressUpdate((transfer, progress) -> Platform.runLater(() -> {
             // Find the message associated with this transfer and update it
             // Since we don't have direct binding from Transfer to Message UI, 
             // we rely on the cell polling or we trigger refresh?
             // Better: The Message object could hold the Transfer ID, and we update a property map?
             // Since this is a simple impl, let's force refresh or use specific update logic.
             // Actually, for a smoother UI, the Cell should observe the Transfer object.
             // But we only have ID.
             
             // Workaround: We refresh the list view on significant changes or status bar for now?
             // User wants progress IN chat.
             
             // We can fire an event or update a shared model. 
             // Let's assume simpler approach: Re-sending "progress" messages is bad.
             // We will iterate visible items? No.
             
             // Best simple way: Store Transfer objects in a map accessible by UI
             // Content of message: "FILE_TX|id"
             // Cell looks up TransferService.getTransfer(id) -> observes progress.
             
             // Just triggering a redraw is inefficient but works for low freq. 
             // But progress is high freq.
             
             // We will use a global observable map for progress in Controller?
             // Or simply: The Cell will query the service. Service needs to expose Observable?
             // Let's add a "Progress" property to the Message if possible? No, Message is data.
             
             // For now, I will use a static map or singleton approach if I can't pass refs easily.
             // But I can pass controller ref to cell.
        }));
        
        fileTransferService.setOnTransferComplete(transfer -> Platform.runLater(() -> {
            appendStatus(I18N.get("status.transferComplete", transfer.getFileName()));
            chatListView.refresh(); // Refresh to show "Completed" state
        }));
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
    
    private void startServices() {
        try {
            discoveryService.start();
            messageService.setMyInfo(discoveryService.getMyUsername(), discoveryService.getMyIp());
            messageService.start();
            fileTransferService.start();
            
            appendStatus(I18N.get("status.serviceStarted"));
        } catch (Exception e) {
            logger.error("启动服务失败", e);
            appendStatus(I18N.get("status.startFailed", e.getMessage()));
        }
    }
    
    private void stopServices() {
        discoveryService.stop();
        messageService.stop();
        fileTransferService.stop();
    }

    public void dispose() {
        stopServices();
        selectedUser = null;
        userMap.clear();
        chatHistory.clear();
        userList.clear();
    }
    
    private void refreshUsers() {
        discoveryService.refreshUsers();
        updateUserList();
    }
    
    private void updateUserList() {
        userList.clear();
        for (User user : userMap.values()) {
            userList.add(user.getDisplayName());
        }
        userCountLabel.setText(I18N.get("label.usersOnline", userList.size()));
    }
    
    private void selectUser(String displayName) {
        for (User user : userMap.values()) {
            if (user.getDisplayName().equals(displayName)) {
                selectedUser = user;
                chatTitleLabel.setText(I18N.get("label.chatWith", user.getUsername()));
                updateChatHistory();
                return;
            }
        }
    }
    
    private void updateChatHistory() {
        if (selectedUser == null) {
            chatListView.setItems(null);
            return;
        }
        
        ObservableList<Message> messages = chatHistory.get(selectedUser.getKey());
        chatListView.setItems(messages);
        
        if (messages != null && !messages.isEmpty()) {
            chatListView.scrollTo(messages.size() - 1);
        }
    }
    
    private void sendMessage() {
        if (selectedUser == null) {
            appendStatus(I18N.get("status.selectUserFirst"));
            return;
        }
        
        String content = messageInput.getText().trim();
        if (content.isEmpty()) return;
        
        // 发送消息
        messageService.sendMessage(selectedUser, content);
        
        // 添加到本地聊天记录
        Message msg = new Message(
            discoveryService.getMyUsername(),
            discoveryService.getMyIp(),
            selectedUser.getUsername(),
            selectedUser.getIp(),
            content
        );
        chatHistory.computeIfAbsent(selectedUser.getKey(), k -> FXCollections.observableArrayList()).add(msg);
        updateChatHistory();
        
        messageInput.clear();
    }

    private void sendFile() {
        if (selectedUser == null) {
            appendStatus(I18N.get("status.selectUserFirst"));
            return;
        }
        
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("filechooser.chooseFileToSend"));
        File file = chooser.showOpenDialog(rootPane.getScene().getWindow());
        
        if (file != null) {
            sendFile(file);
        }
    }
    
    private void sendFile(File file) {
         com.opencgl.lanmsg.model.FileTransfer transfer = fileTransferService.sendFile(
            file,
            selectedUser.getUsername(),
            selectedUser.getIp(),
            discoveryService.getMyUsername(),
            discoveryService.getMyIp()
        );
        
        // Add a "File Sending" message to chat
        String content = "FILE_TX|" + transfer.getId() + "|" + file.getName() + "|" + file.length();
        Message msg = new Message(
            discoveryService.getMyUsername(),
            discoveryService.getMyIp(),
            selectedUser.getUsername(),
            selectedUser.getIp(),
            content
        );
        chatHistory.computeIfAbsent(selectedUser.getKey(), k -> FXCollections.observableArrayList()).add(msg);
        updateChatHistory();
        
        appendStatus(I18N.get("status.sendingFile", file.getName()));
    }


    // Inner class for Cell
    private class ChatMessageCell extends ListCell<Message> {
        @Override
        protected void updateItem(Message msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || msg == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 5;");
                return;
            }
            
            boolean isSelf = msg.getFromIp().equals(discoveryService.getMyIp());
            VBox bubble = new VBox(5);
            bubble.setPadding(new Insets(8));
            bubble.setMaxWidth(400); // 限制气泡宽度
            
            if (msg.getContent().startsWith("FILE_REQ|")) {
                // File Request received
                renderFileRequest(bubble, msg, isSelf);
            } else if (msg.getContent().startsWith("FILE_TX|")) {
                // Sender side file view
                renderFileTransfer(bubble, msg, isSelf);
            } else {
                // Normal text
                Text text = new Text(msg.getContent());
                text.setFill(isSelf ? Color.WHITE : Color.BLACK);
                text.setWrappingWidth(380);
                
                TextFlow flow = new TextFlow(text);
                bubble.getChildren().add(flow);
            }
            
            // Style
            String style = isSelf 
                ? "-fx-background-color: #0078d4; -fx-background-radius: 10 10 0 10;" 
                : "-fx-background-color: #e0e0e0; -fx-background-radius: 10 10 10 0;";
            bubble.setStyle(style);
            
            // Align
            HBox container = new HBox(bubble);
            container.setAlignment(isSelf ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            // Main container logic for alignment
            setGraphic(container);
            setStyle("-fx-background-color: transparent; -fx-padding: 5;");
        }
        
        private void renderFileRequest(VBox bubble, Message msg, boolean isSelf) {
            // FILE_REQ|filename|size|sender_port|transferId
            String[] parts = msg.getContent().split("\\|");
            if (parts.length < 5) return;
            
            String filename = parts[1];
            long size = Long.parseLong(parts[2]);
            String transferId = parts[4];
            
            Label title = new Label(I18N.get("label.fileRequest"));
            title.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (isSelf ? "white" : "black"));
            
            Label info = new Label(filename + "\n" + formatSize(size));
            info.setStyle("-fx-text-fill: " + (isSelf ? "#e0e0e0" : "#333333"));
            
            bubble.getChildren().addAll(title, info);
            
            if (!isSelf) {
                 Button acceptBtn = new Button(I18N.get("btn.accept"));
                 acceptBtn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white;");
                 acceptBtn.setOnAction(e -> {
                     acceptBtn.setDisable(true);
                     FileChooser fileChooser = new FileChooser();
                     fileChooser.setTitle(I18N.get("filechooser.saveFile"));
                     fileChooser.setInitialFileName(filename);
                     File saveFile = fileChooser.showSaveDialog(rootPane.getScene().getWindow());
                     
                     if (saveFile != null) {
                         int port = Integer.parseInt(parts[3]);
                         com.opencgl.lanmsg.model.FileTransfer transfer = new com.opencgl.lanmsg.model.FileTransfer(saveFile, msg.getFromIp(), msg.getFromIp(), "Me", discoveryService.getMyIp());
                         transfer.setId(transferId);
                         transfer.setPort(port);
                         transfer.setFileSize(size);
                         transfer.setFileName(filename);
                         
                         fileTransferService.receiveFile(transfer, saveFile);
                         
                         // Update message to show progress instead of buttons
                         msg.setContent("FILE_TX|" + transferId + "|" + filename + "|" + size);
                         getListView().refresh();
                     } else {
                         acceptBtn.setDisable(false);
                     }
                 });
                 bubble.getChildren().add(acceptBtn);
            }
        }
        
        private void renderFileTransfer(VBox bubble, Message msg, boolean isSelf) {
             // FILE_TX|id|name|size
             String[] parts = msg.getContent().split("\\|");
             if (parts.length < 3) return;
             
             String id = parts[1];
             String name = parts[2];
             
             Label title = new Label(isSelf ? I18N.get("label.sendingFile") : I18N.get("label.receivingFile"));
             title.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (isSelf ? "white" : "black"));
             Label nameLbl = new Label(name);
             nameLbl.setStyle("-fx-text-fill: " + (isSelf ? "#e0e0e0" : "#333333"));
             
             ProgressBar pb = new ProgressBar(0);
             pb.setPrefWidth(200);
             
             // Update progress
             com.opencgl.lanmsg.model.FileTransfer transfer = fileTransferService.getTransfer(id);
             if (transfer != null) {
                 pb.setProgress(transfer.getProgress());
                 if (transfer.getStatus() == com.opencgl.lanmsg.model.FileTransfer.TransferStatus.COMPLETED) {
                     pb.setProgress(1.0);
                     pb.setStyle("-fx-accent: green;");
                 } else if (transfer.getStatus() == com.opencgl.lanmsg.model.FileTransfer.TransferStatus.FAILED) {
                     pb.setStyle("-fx-accent: red;");
                 }
             }
             
             bubble.getChildren().addAll(title, nameLbl, pb);
        }
    }

    
    
    
    private void updateMyInfo() {
        myInfoLabel.setText(I18N.get("label.me") + " " + discoveryService.getMyUsername() + " (" + discoveryService.getMyIp() + ")");
    }
    
    private void appendStatus(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
        });
    }
}
