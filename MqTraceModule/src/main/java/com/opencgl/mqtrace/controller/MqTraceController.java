package com.opencgl.mqtrace.controller;

import com.opencgl.mqtrace.i18n.I18N;
import com.opencgl.mqtrace.service.RabbitMqTraceService;
import com.opencgl.mqtrace.service.RocketMqTraceService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import com.alibaba.rocketmq.common.message.MessageExt;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 消息追踪控制器
 *
 * @author OpenCGL
 */
public class MqTraceController implements Initializable {

    // MQ 类型切换
    @FXML
    private ComboBox<String> mqTypeCombo;

    // RocketMQ 配置
    @FXML
    private TextField rocketNamesrvField;
    @FXML
    private TextField rocketAccessKeyField;
    @FXML
    private PasswordField rocketSecretKeyField;
    @FXML
    private Button rocketConnectBtn;
    @FXML
    private Label rocketStatusLabel;

    // RabbitMQ 配置
    @FXML
    private TextField rabbitHostField;
    @FXML
    private TextField rabbitPortField;
    @FXML
    private TextField rabbitUserField;
    @FXML
    private PasswordField rabbitPassField;
    @FXML
    private TextField rabbitVhostField;
    @FXML
    private Button rabbitConnectBtn;
    @FXML
    private Label rabbitStatusLabel;

    // 查询
    @FXML
    private TextField queryField;
    @FXML
    private ComboBox<String> queryTypeCombo;
    @FXML
    private TextArea resultArea;
    @FXML
    private ListView<String> topicListView;

    // MQ 类型切换面板
    @FXML
    private javafx.scene.control.TitledPane rocketConfigPane;
    @FXML
    private javafx.scene.control.TitledPane rabbitConfigPane;
    @FXML
    private javafx.scene.layout.VBox rocketSendPane;
    @FXML
    private javafx.scene.layout.VBox rabbitSendPane;
    @FXML
    private javafx.scene.layout.VBox rocketSubscribePane;
    @FXML
    private javafx.scene.layout.VBox rabbitSubscribePane;

    private final RocketMqTraceService rocketMqService = new RocketMqTraceService();
    private final RabbitMqTraceService rabbitMqService = new RabbitMqTraceService();
    private boolean rocketConnected = false;
    private boolean rabbitConnected = false;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-mq-trace");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Future<?>> backgroundTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;

    private void runAsync(Runnable action) {
        if (!disposed) {
            backgroundTasks.add(executor.submit(() -> {
                if (!disposed) action.run();
            }));
        }
    }

