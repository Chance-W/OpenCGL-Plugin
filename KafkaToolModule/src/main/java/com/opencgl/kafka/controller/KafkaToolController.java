package com.opencgl.kafka.controller;

import com.opencgl.kafka.i18n.I18N;
import com.opencgl.kafka.service.KafkaService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartitionInfo;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka \u5DE5\u5177\u63A7\u5236\u5668
 */
public class KafkaToolController implements Initializable {

    @FXML private Label bootstrapServersLabel;
    @FXML private TextField bootstrapServersField;
    @FXML private Button connectBtn;
    @FXML private Label connectionStatusLabel;
    @FXML private Label topicsLabel;
    @FXML private Button refreshBtn;
    @FXML private ListView<String> topicListView;
    @FXML private Label topicInfoLabel;

    @FXML private Tab producerTab;
    @FXML private Label producerTopicLabel;
    @FXML private Label messageKeyLabel;
    @FXML private Label messageContentLabel;
    @FXML private Label headersLabel;
    @FXML private Button sendMessageBtn;
    @FXML private ComboBox<String> producerTopicCombo;
    @FXML private TextField messageKeyField;
    @FXML private TextArea messageValueArea;
    @FXML private TextArea headersArea;
    @FXML private Label sendStatusLabel;

    @FXML private Tab consumerTab;
    @FXML private Label consumerTopicLabel;
    @FXML private Label consumerGroupLabel;
    @FXML private Button clearBtn;
    @FXML private Label messagesLabel;
    @FXML private ComboBox<String> consumerTopicCombo;
    @FXML private TextField consumerGroupField;
    @FXML private TextArea messagesArea;
    @FXML private Button startConsumeBtn;
    @FXML private Button stopConsumeBtn;

    private final KafkaService kafkaService = new KafkaService();
    private boolean isConnected = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        bootstrapServersField.setText("localhost:9092");
        consumerGroupField.setText("opencgl-consumer-" + System.currentTimeMillis());

        topicListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showTopicInfo(newVal);
            }
        });

        stopConsumeBtn.setDisable(true);
        initI18n();
    }

    private void initI18n() {
        if (bootstrapServersLabel != null) bootstrapServersLabel.textProperty().bind(I18N.getBinding("label.bootstrap_servers"));
        if (connectBtn != null) connectBtn.textProperty().bind(I18N.getBinding("label.connect"));
        if (topicsLabel != null) topicsLabel.textProperty().bind(I18N.getBinding("label.topics"));
        if (refreshBtn != null) refreshBtn.textProperty().bind(I18N.getBinding("label.refresh"));
        if (producerTab != null) producerTab.textProperty().bind(I18N.getBinding("label.produce"));
        if (producerTopicLabel != null) producerTopicLabel.textProperty().bind(I18N.getBinding("label.topic"));
        if (messageKeyLabel != null) messageKeyLabel.textProperty().bind(I18N.getBinding("label.key"));
        if (messageContentLabel != null) messageContentLabel.textProperty().bind(I18N.getBinding("label.message_content"));
        if (messageValueArea != null) messageValueArea.promptTextProperty().bind(I18N.getBinding("prompt.message_content"));
        if (headersLabel != null) headersLabel.textProperty().bind(I18N.getBinding("label.headers"));
        if (sendMessageBtn != null) sendMessageBtn.textProperty().bind(I18N.getBinding("label.send_message"));
        if (consumerTab != null) consumerTab.textProperty().bind(I18N.getBinding("label.consume"));
        if (consumerTopicLabel != null) consumerTopicLabel.textProperty().bind(I18N.getBinding("label.topic"));
        if (consumerGroupLabel != null) consumerGroupLabel.textProperty().bind(I18N.getBinding("label.consumer_group"));
        if (startConsumeBtn != null) startConsumeBtn.textProperty().bind(I18N.getBinding("label.start_consume"));
        if (stopConsumeBtn != null) stopConsumeBtn.textProperty().bind(I18N.getBinding("label.stop"));
        if (clearBtn != null) clearBtn.textProperty().bind(I18N.getBinding("label.clear"));
        if (messagesLabel != null) messagesLabel.textProperty().bind(I18N.getBinding("label.messages"));
    }

    @FXML
    private void onConnect() {
        String servers = bootstrapServersField.getText();
        if (servers == null || servers.isEmpty()) {
            connectionStatusLabel.setText(I18N.get("msg.please_input_servers"));
            return;
        }

        connectBtn.setDisable(true);
        connectionStatusLabel.setText(I18N.get("msg.connecting"));

        CompletableFuture.runAsync(() -> {
            kafkaService.connect(servers);
            boolean success = kafkaService.testConnection();

            Platform.runLater(() -> {
                if (success) {
                    isConnected = true;
                    connectionStatusLabel.setText(I18N.get("msg.connected"));
                    connectionStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                    refreshTopicList();
                } else {
                    connectionStatusLabel.setText(I18N.get("msg.connect_failed"));
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                }
                connectBtn.setDisable(false);
            });
        });
    }

    @FXML
    private void onRefreshTopics() {
        refreshTopicList();
    }

    private void refreshTopicList() {
        if (!isConnected) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                List<String> topics = kafkaService.listTopics();
                Platform.runLater(() -> {
                    topicListView.getItems().clear();
                    topicListView.getItems().addAll(topics);
                    producerTopicCombo.getItems().clear();
                    producerTopicCombo.getItems().addAll(topics);
                    consumerTopicCombo.getItems().clear();
                    consumerTopicCombo.getItems().addAll(topics);
                });
            } catch (Exception e) {
                Platform.runLater(() -> topicInfoLabel.setText(I18N.get("msg.list_topics_failed", e.getMessage())));
            }
        });
    }

    private void showTopicInfo(String topicName) {
        if (!isConnected) return;

        CompletableFuture.runAsync(() -> {
            try {
                TopicDescription desc = kafkaService.describeTopic(topicName);
                StringBuilder partLines = new StringBuilder();
                for (TopicPartitionInfo partition : desc.partitions()) {
                    partLines.append("  ").append(I18N.get("msg.partition",
                            partition.partition(), partition.leader().id(), partition.replicas().size())).append("\n");
                }
                String info = I18N.get("msg.topic_info", topicName, desc.partitions().size(), partLines.toString().trim());
                Platform.runLater(() -> topicInfoLabel.setText(info));
            } catch (Exception e) {
                Platform.runLater(() -> topicInfoLabel.setText(I18N.get("msg.topic_info_failed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onSendMessage() {
        String topic = producerTopicCombo.getValue();
        String key = messageKeyField.getText();
        String value = messageValueArea.getText();
        
        if (topic == null || topic.isEmpty()) {
            sendStatusLabel.setText(I18N.get("msg.please_select_topic"));
            return;
        }
        if (value == null || value.isEmpty()) {
            sendStatusLabel.setText(I18N.get("msg.please_input_message"));
            return;
        }

        sendStatusLabel.setText(I18N.get("msg.sending"));

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, String> headers = parseHeaders(headersArea.getText());
                kafkaService.sendMessage(topic, key, value, headers);
                Platform.runLater(() -> {
                    sendStatusLabel.setText(I18N.get("msg.send_success"));
                    sendStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    sendStatusLabel.setText(I18N.get("msg.send_failed", e.getMessage()));
                    sendStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onStartConsume() {
        String topic = consumerTopicCombo.getValue();
        String groupId = consumerGroupField.getText();
        
        if (topic == null || topic.isEmpty()) {
            return;
        }
        
        messagesArea.clear();
        startConsumeBtn.setDisable(true);
        stopConsumeBtn.setDisable(false);

        kafkaService.consumeMessages(topic, groupId, record -> {
            Platform.runLater(() -> {
                String timestamp = Instant.ofEpochMilli(record.timestamp())
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                
                messagesArea.appendText(String.format("[%s] partition=%d offset=%d key=%s\n%s\n\n",
                    timestamp, record.partition(), record.offset(), record.key(), record.value()));
            });
        });
    }

    @FXML
    private void onStopConsume() {
        kafkaService.stopConsuming();
        startConsumeBtn.setDisable(false);
        stopConsumeBtn.setDisable(true);
    }

    @FXML
    private void onClearMessages() {
        messagesArea.clear();
    }

    private Map<String, String> parseHeaders(String text) {
        Map<String, String> headers = new HashMap<>();
        if (text == null || text.isEmpty()) return headers;
        
        for (String line : text.split("\n")) {
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
        }
        return headers;
    }

    public void dispose() {
        kafkaService.close();
    }
}
