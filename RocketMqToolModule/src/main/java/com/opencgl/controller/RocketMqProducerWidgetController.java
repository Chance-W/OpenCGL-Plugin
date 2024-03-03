package com.opencgl.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.TreeViewUtil;
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
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class RocketMqProducerWidgetController extends RocketMqProducerWidgetView implements Initializable, TreeOperateService<RocketMqProducerWidgetDto> {

    private final Logger logger = LoggerFactory.getLogger(RocketMqProducerWidgetController.class);
    private TreeView<RocketMqProducerWidgetDto> treeView;
    SendMessageService<RmqRequest, RmqResponse> sendMessageService = RmqSendMessageFactory.sendRmqMessage();
    private final RocketMqProducerWidgetDao rocketMqProducerWidgetDao = new RocketMqProducerWidgetDao();

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        rocketMqProducerWidgetDao.checkTable();
        showTooltip();
        //设置控件
        setControlAndStyle();
        List<RocketMqProducerWidgetDto> rocketMqProducerWidgetDtos = queryAll();
        treeView = TreeViewUtil.buildTreeView(rocketMqProducerWidgetDtos, mainStackPane, this, RocketMqProducerWidgetDto.class);
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeView);
    }

    @FXML
    public void sendLabelAction() {
        if (StringUtils.isEmpty(nameServerAddrTextField.getText())
            || StringUtils.isEmpty(topicTextField.getText())
            || Integer.parseInt(countTextField.getText()) < 1
            || StringUtils.isEmpty(inputTextArea.getText())) {
            DialogUtil.showErrorInfo("参数不合法", mainStackPane);
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

        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> LoadingUtil.show(contentPanel));
            try {
                RmqResponse out = sendMessageService.send(rmqRequest);
                logger.info("response is {}", out.getResultMsg());
                outputTextArea.setText(out.getResultMsg());
            }
            catch (Throwable e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                logger.error("", e);
                outputTextArea.setText(sw.toString());
            }
            finally {
                Platform.runLater(() -> LoadingUtil.remove(contentPanel));
                OperationHisRecord.record("SEND RMQ MESSAGE:\n" + "NameServerAddr:" + nameServerAddrTextField.getText() +
                    "\n" + "Topic:" + topicTextField.getText() + "\n" + "Tags:" + tagsTextField.getText() + "\n" + "循环次数:" + countTextField.getText()
                    + "\n" + "输入:\n" + requestJson + "\n"
                    + "输出:\n" + outputTextArea.getText() + "\t");
            }
        });
    }

    @FXML
    public void refreshLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
            || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, "未选中子节点");
            return;
        }
        RocketMqProducerWidgetDto rocketMqProducerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        try {
            nameServerAddrTextField.setText(rocketMqProducerWidgetDto.getNameServerAddr());
            topicTextField.setText(rocketMqProducerWidgetDto.getNameTopic());
            tagsTextField.setText(rocketMqProducerWidgetDto.getNameTopic());
            countTextField.setText(String.valueOf(rocketMqProducerWidgetDto.getCount()));
            inputTextArea.setText(rocketMqProducerWidgetDto.getInputText());
        }
        catch (Exception e) {
            logger.error("", e);
        }
    }

    @FXML
    public void saveLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
            || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, "未选中子节点");
            return;
        }
        RocketMqProducerWidgetDto rocketMqProducerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        rocketMqProducerWidgetDto.setNameServerAddr(nameServerAddrTextField.getText());
        rocketMqProducerWidgetDto.setNameTopic(topicTextField.getText());
        rocketMqProducerWidgetDto.setTags(tagsTextField.getText());
        rocketMqProducerWidgetDto.setCount(Integer.parseInt(countTextField.getText()));
        rocketMqProducerWidgetDto.setInputText(inputTextArea.getText());
        update(rocketMqProducerWidgetDto);
    }


    @FXML
    public void copyLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
            || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()) {
            TooltipUtil.showToast(mainStackPane, "未选中子节点");
            return;
        }
        RocketMqProducerWidgetDto originRocketMqProducerWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();

        RocketMqProducerWidgetDto rocketMqProducerWidgetDto = new RocketMqProducerWidgetDto();
        rocketMqProducerWidgetDto.setName("copy-" + originRocketMqProducerWidgetDto.getName() + "-" + FormatVariableUtil.getRandom(8));
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
            TooltipUtil.showToast(contentBorderPane, "成功");
        }
        catch (Exception e) {
            logger.error("", e);
            TooltipUtil.showToast(contentBorderPane, e.getMessage());
        }
    }

    public void showTooltip() {
        nameServerAddrTextField.setOnMouseEntered(event ->
            nameServerAddrTextField.setTooltip(new Tooltip(nameServerAddrTextField.getText())));
        topicTextField.setOnMouseEntered(event ->
            topicTextField.setTooltip(new Tooltip(topicTextField.getText())));

    }

    public void setControlAndStyle() {
        sendButton.setTooltip(new Tooltip("发送请求"));
        refreshButton.setTooltip(new Tooltip("刷新"));
        copyButton.setTooltip(new Tooltip("复制"));
        saveButton.setTooltip(new Tooltip("保存"));

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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
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
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }

    @Override
    public List<RocketMqProducerWidgetDto> queryAll() {
        try {
            return rocketMqProducerWidgetDao.queryAllData();
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }
}


