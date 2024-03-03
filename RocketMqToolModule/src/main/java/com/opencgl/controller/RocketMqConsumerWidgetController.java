package com.opencgl.controller;

import java.io.StringWriter;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

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
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.TreeViewUtil;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.dao.RocketMqConsumerWidgetDao;
import com.opencgl.model.RocketMqConsumerWidgetDto;
import com.opencgl.views.RocketMqConsumerWidgetView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeView;
import lombok.SneakyThrows;

public class RocketMqConsumerWidgetController extends RocketMqConsumerWidgetView implements Initializable, TreeOperateService<RocketMqConsumerWidgetDto> {

    private static final Logger logger = LoggerFactory.getLogger(RocketMqConsumerWidgetController.class);
    private TreeView<RocketMqConsumerWidgetDto> treeView = null;
    private final RocketMqConsumerWidgetDao rocketMqConsumerWidgetDao = new RocketMqConsumerWidgetDao();
    private DefaultMQPushConsumer consumer;

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
        showTooltip();
        List<RocketMqConsumerWidgetDto> rocketMqConsumerWidgetDtos = queryAll();
        treeView = TreeViewUtil.buildTreeView(rocketMqConsumerWidgetDtos, mainStackPane, this, RocketMqConsumerWidgetDto.class);
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeView);
    }


    public void init() throws Exception {
        rocketMqConsumerWidgetDao.checkTable();
        headerVbox.getChildren().remove(processBarHBox);
    }

    public void showTooltip() {
        nameServerAddrTextField.setOnMouseEntered(event -> nameServerAddrTextField.setTooltip(new Tooltip(nameServerAddrTextField.getText())));
        sendButton.setTooltip(new Tooltip("开启消费模式"));
        closeButton.setTooltip(new Tooltip("关闭消费模式"));
        refreshButton.setTooltip(new Tooltip("刷新"));
        copyButton.setTooltip(new Tooltip("复制"));
        saveButton.setTooltip(new Tooltip("保存"));
    }


    @FXML
    public void stopConsumer() {
        consumer.shutdown();
        Platform.runLater(() -> headerVbox.getChildren().remove(processBarHBox));
        sendButton.setDisable(false);
    }

    @FXML
    public void consumerLabelAction() {
        if (StringUtils.isEmpty(nameServerAddrTextField.getText())
            || StringUtils.isEmpty(topicTextField.getText())) {
            DialogUtil.showErrorInfo("参数不合法", mainStackPane);
            return;
        }

        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> LoadingUtil.show(mainStackPane));
            try {
                consumer = new DefaultMQPushConsumer("consumer1");
                consumer.setInstanceName(FormatVariableUtil.format("consumer{Random}"));
                //同样也要设置NameServer地址
                consumer.setNamesrvAddr(nameServerAddrTextField.getText());
                //这里设置的是一个consumer的消费策略
                //CONSUME_FROM_LAST_OFFSET 默认策略，从该队列最尾开始消费，即跳过历史消息
                //CONSUME_FROM_FIRST_OFFSET 从队列最开始开始消费，即历史消息（还储存在broker的）全部消费一遍
                //CONSUME_FROM_TIMESTAMP 从某个时间点开始消费，和setConsumeTimestamp()配合使用，默认是半个小时以前
                consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_TIMESTAMP);
                consumer.subscribe(topicTextField.getText(), "*");

                consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                    for (MessageExt messageExt : msgs) {
                        String messageBody = new String(messageExt.getBody());
                        logger.info("消费响应：msgId : {},  msgBody : {}", messageExt.getMsgId(), messageBody);//输出消息内容
                        Platform.runLater(() -> outputTextArea.appendText("消费响应：msgId : " + messageExt.getMsgId() + ",  msgBody : " + messageBody + "\n"));
                    }
                    //返回消费状态
                    //CONSUME_SUCCESS 消费成功
                    //RECONSUME_LATER 消费失败，需要稍后重新消费
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                });
                consumer.start();
                sendButton.setDisable(true);
                Platform.runLater(() -> headerVbox.getChildren().addAll(processBarHBox));
            }
            catch (Exception e) {
                StringWriter sw = new StringWriter();
                logger.error("", e);
                outputTextArea.setText(sw.toString());
            }
            finally {
                Platform.runLater(() -> LoadingUtil.remove(mainStackPane));
            }
        });
    }

    @FXML
    public void refreshLabelAction() {
        if (null == treeView.getSelectionModel().getSelectedItem()
            || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()
        ) {
            return;
        }
        RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        changeToDisplay(rocketMqConsumerWidgetDto);
    }

    @FXML
    public void saveLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
            || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, "子节点未选中");
            return;
        }
        RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        update(rocketMqConsumerWidgetDto);
    }


    @FXML
    public void copyLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
            || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, "子节点未选中");
            return;
        }
        RocketMqConsumerWidgetDto originRocketMqConsumerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();

        RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto = new RocketMqConsumerWidgetDto();
        rocketMqConsumerWidgetDto.setParentId(originRocketMqConsumerWidgetDto.getParentId());
        rocketMqConsumerWidgetDto.setIsLeaf(originRocketMqConsumerWidgetDto.getIsLeaf());
        rocketMqConsumerWidgetDto.setName("copy-" + originRocketMqConsumerWidgetDto.getName() + "-" + FormatVariableUtil.getRandom(8));
        rocketMqConsumerWidgetDto.setNameServerAddr(originRocketMqConsumerWidgetDto.getNameServerAddr());
        rocketMqConsumerWidgetDto.setNameTopic(originRocketMqConsumerWidgetDto.getNameTopic());
        try {
            Long newTreeItemId = rocketMqConsumerWidgetDao.insertData(rocketMqConsumerWidgetDto);
            rocketMqConsumerWidgetDto.setId(newTreeItemId);
            CustomizeTreeItem<RocketMqConsumerWidgetDto> treeItem = new CustomizeTreeItem<>(rocketMqConsumerWidgetDto);
            treeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(treeItem);
            treeView.getSelectionModel().select(treeItem);
            TooltipUtil.showToast(contentBorderPane, "成功");
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        rocketMqConsumerWidgetDto.setId(newId);
        return new CustomizeTreeItem<>(rocketMqConsumerWidgetDto);
    }

    @Override
    public CustomizeTreeItem<RocketMqConsumerWidgetDto> importData(RocketMqConsumerWidgetDto rocketMqConsumerWidgetDto) {
        Long newId;
        try {
            newId = rocketMqConsumerWidgetDao.insertData(rocketMqConsumerWidgetDto);
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }


    @Override
    public List<RocketMqConsumerWidgetDto> queryAll() {
        try {
            return rocketMqConsumerWidgetDao.queryAllData();
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }
}


