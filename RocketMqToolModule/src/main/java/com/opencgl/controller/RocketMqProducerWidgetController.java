package com.opencgl.controller;

import com.opencgl.rocketmq.i18n.I18N;

import java.io.PrintWriter;
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

import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.LoadingMask;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.dao.RocketMqProducerWidgetDao;
import com.opencgl.factory.RmqSendMessageFactory;
import com.opencgl.model.RmqRequest;
import com.opencgl.model.RmqResponse;
import com.opencgl.model.RocketMqProducerWidgetDto;
import com.opencgl.views.RocketMqProducerWidgetView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class RocketMqProducerWidgetController extends RocketMqProducerWidgetView
        implements Initializable, TreeOperateService<RocketMqProducerWidgetDto> {

    private final Logger logger = LoggerFactory.getLogger(RocketMqProducerWidgetController.class);
    private TreeView<RocketMqProducerWidgetDto> treeView;
    SendMessageService<RmqRequest, RmqResponse> sendMessageService = RmqSendMessageFactory.sendRmqMessage();
    private final RocketMqProducerWidgetDao rocketMqProducerWidgetDao = new RocketMqProducerWidgetDao();
    private final LoadingMask loadingMask = new LoadingMask();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-rocketmq-producer");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Future<?> sendTask;
    private volatile boolean disposed;
    private final AtomicLong sendToken = new AtomicLong();

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        rocketMqProducerWidgetDao.checkTable();
        initI18n();
        showTooltip();
        setControlAndStyle();
        // 使用新版TreeViewBuilder构建树
        VBox treeViewVbox = new TreeViewBuilder<RocketMqProducerWidgetDto>()
                .enableSearch(true)
                .onTreeCreated(tree -> this.treeView = tree)
                .service(this)
                .dataType(RocketMqProducerWidgetDto.class)
                .enableDragDrop(true)
                .build();
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeViewVbox);
    }

    @FXML
    public void sendLabelAction() {
        if (StringUtils.isEmpty(nameServerAddrTextField.getText())
                || StringUtils.isEmpty(topicTextField.getText())
                || Integer.parseInt(countTextField.getText()) < 1
                || StringUtils.isEmpty(inputTextArea.getText())) {
            DialogUtil.showErrorInfo(I18N.get("message.invalid_params"), mainStackPane);
            return;
        }
        String requestJson = FormatVariableUtil.format(inputTextArea.getText());
        RmqRequest rmqRequest = RmqRequest.builder()
                .nameServerAddr(nameServerAddrTextField.getText())
                .nameTopic(topicTextField.getText())
                .tags(tagsTextField.getText())
                .count(Integer.parseInt(countTextField.getText()))
                .requestMessage(inputTextArea.getText())
                .build();

        final long token = sendToken.incrementAndGet();
        Future<?> previous = sendTask;
        if (previous != null) previous.cancel(true);
        sendTask = executor.submit(() -> {
            if (disposed || token != sendToken.get()) return;
            Platform.runLater(() -> {
                if (!disposed) loadingMask.show(contentPanel);
            });
            try {
                RmqResponse out = sendMessageService.send(rmqRequest);
                logger.info("response is {}", out.getResultMsg());
                Platform.runLater(() -> {
                    if (!disposed && token == sendToken.get()) outputTextArea.setText(out.getResultMsg());
                });
            } catch (Throwable e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                logger.error("", e);
                Platform.runLater(() -> {
                    if (!disposed && token == sendToken.get()) outputTextArea.setText(sw.toString());
                });
            } finally {
                Platform.runLater(() -> {
                    if (!disposed && token == sendToken.get()) loadingMask.hide();
                });
                OperationHisRecord
                        .record(I18N.get("label.history.send_rmq_message") + "\n"
                                + I18N.get("label.history.namesrv_addr") + nameServerAddrTextField.getText() +
                                "\n" + I18N.get("label.history.topic") + topicTextField.getText() + "\n"
                                + I18N.get("label.history.tags") + tagsTextField.getText()
                                + "\n" + I18N.get("label.history.loop_count") + countTextField.getText()
                                + "\n" + I18N.get("label.history.input") + "\n" + requestJson + "\n"
                                + I18N.get("label.history.output") + "\n" + outputTextArea.getText() + "\t");
            }
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        sendToken.incrementAndGet();
        Future<?> task = sendTask;
        sendTask = null;
        if (task != null) task.cancel(true);
        executor.shutdownNow();
    }

    public void refreshLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
                || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, I18N.get("message.no_node_selected"));
            return;
        }
        RocketMqProducerWidgetDto rocketMqProducerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        try {
            nameServerAddrTextField.setText(rocketMqProducerWidgetDto.getNameServerAddr());
            topicTextField.setText(rocketMqProducerWidgetDto.getNameTopic());
            tagsTextField.setText(rocketMqProducerWidgetDto.getTags());
            countTextField.setText(String.valueOf(rocketMqProducerWidgetDto.getCount()));
            inputTextArea.setText(rocketMqProducerWidgetDto.getInputText());
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    public void saveLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
                || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, I18N.get("message.no_node_selected"));
            return;
        }
        RocketMqProducerWidgetDto rocketMqProducerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        rocketMqProducerWidgetDto.setNameServerAddr(nameServerAddrTextField.getText());
        rocketMqProducerWidgetDto.setNameTopic(topicTextField.getText());
        rocketMqProducerWidgetDto.setTags(tagsTextField.getText());
        rocketMqProducerWidgetDto.setCount(Integer.parseInt(countTextField.getText()));
        rocketMqProducerWidgetDto.setInputText(inputTextArea.getText());
        update(rocketMqProducerWidgetDto);
        TooltipUtil.showToast(mainStackPane, I18N.get("message.operation_success"));
    }

    public void copyLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
                || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, I18N.get("message.no_node_selected"));
            return;
        }
        RocketMqProducerWidgetDto originRocketMqProducerWidgetDto = treeView.getSelectionModel().getSelectedItem()
                .getValue();

        RocketMqProducerWidgetDto rocketMqProducerWidgetDto = new RocketMqProducerWidgetDto();
        rocketMqProducerWidgetDto
                .setName(I18N.get("label.copy_prefix") + originRocketMqProducerWidgetDto.getName() + "-"
                        + FormatVariableUtil.getRandom(8));
        rocketMqProducerWidgetDto.setParentId(originRocketMqProducerWidgetDto.getParentId());
        rocketMqProducerWidgetDto.setNameServerAddr(originRocketMqProducerWidgetDto.getNameServerAddr());
        rocketMqProducerWidgetDto.setNameTopic(originRocketMqProducerWidgetDto.getNameTopic());
        rocketMqProducerWidgetDto.setTags(originRocketMqProducerWidgetDto.getTags());
        rocketMqProducerWidgetDto.setIsLeaf(originRocketMqProducerWidgetDto.getIsLeaf());
        rocketMqProducerWidgetDto.setCount(originRocketMqProducerWidgetDto.getCount());
        rocketMqProducerWidgetDto.setInputText(originRocketMqProducerWidgetDto.getInputText());
        try {
            Long newTreeItemId = rocketMqProducerWidgetDao.insertData(rocketMqProducerWidgetDto);
            rocketMqProducerWidgetDto.setId(newTreeItemId);
            CustomizeTreeItem<RocketMqProducerWidgetDto> treeItem = new CustomizeTreeItem<>(rocketMqProducerWidgetDto);
            treeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(treeItem);
            treeView.getSelectionModel().select(treeItem);
            TooltipUtil.showToast(contentBorderPane, I18N.get("message.operation_success"));
        } catch (Exception e) {
            logger.error("", e);
            TooltipUtil.showToast(contentBorderPane, e.getMessage());
        }
    }

    public void showTooltip() {
        nameServerAddrTextField.setOnMouseEntered(
                event -> {
                    if (StringUtils.isNotEmpty(nameServerAddrTextField.getText())) {
                        nameServerAddrTextField.setTooltip(new Tooltip(nameServerAddrTextField.getText()));
                    }
                });
        topicTextField.setOnMouseEntered(event -> {
            if (StringUtils.isNotEmpty(topicTextField.getText())) {
                topicTextField.setTooltip(new Tooltip(topicTextField.getText()));
            }
        });
    }

    private void initI18n() {
        Tooltip sendTip = new Tooltip();
        sendTip.textProperty().bind(I18N.getBinding("tooltip.send_request"));
        sendButton.setTooltip(sendTip);
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

    public void setControlAndStyle() {
        // Tooltips are now bound in initI18n
    }

    @Override
    public CustomizeTreeItem<RocketMqProducerWidgetDto> add(RocketMqProducerWidgetDto rocketMqProducerWidgetDto) {
        rocketMqProducerWidgetDto.setNameServerAddr(nameServerAddrTextField.getText());
        rocketMqProducerWidgetDto.setNameTopic(topicTextField.getText());
        rocketMqProducerWidgetDto.setTags(tagsTextField.getText());
        rocketMqProducerWidgetDto.setCount(Integer.parseInt(countTextField.getText()));
        rocketMqProducerWidgetDto.setInputText(inputTextArea.getText());
        try {
            Long newTreeId = rocketMqProducerWidgetDao.insertData(rocketMqProducerWidgetDto);
            rocketMqProducerWidgetDto.setId(newTreeId);
            return new CustomizeTreeItem<>(rocketMqProducerWidgetDto);
        } catch (Exception e) {
            logger.error("", e);
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<RocketMqProducerWidgetDto> importData(RocketMqProducerWidgetDto treeDataDto) {
        return null;
    }

    @Override
    public CustomizeTreeItem<RocketMqProducerWidgetDto> delete(RocketMqProducerWidgetDto rocketMqProducerWidgetDto) {
        try {
            rocketMqProducerWidgetDao.delLevelData(rocketMqProducerWidgetDto);
            return new CustomizeTreeItem<>(rocketMqProducerWidgetDto);
        } catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<RocketMqProducerWidgetDto> update(RocketMqProducerWidgetDto rocketMqProducerWidgetDto) {
        try {
            rocketMqProducerWidgetDto.setNameServerAddr(nameServerAddrTextField.getText());
            rocketMqProducerWidgetDto.setNameTopic(topicTextField.getText());
            rocketMqProducerWidgetDto.setTags(tagsTextField.getText());
            rocketMqProducerWidgetDto.setCount(Integer.parseInt(countTextField.getText()));
            rocketMqProducerWidgetDto.setInputText(inputTextArea.getText());
            rocketMqProducerWidgetDao.updateData(rocketMqProducerWidgetDto);
            return new CustomizeTreeItem<>(rocketMqProducerWidgetDto);
        } catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void changeToDisplay(RocketMqProducerWidgetDto rocketMqProducerWidgetDto) {
        try {
            nameServerAddrTextField.setText(rocketMqProducerWidgetDto.getNameServerAddr());
            topicTextField.setText(rocketMqProducerWidgetDto.getNameTopic());
            tagsTextField.setText(rocketMqProducerWidgetDto.getTags());
            countTextField.setText(String.valueOf(rocketMqProducerWidgetDto.getCount()));
            inputTextArea.setText(rocketMqProducerWidgetDto.getInputText());
        } catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }

    @Override
    public List<RocketMqProducerWidgetDto> queryAll() {
        try {
            return rocketMqProducerWidgetDao.queryAllData();
        } catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void updatePositionOnly(RocketMqProducerWidgetDto dto) {
        try {
            rocketMqProducerWidgetDao.updatePositionOnly(dto);
        } catch (Exception e) {
            logger.error(I18N.get("message.update_position_failed"), e);
        }
    }
}
