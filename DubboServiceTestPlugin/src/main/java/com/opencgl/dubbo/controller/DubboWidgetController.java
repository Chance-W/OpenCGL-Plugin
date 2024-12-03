package com.opencgl.dubbo.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.controls.CustomMFXFilterComboBox;
import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.base.model.Base;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.TreeViewUtil;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.dubbo.dao.DubboWidgetDao;
import com.opencgl.dubbo.factory.DubboSendMessageFactory;
import com.opencgl.dubbo.model.DubboInfo;
import com.opencgl.dubbo.model.DubboRequest;
import com.opencgl.dubbo.model.DubboResponse;
import com.opencgl.dubbo.model.DubboTreeItem;
import com.opencgl.dubbo.utils.DubboConfigFileParseUtil;
import com.opencgl.dubbo.utils.ZkClientTestUtil;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DubboWidgetController implements Initializable, TreeOperateService<DubboTreeItem> {

    @FXML
    protected SplitPane splitPane;
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected BorderPane contentBorderPane;
    @FXML
    public StackPane contentInputAndOutputPane;
    @FXML
    protected MFXButton sendButton;
    @FXML
    protected MFXButton refreshButton;
    @FXML
    protected MFXButton saveButton;
    @FXML
    protected MFXButton settingButton;
    @FXML
    protected MFXButton copyButton;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseEnvComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseIntComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseMetComboBox;
    @FXML
    protected CustomMFXFilterComboBox<String> chooseProvideComboBox;
    @FXML
    protected MFXCheckbox extCheckBox;
    @FXML
    public VBox operateTopVbox;
    @FXML
    protected HBox extHbox;
    @FXML
    protected MFXTextField extRequestType;
    @FXML
    protected CustomTextArea outputTextArea;
    @FXML
    protected CustomTextArea inputTextArea;

    private final Logger logger = LoggerFactory.getLogger(DubboWidgetController.class);
    private final DubboWidgetDao dubboWidgetDao = new DubboWidgetDao();
    private final DubboConfigFileParseUtil dubboConfigFileOperUtil = new DubboConfigFileParseUtil();
    private final DubboEnvConfigureDialog dubboEnvConfigureController = new DubboEnvConfigureDialog();
    private final ZkClientTestUtil zkClientTest = new ZkClientTestUtil();
    private final String basePath = Base.BASE_PATH + "/conf/dubbo/";

    private final SendMessageService<DubboRequest, DubboResponse> sendMessageService = DubboSendMessageFactory.sendDubboMessage();

    private TreeView<DubboTreeItem> dubboTreeItemTreeView = null;

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
        showTooltip();
        List<DubboTreeItem> dubboTreeItems = queryAll();
        dubboTreeItemTreeView = TreeViewUtil.buildTreeView(dubboTreeItems, mainStackPane, this, DubboTreeItem.class);
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, dubboTreeItemTreeView);
    }


    public void init() {
        initStyle();
        operateTopVbox.getChildren().remove(extHbox);
        extCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                operateTopVbox.getChildren().add(extHbox);
            }
            else {
                operateTopVbox.getChildren().remove(extHbox);
            }
        });
        chooseEnvComboBox.setOnMouseClicked(event -> chooseEnvComboBox.setItems(DubboConfigFileParseUtil.getFileList(basePath)));
        //noinspection unchecked,rawtypes
        chooseEnvComboBox.getSelectionModel().selectedItemProperty().addListener((ChangeListener) (observable, oldValue, newValue) -> {
            if (null == newValue) {
                return;
            }
            chooseIntComboBox.getItems().clear();
            chooseMetComboBox.getItems().clear();
            String configPathName = basePath + chooseEnvComboBox.getText();
            try {
                List<String> allContent = DubboConfigFileParseUtil.readMethodAndType(configPathName);
                List<String> infs = new ArrayList<>();
                for (String line : allContent) {
                    infs.add(line.split(",")[0]);
                }
                chooseIntComboBox.setItems(FXCollections.observableArrayList(infs.stream().distinct().collect(Collectors.toList())));
                //  new AutoCompleteComboBoxListenerUtil(chooseIntComboBox);
            }
            catch (Exception ex) {
                logger.error("", ex);
            }
        });

        //noinspection unchecked,rawtypes
        chooseIntComboBox.getSelectionModel().selectedItemProperty().addListener((ChangeListener) (observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            chooseMetComboBox.getItems().clear();
            String configPathName = basePath + chooseEnvComboBox.getText();
            try {
                List<String> allContent = DubboConfigFileParseUtil.readMethodAndType(configPathName);
                List<String> methods = new ArrayList<>();
                for (String line : allContent) {
                    if (line.split(",")[0].equals(chooseIntComboBox.getText())) {
                        methods.add(line.split(",")[1]);
                    }
                }
                chooseMetComboBox.setItems(FXCollections.observableArrayList(methods.stream().distinct().collect(Collectors.toList())));
            }
            catch (Exception ex) {
                logger.error("", ex);
            }
        });


        chooseMetComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (null == newValue) {
                return;
            }
            chooseProvideComboBox.getItems().clear();
            String configPathName = basePath + chooseEnvComboBox.getText();
            logger.info("方法事件监控,方法选择已选中" + chooseMetComboBox.getText());
            //获取物理jar包
            String[] jarFile = (Objects.requireNonNull(DubboConfigFileParseUtil.readClientParameter(basePath + chooseEnvComboBox.getText(), "projectDubboAddress"))).split(",");
            try {
                String requestType = "";
                List<String> allContent = DubboConfigFileParseUtil.readMethodAndType(configPathName);
                for (String line : allContent) {
                    if (line.split(",")[0].equals(chooseIntComboBox.getText()) && line.split(",")[1].equals(chooseMetComboBox.getText())) {
                        requestType = line.split(",")[2];
                    }
                }
                if (StringUtils.isEmpty(requestType)) {
                    return;
                }
                if (requestType.startsWith("java")) {
                    inputTextArea.setText(requestType);
                    return;
                }
                inputTextArea.setText(DubboConfigFileParseUtil.jsonMessage(jarFile, requestType));
            }
            catch (Exception e) {
                logger.error("", e);
            }
        });

        chooseProvideComboBox.setOnMouseClicked(event -> {
            chooseProvideComboBox.getItems().clear();
            if (StringUtils.isEmpty(chooseIntComboBox.getText())) {
                return;
            }
            String configPathName = basePath + chooseEnvComboBox.getText();
            String zkAddress = DubboConfigFileParseUtil.readClientParameter(configPathName, "registryAddress");
            String dubboGroup = DubboConfigFileParseUtil.readClientParameter(configPathName, "registryGroup");
            CompletableFuture.runAsync(() -> {
                try {
                    Platform.runLater(() -> LoadingUtil.show(contentInputAndOutputPane));
                    List<String> providers = DubboConfigFileParseUtil.getProviders("/" + dubboGroup + "/" + chooseIntComboBox.getText(), zkAddress);
                    Platform.runLater(() -> chooseProvideComboBox.setItems(FXCollections.observableArrayList(providers)));
                    chooseProvideComboBox.getItems().sorted();
                }
                catch (Throwable e) {
                    logger.error("", e);
                    Platform.runLater(() -> DialogUtil.showErrorInfo(e.getMessage()));
                }
                finally {
                    Platform.runLater(() -> LoadingUtil.remove(contentInputAndOutputPane));
                }
            });
        });
    }

    private void initStyle() {
        MFXFontIcon sendButtonIcon = new MFXFontIcon("fas-play");
        sendButton.setGraphic(sendButtonIcon);
        sendButton.setText(null);
    }


    @FXML
    public void sendLabelAction() {
        if (StringUtils.isEmpty(chooseIntComboBox.getText())
            || StringUtils.isEmpty(chooseMetComboBox.getText())) {
            DialogUtil.showErrorInfo("环境/接口/方法等关键信息不能为空!");
            return;
        }
        String configPathName = basePath + chooseEnvComboBox.getText();
        DubboInfo dubboInfo = DubboConfigFileParseUtil.readClientParameter(configPathName);
        String requestJson = FormatVariableUtil.format(inputTextArea.getNonAnnotationText());
        if (dubboInfo == null
            || StringUtils.isEmpty(dubboInfo.getZk())
            || StringUtils.isEmpty(dubboInfo.getGroup())) {
            DialogUtil.showErrorInfo("环境配置文件不存在，或信息为空!");
            return;
        }
        String requestType = "";
        if (extCheckBox.isSelected() && !StringUtils.isEmpty(extRequestType.getText())) {
            requestType = extRequestType.getText();
        }
        else {
            List<String> allContent;
            try {
                allContent = DubboConfigFileParseUtil.readMethodAndType(configPathName);
                for (String line : allContent) {
                    if (line.split(",")[0].equals(chooseIntComboBox.getText()) && line.split(",")[1].equals(chooseMetComboBox.getText())) {
                        requestType = line.split(",")[2];
                    }
                }
            }
            catch (Exception e) {
                logger.error("", e);
            }
        }
        DubboRequest dubboRequest = DubboRequest.builder()
            .dubboRegistryAddr(dubboInfo.getZk())
            .dubboRegistryGroup(dubboInfo.getGroup())
            .dubboProvidersUrl(chooseProvideComboBox.getText())
            .interfaceName(chooseIntComboBox.getText())
            .method(chooseMetComboBox.getText())
            .reqType(requestType)
            .reqJsonMessage(requestJson)
            .build();

        AtomicReference<String> outResult = new AtomicReference<>("");
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> LoadingUtil.show(contentInputAndOutputPane));
            try {
                DubboResponse out = sendMessageService.send(dubboRequest);
                logger.info("response is {}", out.getObject().toString());
                outResult.set(JSON.toJSONString(out.getObject(), SerializerFeature.PrettyFormat, SerializerFeature.WriteDateUseDateFormat));
            }
            catch (Throwable e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                logger.error("", e);
                outResult.set(sw.toString());
            }
            finally {
                outputTextArea.setText(outResult.get());
                Platform.runLater(() -> LoadingUtil.remove(contentInputAndOutputPane));
                OperationHisRecord.record("SEND DUBBO MESSAGE:\n" + "环境名称:"
                    + chooseEnvComboBox.getText() + "\n"
                    + "zk地址:" + dubboRequest.getDubboRegistryAddr() + "\n"
                    + "dubbo分组:" + dubboRequest.getDubboRegistryGroup() + "\n"
                    + "接口:" + chooseIntComboBox.getText()
                    + "\n" + "方法:" + chooseMetComboBox.getText() + "\n"
                    + "提供者:" + chooseProvideComboBox.getText() + "\n"
                    + "输入:\n" + requestJson + "\n"
                    + "输出:\n" + outResult.get() + "\t");
            }
        });
    }

    @FXML
    public void refreshLabelAction() {
        DubboTreeItem dubboTreeItem = dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getValue();
        try {
            dubboTreeItem = dubboWidgetDao.queryData(dubboTreeItem);
            chooseEnvComboBox.setText(dubboTreeItem.getEnvName());
            chooseIntComboBox.setText(dubboTreeItem.getInterfaceInfo());
            chooseMetComboBox.setText(dubboTreeItem.getMethodInfo());
            if (dubboTreeItem.getIsSelected()) {
                if (!extCheckBox.isSelected()) {
                    extCheckBox.setSelected(true);
                }
                extRequestType.setText(dubboTreeItem.getRequestType());
            }
            else {
                if (extCheckBox.isSelected()) {
                    extCheckBox.setSelected(false);
                }
                extRequestType.clear();
            }
            inputTextArea.setText(dubboTreeItem.getInputText());
        }
        catch (Exception e) {
            logger.error("", e);
        }
    }

    @FXML
    public void saveLabelAction() {
        DubboTreeItem dubboTreeItem = dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getValue();
        dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
        dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
        dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());
        dubboTreeItem.setIsSelected(extCheckBox.isSelected());
        dubboTreeItem.setRequestType(extRequestType.getText());
        dubboTreeItem.setInputText(inputTextArea.getText());
        try {
            dubboWidgetDao.updateData(dubboTreeItem);
            TooltipUtil.showToast(splitPane, "修改成功");
            dubboTreeItemTreeView.refresh();
        }
        catch (Exception e) {
            TooltipUtil.showToast(splitPane, e.getMessage());
            logger.error("", e);
        }
    }

    @FXML
    public void settingLabelAction() throws IOException {
        dubboEnvConfigureController.showAndWait();
    }

    @FXML
    public void copyLabelAction() {
        if (dubboTreeItemTreeView.getSelectionModel().getSelectedItem() == null
            || !dubboTreeItemTreeView.getSelectionModel().getSelectedItem().isLeaf()) {
            TooltipUtil.showToast(mainStackPane, "子节点未选中");
            return;
        }
        DubboTreeItem originDubboTreeItem = dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getValue();

        DubboTreeItem dubboTreeItem = new DubboTreeItem();
        dubboTreeItem.setId(originDubboTreeItem.getId());
        dubboTreeItem.setParentId(dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getParent().getValue().getId());
        dubboTreeItem.setIsLeaf(originDubboTreeItem.getIsLeaf());
        dubboTreeItem.setName("copy-" + originDubboTreeItem.getName() + "-" + FormatVariableUtil.getRandom(8));
        dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
        dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
        dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());
        dubboTreeItem.setIsSelected(extCheckBox.isSelected());
        dubboTreeItem.setRequestType(extRequestType.getText());
        dubboTreeItem.setInputText(inputTextArea.getText());
        try {
            Long newTreeItemId = dubboWidgetDao.insertData(dubboTreeItem);
            dubboTreeItem.setId(newTreeItemId);
            TreeItem<DubboTreeItem> treeItem = new TreeItem<>(dubboTreeItem);
            dubboTreeItemTreeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(treeItem);
            dubboTreeItemTreeView.getSelectionModel().select(treeItem);
            TooltipUtil.showToast(splitPane, "成功");
        }
        catch (Exception e) {
            TooltipUtil.showToast(splitPane, e.getMessage());
            logger.error("", e);
        }
    }


    public void showTooltip() {
        chooseIntComboBox.setOnMouseEntered(event -> chooseIntComboBox.setTooltip(new Tooltip(chooseIntComboBox.getText())));
        chooseMetComboBox.setOnMouseEntered(event -> chooseMetComboBox.setTooltip(new Tooltip(chooseMetComboBox.getText())));
        chooseProvideComboBox.setOnMouseEntered(event -> chooseProvideComboBox.setTooltip(new Tooltip(chooseProvideComboBox.getText())));
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> add(DubboTreeItem dubboTreeItem) {
        dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
        dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
        dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());
        dubboTreeItem.setIsSelected(extCheckBox.isSelected());
        if (!extCheckBox.isSelected()) {
            extRequestType.clear();
        }
        dubboTreeItem.setRequestType(extRequestType.getText());
        dubboTreeItem.setInputText(inputTextArea.getText());
        return insertAndGetTreeItem(dubboTreeItem);
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> importData(DubboTreeItem dubboTreeItem) {
        logger.info("begin import data {}", dubboTreeItem);
        return insertAndGetTreeItem(dubboTreeItem);
    }

    private CustomizeTreeItem<DubboTreeItem> insertAndGetTreeItem(DubboTreeItem dubboTreeItem) {
        try {
            Long newId = dubboWidgetDao.insertData(dubboTreeItem);
            dubboTreeItem.setId(newId);
            return new CustomizeTreeItem<>(dubboTreeItem);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> delete(DubboTreeItem dubboTreeItem) {
        try {
            dubboWidgetDao.delLevelData(dubboTreeItem);
            return new CustomizeTreeItem<>(dubboTreeItem);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<DubboTreeItem> update(DubboTreeItem dubboTreeItem) {
        try {
            dubboTreeItem.setName(dubboTreeItem.getName());
            dubboTreeItem.setEnvName(chooseEnvComboBox.getText());
            dubboTreeItem.setInterfaceInfo(chooseIntComboBox.getText());
            dubboTreeItem.setMethodInfo(chooseMetComboBox.getText());
            dubboTreeItem.setIsSelected(extCheckBox.isSelected());
            dubboTreeItem.setRequestType(extRequestType.getText());
            dubboTreeItem.setInputText(inputTextArea.getText());
            dubboWidgetDao.updateData(dubboTreeItem);
            return new CustomizeTreeItem<>(dubboTreeItem);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void changeToDisplay(DubboTreeItem dubboTreeItem) {
        try {
            chooseEnvComboBox.getItems().forEach(s -> {
                if (s.equals(dubboTreeItem.getEnvName())) {
                    chooseEnvComboBox.selectItem(s);
                }
            });
            chooseEnvComboBox.setText(dubboTreeItem.getEnvName());
            chooseIntComboBox.setText(dubboTreeItem.getInterfaceInfo());
            chooseMetComboBox.setText(dubboTreeItem.getMethodInfo());
            chooseProvideComboBox.clear();
            if (dubboTreeItem.getIsSelected()) {
                extCheckBox.setSelected(true);
                extRequestType.setText(dubboTreeItem.getRequestType());
                if (operateTopVbox.getChildren().size() == 1) {
                    operateTopVbox.getChildren().add(extHbox);
                }
            }
            else {
                extCheckBox.setSelected(false);
                if (operateTopVbox.getChildren().size() == 2) {
                    operateTopVbox.getChildren().remove(extHbox);
                }
            }
            inputTextArea.setText(dubboTreeItem.getInputText());
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }

    @Override
    public List<DubboTreeItem> queryAll() {
        try {
            return dubboWidgetDao.queryAllData();
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }
}


