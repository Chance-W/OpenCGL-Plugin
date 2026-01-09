package com.opencgl.controller;

import com.opencgl.rocketmq.i18n.I18N;

import java.io.StringWriter;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import com.alibaba.rocketmq.common.consumer.ConsumeFromWhere;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.LoadingMask;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.dao.RocketMqConsumerWidgetDao;
import com.opencgl.model.RocketMqConsumerWidgetDto;
import com.opencgl.views.RocketMqConsumerWidgetView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import lombok.SneakyThrows;

public class RocketMqConsumerWidgetController extends RocketMqConsumerWidgetView
        implements Initializable, TreeOperateService<RocketMqConsumerWidgetDto> {

    private static final Logger logger = LoggerFactory.getLogger(RocketMqConsumerWidgetController.class);
    private TreeView<RocketMqConsumerWidgetDto> treeView = null;
    private final RocketMqConsumerWidgetDao rocketMqConsumerWidgetDao = new RocketMqConsumerWidgetDao();
    private DefaultMQPushConsumer consumer;
    private final LoadingMask loadingMask = new LoadingMask();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-rocketmq-consumer-start");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Future<?> startTask;
    private volatile boolean disposed;
    /**
     * Monotonically increasing operation token.  A cancelled Future cannot
     * reliably stop RocketMQ's blocking start call, so the token is also
     * checked before publishing a newly started consumer.
     */
    private final AtomicLong operationToken = new AtomicLong();

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
        initI18n();
        showTooltip();
        // 使用新版TreeViewBuilder构建树
        VBox treeVbox = new TreeViewBuilder<RocketMqConsumerWidgetDto>()
                .onTreeCreated(tree -> this.treeView = tree)
                .service(this)
                .dataType(RocketMqConsumerWidgetDto.class)
                .enableDragDrop(true)
                .build();
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeVbox);
    }

    public void init() throws Exception {
        rocketMqConsumerWidgetDao.checkTable();
        headerVbox.getChildren().remove(processBarHBox);
    }

    private void initI18n() {
        Tooltip sendTip = new Tooltip();
        sendTip.textProperty().bind(I18N.getBinding("tooltip.start_consume"));
        sendButton.setTooltip(sendTip);
        Tooltip closeTip = new Tooltip();
        closeTip.textProperty().bind(I18N.getBinding("tooltip.stop_consume"));
        closeButton.setTooltip(closeTip);
        Tooltip refreshTip = new Tooltip();
        refreshTip.textProperty().bind(I18N.getBinding("tooltip.refresh"));
        refreshButton.setTooltip(refreshTip);
        Tooltip copyTip = new Tooltip();
        copyTip.textProperty().bind(I18N.getBinding("tooltip.copy"));
        copyButton.setTooltip(copyTip);
        Tooltip saveTip = new Tooltip();
        saveTip.textProperty().bind(I18N.getBinding("tooltip.save"));
        saveButton.setTooltip(saveTip);
    }

    public void showTooltip() {
        // Tooltips are bound in initI18n
        nameServerAddrTextField.setOnMouseEntered(
                event -> {
                    if (StringUtils.isNotEmpty(nameServerAddrTextField.getText())) {
                        nameServerAddrTextField.setTooltip(new Tooltip(nameServerAddrTextField.getText()));
                    }
                });
    }

    @FXML
    public void stopConsumer() {
        operationToken.incrementAndGet();
        Future<?> task = startTask;
        startTask = null;
        if (task != null) task.cancel(true);
        DefaultMQPushConsumer current = consumer;
        consumer = null;
        shutdownConsumer(current);
        Platform.runLater(() -> headerVbox.getChildren().remove(processBarHBox));
        sendButton.setDisable(false);
    }

    private void shutdownConsumer(DefaultMQPushConsumer target) {
        if (target == null) return;
        try {
            target.shutdown();
        } catch (RuntimeException error) {
            logger.warn("Failed to shut down RocketMQ consumer", error);
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        operationToken.incrementAndGet();
        Future<?> task = startTask;
        startTask = null;
        if (task != null) task.cancel(true);
        DefaultMQPushConsumer current = consumer;
        consumer = null;
        shutdownConsumer(current);
        executor.shutdownNow();
        loadingMask.hide();
    }

    public void consumerLabelAction() {
        if (StringUtils.isEmpty(nameServerAddrTextField.getText())
                || StringUtils.isEmpty(topicTextField.getText())) {
            DialogUtil.showErrorInfo(I18N.get("message.invalid_params"), mainStackPane);
            return;
        }

        final long token = operationToken.incrementAndGet();
        Future<?> previous = startTask;
        if (previous != null) previous.cancel(true);
        startTask = executor.submit(() -> {
            if (disposed || token != operationToken.get()) return;
            Platform.runLater(() -> {
                if (!disposed) loadingMask.show(mainStackPane);
            });
            try {
                DefaultMQPushConsumer newConsumer = new DefaultMQPushConsumer("consumer1");
                newConsumer.setInstanceName(FormatVariableUtil.format("consumer{Random}"));
                // 同样也要设置NameServer地址
                newConsumer.setNamesrvAddr(nameServerAddrTextField.getText());
                // 这里设置的是一个consumer的消费策略
                // CONSUME_FROM_LAST_OFFSET 默认策略，从该队列最尾开始消费，即跳过历史消息
                // CONSUME_FROM_FIRST_OFFSET 从队列最开始开始消费，即历史消息（还储存在broker的）全部消费一遍
                // CONSUME_FROM_TIMESTAMP 从某个时间点开始消费，和setConsumeTimestamp()配合使用，默认是半个小时以前
                newConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_TIMESTAMP);
                newConsumer.subscribe(topicTextField.getText(), "*");

                newConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                    for (MessageExt messageExt : msgs) {
                        String messageBody = new String(messageExt.getBody());
                        String logMsg = I18N.get("message.consumer_response", messageExt.getMsgId(), messageBody);
                        logger.info(logMsg);
                        Platform.runLater(() -> {
                            if (!disposed) outputTextArea.appendText(logMsg + "\n");
                        });
                    }
                    // 返回消费状态
                    // CONSUME_SUCCESS 消费成功
                    // RECONSUME_LATER 消费失败，需要稍后重新消费
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                });
                newConsumer.start();
                if (disposed || token != operationToken.get()) {
                    shutdownConsumer(newConsumer);
                    return;
                }
                consumer = newConsumer;
                Platform.runLater(() -> {
                    if (!disposed) {
                        sendButton.setDisable(true);
                        headerVbox.getChildren().addAll(processBarHBox);
                    }
                });
            } catch (Exception e) {
                logger.error("Consumer error", e);
                Platform.runLater(() -> {
                    if (!disposed) outputTextArea.setText(e.getMessage());
                });
            } finally {
                Platform.runLater(() -> {
                    if (!disposed) loadingMask.hide();
                });
            }
        });
    }

    @FXML
    public void refreshLabelAction() {
        if (null == treeView.getSelectionModel().getSelectedItem()
                || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            return;
        }
        RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        changeToDisplay(rocketMqConsumerWidgetDto);
    }

    public void saveLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
                || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, I18N.get("message.no_node_selected"));
            return;
        }
        RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        update(rocketMqConsumerWidgetDto);
        TooltipUtil.showToast(mainStackPane, I18N.get("message.operation_success"));
    }

    public void copyLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
                || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, I18N.get("message.no_node_selected"));
            return;
        }
        RocketMqConsumerWidgetDto originRocketMqConsumerWidgetDto = treeView.getSelectionModel().getSelectedItem()
                .getValue();

        RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto = new RocketMqConsumerWidgetDto();
        rocketMqConsumerWidgetDto.setParentId(originRocketMqConsumerWidgetDto.getParentId());
        rocketMqConsumerWidgetDto.setIsLeaf(originRocketMqConsumerWidgetDto.getIsLeaf());
        rocketMqConsumerWidgetDto
                .setName(I18N.get("label.copy_prefix") + originRocketMqConsumerWidgetDto.getName() + "-"
                        + FormatVariableUtil.getRandom(8));
        rocketMqConsumerWidgetDto.setNameServerAddr(originRocketMqConsumerWidgetDto.getNameServerAddr());
        rocketMqConsumerWidgetDto.setNameTopic(originRocketMqConsumerWidgetDto.getNameTopic());
        try {
            Long newTreeItemId = rocketMqConsumerWidgetDao.insertData(rocketMqConsumerWidgetDto);
            rocketMqConsumerWidgetDto.setId(newTreeItemId);
            CustomizeTreeItem<RocketMqConsumerWidgetDto> treeItem = new CustomizeTreeItem<>(rocketMqConsumerWidgetDto);
            treeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(treeItem);
            treeView.getSelectionModel().select(treeItem);
            TooltipUtil.showToast(contentBorderPane, I18N.get("message.operation_success"));
        } catch (Exception e) {
            logger.error("", e);
            TooltipUtil.showToast(contentBorderPane, e.getMessage());
        }

    }

    @Override
    public CustomizeTreeItem<RocketMqConsumerWidgetDto> add(RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto) {
        rocketMqConsumerWidgetDto.setNameServerAddr(nameServerAddrTextField.getText());
        rocketMqConsumerWidgetDto.setNameTopic(topicTextField.getText());
        Long newId;
        try {
            newId = rocketMqConsumerWidgetDao.insertData(rocketMqConsumerWidgetDto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        rocketMqConsumerWidgetDto.setId(newId);
        return new CustomizeTreeItem<>(rocketMqConsumerWidgetDto);
    }

    @Override
    public CustomizeTreeItem<RocketMqConsumerWidgetDto> importData(
            RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto) {
        Long newId;
        try {
            newId = rocketMqConsumerWidgetDao.insertData(rocketMqConsumerWidgetDto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        rocketMqConsumerWidgetDto.setId(newId);
        return new CustomizeTreeItem<>(rocketMqConsumerWidgetDto);
    }

    @Override
    public CustomizeTreeItem<RocketMqConsumerWidgetDto> delete(RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto) {
        try {
            rocketMqConsumerWidgetDao.delLevelData(rocketMqConsumerWidgetDto);
            return new CustomizeTreeItem<>(rocketMqConsumerWidgetDto);
        } catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<RocketMqConsumerWidgetDto> update(RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto) {
        try {
            rocketMqConsumerWidgetDto.setName(rocketMqConsumerWidgetDto.getName());
            rocketMqConsumerWidgetDto.setNameServerAddr(nameServerAddrTextField.getText());
            rocketMqConsumerWidgetDto.setNameTopic(topicTextField.getText());
            return new CustomizeTreeItem<>(rocketMqConsumerWidgetDto);
        } catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void changeToDisplay(RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto) {
        try {
            nameServerAddrTextField.setText(rocketMqConsumerWidgetDto.getNameServerAddr());
            topicTextField.setText(rocketMqConsumerWidgetDto.getNameTopic());
        } catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }

    @Override
    public List<RocketMqConsumerWidgetDto> queryAll() {
        try {
            return rocketMqConsumerWidgetDao.queryAllData();
        } catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }

    @Override
    public void updatePositionOnly(RocketMqConsumerWidgetDto dto) {
        try {
            rocketMqConsumerWidgetDao.updatePositionOnly(dto);
        } catch (Exception e) {
            logger.error(I18N.get("message.update_position_failed"), e);
        }
    }
}