    private void runOnUi(Runnable action) {
        Platform.runLater(() -> {
            if (!disposed) action.run();
        });
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initComboBoxes();

        rocketNamesrvField.setText("localhost:9876");
        rabbitHostField.setText("localhost");
        rabbitPortField.setText("5672");
        rabbitUserField.setText("guest");
        rabbitPassField.setText("guest");
        rabbitVhostField.setText("/");

        mqTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateUIForMqType());
        updateUIForMqType();
        initI18n();
    }

    private void initI18n() {
        // FXML uses %key; bind any controls that must update on language change if needed.
        // Combo items and status labels are set dynamically with I18N.get().
    }

    private void initComboBoxes() {
        mqTypeCombo.getItems().setAll("RocketMQ", "RabbitMQ");
        mqTypeCombo.setValue("RocketMQ");

        queryTypeCombo.getItems().setAll(
                I18N.get("item.msg_id"),
                I18N.get("item.msg_key"),
                I18N.get("item.dlq"));
        queryTypeCombo.setValue(I18N.get("item.msg_id"));
    }

    private void updateUIForMqType() {
        boolean isRocket = "RocketMQ".equals(mqTypeCombo.getValue());

        // 配置面板
        if (rocketConfigPane != null) {
            rocketConfigPane.setVisible(isRocket);
            rocketConfigPane.setManaged(isRocket);
        }
        if (rabbitConfigPane != null) {
            rabbitConfigPane.setVisible(!isRocket);
            rabbitConfigPane.setManaged(!isRocket);
        }

        // 发送面板
        if (rocketSendPane != null) {
            rocketSendPane.setVisible(isRocket);
            rocketSendPane.setManaged(isRocket);
        }
        if (rabbitSendPane != null) {
            rabbitSendPane.setVisible(!isRocket);
            rabbitSendPane.setManaged(!isRocket);
        }

        // 订阅面板
        if (rocketSubscribePane != null) {
            rocketSubscribePane.setVisible(isRocket);
            rocketSubscribePane.setManaged(isRocket);
        }
        if (rabbitSubscribePane != null) {
            rabbitSubscribePane.setVisible(!isRocket);
            rabbitSubscribePane.setManaged(!isRocket);
        }
    }

    @FXML
    private void onRocketConnect() {
        String namesrv = rocketNamesrvField.getText() != null ? rocketNamesrvField.getText().trim() : "";
        String accessKey = rocketAccessKeyField.getText();
        String secretKey = rocketSecretKeyField.getText();

        rocketConnectBtn.setDisable(true);
        rocketStatusLabel.setText(I18N.get("status.connecting"));

        runAsync(() -> {
            // 使用插件 ClassLoader 作为当前线程（及 Netty 子线程）上下文，避免主程序 CL 加载不到 LanguageCode 等
            ClassLoader pluginCl = getClass().getClassLoader();
            Thread.currentThread().setContextClassLoader(pluginCl);
            try {
                rocketMqService.connect(namesrv, accessKey, secretKey);
                // 用 listTopics 代替 testConnection，既校验连接又拉取列表，失败时能拿到真实异常信息
                List<String> topics = rocketMqService.listTopics();
                runOnUi(() -> {
                    rocketConnected = true;
                    rocketStatusLabel.setText(I18N.get("status.connected"));
                    rocketStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                    topicListView.getItems().clear();
                    topicListView.getItems().addAll(topics);
                    rocketConnectBtn.setDisable(false);
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnUi(() -> {
                    rocketStatusLabel.setText(I18N.get("status.error", msg));
                    rocketStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    rocketConnectBtn.setDisable(false);
                });
            }
        });
    }

    @FXML
    private void onRabbitConnect() {
        rabbitConnectBtn.setDisable(true);
        rabbitStatusLabel.setText(I18N.get("status.connecting"));

        runAsync(() -> {
            try {
                rabbitMqService.connect(
                        rabbitHostField.getText(),
                        Integer.parseInt(rabbitPortField.getText()),
                        rabbitUserField.getText(),
                        rabbitPassField.getText(),
                        rabbitVhostField.getText());
                boolean success = rabbitMqService.testConnection();
                runOnUi(() -> {
                    if (success) {
                        rabbitConnected = true;
                        rabbitStatusLabel.setText(I18N.get("status.connected"));
                        rabbitStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                    } else {
                        rabbitStatusLabel.setText(I18N.get("status.connect_failed"));
                        rabbitStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    }
                    rabbitConnectBtn.setDisable(false);
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    rabbitStatusLabel.setText(I18N.get("status.error", e.getMessage()));
                    rabbitStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    rabbitConnectBtn.setDisable(false);
                });
            }
        });
    }

    private void refreshRocketTopics() {
        runAsync(() -> {
            try {
                List<String> topics = rocketMqService.listTopics();
                runOnUi(() -> {
                    topicListView.getItems().clear();
                    topicListView.getItems().addAll(topics);
                });
            } catch (Exception e) {
                runOnUi(() -> resultArea.setText(I18N.get("status.get_topics_failed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onQuery() {
        String mqType = mqTypeCombo.getValue();
        String queryType = queryTypeCombo.getValue();
        String queryValue = queryField.getText();

        if (queryValue == null || queryValue.isEmpty()) {
            resultArea.setText(I18N.get("status.input_query_first"));
            return;
        }

        resultArea.setText(I18N.get("status.querying"));

        if ("RocketMQ".equals(mqType)) {
            queryRocketMq(queryType, queryValue);
        } else {
            queryRabbitMq(queryType, queryValue);
        }
    }

    private void queryRocketMq(String queryType, String queryValue) {
        if (!rocketConnected) {
            resultArea.setText(I18N.get("status.connect_rocket_first"));
            return;
        }

        runAsync(() -> {
            try {
                StringBuilder result = new StringBuilder();

                if (I18N.get("item.msg_id").equals(queryType)) {
                    MessageExt msg = rocketMqService.queryMessageById(queryValue);
                    result.append(formatRocketMessage(msg));
                } else if (I18N.get("item.msg_key").equals(queryType)) {
                    String selectedTopic = topicListView.getSelectionModel().getSelectedItem();
                    if (selectedTopic == null) {
                        runOnUi(() -> resultArea.setText(I18N.get("status.select_topic_first")));
                        return;
                    }
                    List<MessageExt> messages = rocketMqService.queryMessageByKey(selectedTopic, queryValue, 10);
                    for (MessageExt msg : messages) {
                        result.append(formatRocketMessage(msg)).append("\n---\n");
                    }
                }

                runOnUi(() -> resultArea.setText(result.toString()));
            } catch (Exception e) {
                runOnUi(() -> resultArea.setText(I18N.get("status.query_failed", e.getMessage())));
            }
        });
    }

    private void queryRabbitMq(String queryType, String queryValue) {
        if (!rabbitConnected) {
            resultArea.setText(I18N.get("status.connect_rabbit_first"));
            return;
        }

        runAsync(() -> {
            try {
                List<RabbitMqTraceService.TracedMessage> messages = rabbitMqService.peekMessages(queryValue, 10);
                StringBuilder result = new StringBuilder();
                for (RabbitMqTraceService.TracedMessage msg : messages) {
                    result.append(formatRabbitMessage(msg)).append("\n---\n");
                }
                runOnUi(() -> resultArea.setText(result.toString()));
            } catch (Exception e) {
                runOnUi(() -> resultArea.setText(I18N.get("status.query_failed", e.getMessage())));
            }
        });
    }

    private String formatRocketMessage(MessageExt msg) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String time = Instant.ofEpochMilli(msg.getBornTimestamp())
                .atZone(ZoneId.systemDefault()).format(fmt);

        return String.format("%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s",
                I18N.get("format.rocket.msg_id", msg.getMsgId()),
                I18N.get("format.rocket.topic", msg.getTopic()),
                I18N.get("format.rocket.tags", msg.getTags()),
                I18N.get("format.rocket.keys", msg.getKeys()),
                I18N.get("format.rocket.born_time", time),
                I18N.get("format.rocket.born_host", msg.getBornHostNameString()),
                I18N.get("format.rocket.store_host", msg.getStoreHost()),
                I18N.get("format.rocket.body", new String(msg.getBody())));
    }

    private String formatRabbitMessage(RabbitMqTraceService.TracedMessage msg) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String time = Instant.ofEpochMilli(msg.timestamp())
                .atZone(ZoneId.systemDefault()).format(fmt);

        return String.format("%s\n%s\n%s\n%s\n%s\n%s",
                I18N.get("format.rabbit.msg_id", msg.messageId()),
                I18N.get("format.rabbit.exchange", msg.exchange()),
                I18N.get("format.rabbit.routing_key", msg.routingKey()),
                I18N.get("format.rabbit.queue", msg.queue()),
                I18N.get("format.rabbit.time", time),
                I18N.get("format.rabbit.body", msg.body()));
    }

    @FXML
    private void onClear() {
        resultArea.clear();
        queryField.clear();
    }

    // ==================== 消息发送功能 ====================

    @FXML
    private TextField sendTopicField;
    @FXML
    private TextField sendTagsField;
    @FXML
    private TextField sendKeysField;
    @FXML
    private TextArea sendBodyArea;
    @FXML
    private Label sendStatusLabel;

    // RabbitMQ 发送字段
    @FXML
    private TextField rabbitExchangeField;
    @FXML
    private TextField rabbitRoutingKeyField;
    @FXML
    private TextArea rabbitSendBodyArea;
    @FXML
    private Label rabbitSendStatusLabel;

    @FXML
    private void onSendRocketMessage() {
        if (!rocketConnected) {
            sendStatusLabel.setText(I18N.get("status.connect_rocket_first"));
            sendStatusLabel.setStyle("-fx-text-fill: #f44336;");
            return;
        }

        String topic = sendTopicField.getText();
        String tags = sendTagsField.getText();
        String keys = sendKeysField.getText();
        String body = sendBodyArea.getText();

        if (topic == null || topic.isEmpty()) {
            sendStatusLabel.setText(I18N.get("status.input_topic_first"));
            return;
        }
        if (body == null || body.isEmpty()) {
            sendStatusLabel.setText(I18N.get("status.input_body_first"));
            return;
        }

        sendStatusLabel.setText(I18N.get("status.sending"));

        runAsync(() -> {
            try {
                String msgId = rocketMqService.sendMessage(topic, tags, keys, body);
                runOnUi(() -> {
                    sendStatusLabel.setText(I18N.get("status.send_success_id", msgId));
                    sendStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    sendStatusLabel.setText(I18N.get("status.error", e.getMessage()));
                    sendStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onSendRabbitMessage() {
        if (!rabbitConnected) {
            rabbitSendStatusLabel.setText(I18N.get("status.connect_rabbit_first"));
            rabbitSendStatusLabel.setStyle("-fx-text-fill: #f44336;");
            return;
        }

        String exchange = rabbitExchangeField.getText();
        String routingKey = rabbitRoutingKeyField.getText();
        String body = rabbitSendBodyArea.getText();

        if (body == null || body.isEmpty()) {
            rabbitSendStatusLabel.setText(I18N.get("status.input_body_first"));
            return;
        }

        rabbitSendStatusLabel.setText(I18N.get("status.sending"));

        runAsync(() -> {
            try {
                rabbitMqService.sendMessage(exchange != null ? exchange : "",
                        routingKey != null ? routingKey : "", body, null);
                runOnUi(() -> {
                    rabbitSendStatusLabel.setText(I18N.get("status.send_success"));
                    rabbitSendStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    rabbitSendStatusLabel.setText(I18N.get("status.error", e.getMessage()));
                    rabbitSendStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    // ==================== 消息订阅功能 ====================

    @FXML
    private TextField subscribeTopicField;
    @FXML
    private TextField subscribeGroupField;
    @FXML
    private TextField subscribeTagsField;
    @FXML
    private TextArea consumeResultArea;
    @FXML
    private Button startConsumeBtn;
    @FXML
    private Button stopConsumeBtn;

    // RabbitMQ 订阅字段
    @FXML
    private TextField rabbitQueueField;
    @FXML
    private TextArea rabbitConsumeResultArea;
    @FXML
    private Button rabbitStartConsumeBtn;
    @FXML
    private Button rabbitStopConsumeBtn;

    @FXML
    private void onStartRocketConsume() {
        if (!rocketConnected) {
            consumeResultArea.setText(I18N.get("status.connect_rocket_first"));
            return;
        }

        String topic = subscribeTopicField.getText();
        String group = subscribeGroupField.getText();
        String tags = subscribeTagsField.getText();

        if (topic == null || topic.isEmpty()) {
            consumeResultArea.setText(I18N.get("status.input_topic_first"));
            return;
        }
        if (group == null || group.isEmpty()) {
            group = "MqTrace_Consumer_" + System.currentTimeMillis();
            subscribeGroupField.setText(group);
        }

        consumeResultArea.clear();
        startConsumeBtn.setDisable(true);
        stopConsumeBtn.setDisable(false);

        String finalGroup = group;
        runAsync(() -> {
            try {
                rocketMqService.subscribe(topic, finalGroup, tags, msg -> {
                    runOnUi(() -> {
                        consumeResultArea.appendText(formatRocketMessage(msg) + "\n---\n");
                    });
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    consumeResultArea.appendText(I18N.get("status.subscribe_failed", e.getMessage()));
                    startConsumeBtn.setDisable(false);
                    stopConsumeBtn.setDisable(true);
                });
            }
        });
    }

    @FXML
    private void onStopRocketConsume() {
        rocketMqService.stopConsume();
        startConsumeBtn.setDisable(false);
        stopConsumeBtn.setDisable(true);
    }

    @FXML
    private void onStartRabbitConsume() {
        if (!rabbitConnected) {
            rabbitConsumeResultArea.setText(I18N.get("status.connect_rabbit_first"));
            return;
        }

        String queue = rabbitQueueField.getText();
        if (queue == null || queue.isEmpty()) {
            rabbitConsumeResultArea.setText(I18N.get("status.input_queue_first"));
            return;
        }

        rabbitConsumeResultArea.clear();
        rabbitStartConsumeBtn.setDisable(true);
        rabbitStopConsumeBtn.setDisable(false);

        runAsync(() -> {
            try {
                rabbitMqService.subscribe(queue, msg -> {
                    runOnUi(() -> {
                        rabbitConsumeResultArea.appendText(formatRabbitMessage(msg) + "\n---\n");
                    });
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    rabbitConsumeResultArea.appendText(I18N.get("status.subscribe_failed", e.getMessage()));
                    rabbitStartConsumeBtn.setDisable(false);
                    rabbitStopConsumeBtn.setDisable(true);
                });
            }
        });
    }

    @FXML
    private void onStopRabbitConsume() {
        rabbitMqService.stopConsume();
        rabbitStartConsumeBtn.setDisable(false);
        rabbitStopConsumeBtn.setDisable(true);
    }

    @FXML
    private void onClearConsumeResult() {
        consumeResultArea.clear();
        rabbitConsumeResultArea.clear();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        backgroundTasks.forEach(task -> task.cancel(true));
        backgroundTasks.clear();
        executor.shutdownNow();
        try {
            rocketMqService.close();
        } catch (RuntimeException error) {
            // Continue so RabbitMQ resources are not leaked by a RocketMQ failure.
        }
        try {
            rabbitMqService.close();
        } catch (RuntimeException error) {
            // Both clients are best-effort closed independently.
        }
    }
}
