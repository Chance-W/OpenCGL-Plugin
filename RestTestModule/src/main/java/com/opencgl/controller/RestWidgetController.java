package com.opencgl.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
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
import com.opencgl.dao.RestWidgetDao;
import com.opencgl.model.DataAttribute;
import com.opencgl.model.RestRequest;
import com.opencgl.model.RestWidgetDto;
import com.opencgl.views.RestWidgetView;
import com.opencgl.factory.RestSendMessageFactory;
import com.opencgl.model.RestResponse;
import com.opencgl.util.Dom4jUtil;
import com.opencgl.util.EditableTextFieldTreeTableCell;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
public class RestWidgetController extends RestWidgetView implements Initializable, TreeOperateService<RestWidgetDto> {

    private final Logger logger = LoggerFactory.getLogger(RestWidgetController.class);
    private TreeView<RestWidgetDto> treeView;
    private final SendMessageService<RestRequest, RestResponse> sendMessageService = RestSendMessageFactory.sendRestMessage();
    private final RestWidgetDao restWidgetDao = new RestWidgetDao();

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
        initTableView();
        //设置控件
        setControlAndStyle();
        List<RestWidgetDto> restWidgetDtos = queryAll();
        treeView = TreeViewUtil.buildTreeView(restWidgetDtos, mainStackPane, this, RestWidgetDto.class);
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeView);
    }

    public void init() throws Exception {
        restWidgetDao.checkTable();
        //设置悬停展示url
        urlTextField.setOnMouseEntered(event -> urlTextField.setTooltip(new Tooltip(urlTextField.getText())));
        //设置选择默认停留在body
        restJfxTabPane.getSelectionModel().select(2);
        //设置 mediaType 默认选中第一个
        chooseMediaTypeComboBox.selectFirst();
    }

    private void initTableView() {
        generateTreeTableView(headers, headersAddButton, headersDelButton, headersKey, headersValue, headersDescription);
        generateTreeTableView(cookies, cookiesAddButton, cookiesDelButton, cookiesKey, cookiesValue, cookiesDescription);
    }

    private void generateTreeTableView(TreeTableView<DataAttribute> treeTableView,
                                       MFXButton addButton,
                                       MFXButton delButton,
                                       TreeTableColumn<DataAttribute, String> key,
                                       TreeTableColumn<DataAttribute, String> value,
                                       TreeTableColumn<DataAttribute, String> description) {
        // 创建根节点
        TreeItem<DataAttribute> treeItem = new TreeItem<>(new DataAttribute("-", "-", "-"));
        treeTableView.setRoot(treeItem);
        treeTableView.setShowRoot(false);

        addButton.setOnAction(event -> {
            ObservableList<TreeItem<DataAttribute>> children = treeItem.getChildren();
            List<TreeItem<DataAttribute>> notFixedDataAttrs = children.stream().filter(item -> {
                DataAttribute dataAttribute = item.getValue();
                return StringUtils.equals(dataAttribute.getName(), "-")
                    || StringUtils.isEmpty(dataAttribute.getName())
                    || StringUtils.equals(dataAttribute.getValue(), "-")
                    || StringUtils.isEmpty(dataAttribute.getValue());
            }).toList();

            if (CollectionUtils.isNotEmpty(notFixedDataAttrs)) {
                TooltipUtil.showToast(contentInputAndOutputPane, "请配置合法参数后再继续添加");
            }
            else {
                treeItem.getChildren().add(new TreeItem<>(new DataAttribute("-", "-", "-")));
                treeTableView.getSelectionModel().selectLast();
            }
        });

        delButton.setOnAction(event -> {
            if (CollectionUtils.isNotEmpty(treeItem.getChildren())
                && null != treeTableView.getSelectionModel().getSelectedItem()) {
                treeItem.getChildren().remove(treeTableView.getSelectionModel().getSelectedIndex());
                treeTableView.getSelectionModel().select(treeTableView.getSelectionModel().getSelectedIndex());
            }
            else {
                TooltipUtil.showToast("未选中待删除节点");
            }
        });

        key.setCellValueFactory(cellData -> cellData.getValue().getValue().nameProperty());
        key.setCellFactory(column -> new EditableTextFieldTreeTableCell<>());
        key.setOnEditCommit((TreeTableColumn.CellEditEvent<DataAttribute, String> t) -> t.getTreeTableView()
            .getTreeItem(t.getTreeTablePosition()
                .getRow())
            .getValue().name.set(t.getNewValue()));

        value.setCellValueFactory(cellData -> cellData.getValue().getValue().valueProperty());
        value.setCellFactory(column -> new EditableTextFieldTreeTableCell<>());
        value.setOnEditCommit((TreeTableColumn.CellEditEvent<DataAttribute, String> t) -> t.getTreeTableView()
            .getTreeItem(t.getTreeTablePosition()
                .getRow())
            .getValue().value.set(t.getNewValue()));


        description.setCellValueFactory(cellData -> cellData.getValue().getValue().desProperty());
        description.setCellFactory(column -> new EditableTextFieldTreeTableCell<>());
        description.setOnEditCommit((TreeTableColumn.CellEditEvent<DataAttribute, String> t) -> t.getTreeTableView()
            .getTreeItem(t.getTreeTablePosition()
                .getRow())
            .getValue().des.set(t.getNewValue()));
    }


    @FXML
    public void sendLabelAction() {
        if (StringUtils.isEmpty(urlTextField.getText())
            || StringUtils.isEmpty(chooseMetComboBox.getText())) {
            DialogUtil.showErrorInfo("请求方法和请求地址不能为空!", mainStackPane);
            return;
        }

        //设置选择停留在body
        restJfxTabPane.getSelectionModel().select(2);

        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> LoadingUtil.show(contentInputAndOutputPane));
            String requestText = FormatVariableUtil.format(inputTextArea.getText());
            logger.info("rest request message is {}", requestText);
            List<DataAttribute> headersDataAttributes = traverseTreeItems(headers.getRoot());
            Map<String, String> requestHeader = new HashMap<>(32);
            headersDataAttributes.forEach(dataAttribute -> {
                if (StringUtils.isNotEmpty(dataAttribute.getName())
                    && !StringUtils.equals(dataAttribute.getName(), "-")) {
                    requestHeader.put(dataAttribute.getName(), dataAttribute.getValue());
                }
            });

            Map<String, String> requestCookie = new HashMap<>(32);
            List<DataAttribute> cookiesDataAttributes = traverseTreeItems(cookies.getRoot());
            cookiesDataAttributes.forEach(dataAttribute -> {
                if (StringUtils.isNotEmpty(dataAttribute.getName())
                    && !StringUtils.equals(dataAttribute.getName(), "-")) {
                    requestCookie.put(dataAttribute.getName(), dataAttribute.getValue());
                }
            });

            try {
                RestRequest restRequest = RestRequest.builder()
                    .requestMethod(chooseMetComboBox.getText())
                    .requestUrl(urlTextField.getText())
                    .requestHeaderMap(requestHeader)
                    .requestCookieMap(requestCookie)
                    .mediaType(chooseMediaTypeComboBox.getText())
                    .requestMessage(requestText)
                    .build();
                RestResponse response = sendMessageService.send(restRequest);
                if (JSONObject.isValid(response.getResultMsg())) {
                    outputTextArea.setText(JSON.toJSONString(JSONObject.parseObject(response.getResultMsg()), SerializerFeature.PrettyFormat, SerializerFeature.WriteDateUseDateFormat));
                }
                else if (Dom4jUtil.isValidXml(response.getResultMsg())) {
                    outputTextArea.setText(Dom4jUtil.formatXml(response.getResultMsg()));
                }
                else {
                    outputTextArea.setText(response.getResultMsg());
                }
            }
            catch (Throwable e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                logger.error("", e);
                outputTextArea.setText(sw.toString());
            }
            finally {
                Platform.runLater(() -> LoadingUtil.remove(contentInputAndOutputPane));
                OperationHisRecord.record("SEND REST MESSAGE:\n" + "请求方法:" + chooseMetComboBox.getText() +
                    "\n" + "请求地址:" + urlTextField.getText() + "\n" + "请求header:" + JSONObject.toJSONString(requestHeader) + "\n" + "请求cookie:" + JSONObject.toJSONString(requestCookie)
                    + "\n" + "输入:\n" + requestText + "\n"
                    + "输出:\n" + outputTextArea.getText() + "\t");
            }
        });
    }

    @FXML
    public void refreshLabelAction() {
        /* eventBus.post(DialogStyleDto.builder().type(OperateTypeEnum.QUERY).build());*/
    }

    @FXML
    public void saveLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null) {
            TooltipUtil.showToast(contentInputAndOutputPane, "子节点未选中");
            return;
        }
        RestWidgetDto restWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        restWidgetDto.setRequestMethod(chooseMetComboBox.getText());
        restWidgetDto.setRequestUrl(urlTextField.getText());
        buildHeaderAndCookieDataAttribute(restWidgetDto);
        restWidgetDto.setRequestMediaType(chooseMediaTypeComboBox.getText());
        restWidgetDto.setInputText(inputTextArea.getText());
        try {
            restWidgetDao.updateData(restWidgetDto);
            TooltipUtil.showToast(contentInputAndOutputPane, "保存成功");
        }
        catch (Exception e) {
            logger.error("", e);
            TooltipUtil.showToast(contentInputAndOutputPane, e.getMessage());
        }
    }

    @FXML
    public void copyLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null
            || !treeView.getSelectionModel().getSelectedItem().isLeaf()) {
            TooltipUtil.showToast(mainStackPane, "子节点未选中");
            return;
        }
        RestWidgetDto originRestWidgetDto = treeView.getSelectionModel().getSelectedItem().getValue();
        RestWidgetDto newRestWidgetDto = new RestWidgetDto();
        newRestWidgetDto.setName("copy-"
            + originRestWidgetDto.getName()
            + "-"
            + FormatVariableUtil.getRandom(8));
        newRestWidgetDto.setParentId(newRestWidgetDto.getParentId());
        newRestWidgetDto.setIsLeaf(true);
        newRestWidgetDto.setRequestHeader(originRestWidgetDto.getRequestHeader());
        newRestWidgetDto.setRequestCookie(originRestWidgetDto.getRequestCookie());
        newRestWidgetDto.setRequestMethod(originRestWidgetDto.getRequestMethod());
        newRestWidgetDto.setRequestUrl(originRestWidgetDto.getRequestUrl());
        newRestWidgetDto.setRequestMediaType(originRestWidgetDto.getRequestMediaType());
        newRestWidgetDto.setInputText(originRestWidgetDto.getInputText());
        try {
            CustomizeTreeItem<RestWidgetDto> restWidgetDtoCustomizeTreeItem = insertAndGetTreeItem(newRestWidgetDto);
            treeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(restWidgetDtoCustomizeTreeItem);
            treeView.getSelectionModel().select(restWidgetDtoCustomizeTreeItem);
            TooltipUtil.showToast(contentInputAndOutputPane, "成功");
        }
        catch (Exception e) {
            logger.error("", e);
            TooltipUtil.showToast(contentInputAndOutputPane, e.getMessage());
        }
    }

    public void setControlAndStyle() {
        sendButton.setTooltip(new Tooltip("发送请求"));
        refreshButton.setTooltip(new Tooltip("刷新"));
        copyButton.setTooltip(new Tooltip("复制"));
        saveButton.setTooltip(new Tooltip("保存"));
        chooseMetComboBox.setOnMouseEntered(event -> chooseMetComboBox.setTooltip(new Tooltip(chooseMetComboBox.getText())));
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }

    @Override
    public CustomizeTreeItem<RestWidgetDto> add(RestWidgetDto restWidgetDto) {
        buildRestInfoExpectTree(restWidgetDto);
        return insertAndGetTreeItem(restWidgetDto);
    }

    @Override
    public CustomizeTreeItem<RestWidgetDto> importData(RestWidgetDto restWidgetDto) {
        logger.info("begin import data {}", restWidgetDto);
        return insertAndGetTreeItem(restWidgetDto);
    }

    @Override
    public CustomizeTreeItem<RestWidgetDto> delete(RestWidgetDto restWidgetDto) {
        try {
            restWidgetDao.delLevelData(restWidgetDto);
            return new CustomizeTreeItem<>(restWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<RestWidgetDto> update(RestWidgetDto restWidgetDto) {
        try {
            buildRestInfoExpectTree(restWidgetDto);
            restWidgetDao.updateData(restWidgetDto);
            return new CustomizeTreeItem<>(restWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void changeToDisplay(RestWidgetDto restWidgetDto) {
        try {
            urlTextField.setText(restWidgetDto.getRequestUrl());
            chooseMediaTypeComboBox.setText(restWidgetDto.getRequestMediaType());
            chooseMetComboBox.setText(restWidgetDto.getRequestMethod());
            if (StringUtils.isNotEmpty(restWidgetDto.getRequestHeader())) {
                List<Map<String, String[]>> headerDataAttributes = JSON.parseObject(restWidgetDto.getRequestHeader(), new TypeReference<>() {
                });
                generateDisplayTableTreeData(headerDataAttributes, headers);
            }
            if (StringUtils.isNotEmpty(restWidgetDto.getRequestCookie())) {
                List<Map<String, String[]>> cookiesDataAttributes = JSON.parseObject(restWidgetDto.getRequestCookie(), new TypeReference<>() {
                });
                generateDisplayTableTreeData(cookiesDataAttributes, cookies);
            }
            inputTextArea.setText(restWidgetDto.getInputText());
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }

    private void generateDisplayTableTreeData(List<Map<String, String[]>> headerDataAttributes, TreeTableView<DataAttribute> headers) {
        TreeItem<DataAttribute> root = headers.getRoot();
        root.getChildren().clear();
        headerDataAttributes.forEach(item -> item.forEach((s, strings) -> {
            DataAttribute dataAttribute = new DataAttribute(s, strings[0], strings[1]);
            root.getChildren().add(new TreeItem<>(dataAttribute));
        }));
    }

    @Override
    public List<RestWidgetDto> queryAll() {
        try {
            return restWidgetDao.queryAllData();
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    private List<DataAttribute> traverseTreeItems(TreeItem<DataAttribute> root) {
        List<DataAttribute> dataAttributes = new ArrayList<>();
        ObservableList<TreeItem<DataAttribute>> children = root.getChildren();
        for (TreeItem<DataAttribute> child : children) {
            dataAttributes.add(child.getValue()); // 添加节点值到列表
        }
        return dataAttributes;
    }

    private CustomizeTreeItem<RestWidgetDto> insertAndGetTreeItem(RestWidgetDto restWidgetDto) {
        try {
            Long newId = restWidgetDao.insertData(restWidgetDto);
            restWidgetDto.setId(newId);
            return new CustomizeTreeItem<>(restWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    private void buildRestInfoExpectTree(RestWidgetDto restWidgetDto) {
        restWidgetDto.setRequestUrl(urlTextField.getText());
        restWidgetDto.setRequestMethod(chooseMetComboBox.getText());
        buildHeaderAndCookieDataAttribute(restWidgetDto);
        restWidgetDto.setRequestMediaType(chooseMediaTypeComboBox.getText());
        restWidgetDto.setInputText(inputTextArea.getText());
    }

    private void buildHeaderAndCookieDataAttribute(RestWidgetDto restWidgetDto) {
        List<Map<String, String[]>> headersDataAttribute = new ArrayList<>();
        headers.getRoot().getChildren().forEach(item -> {
            DataAttribute dataAttribute = item.getValue();
            Map<String, String[]> dataAttributeMap = new HashMap<>();
            String[] valueAndDes = new String[]{dataAttribute.getValue(), dataAttribute.getDes()};
            dataAttributeMap.put(dataAttribute.getName(), valueAndDes);
            headersDataAttribute.add(dataAttributeMap);
        });
        restWidgetDto.setRequestHeader(JSONObject.toJSONString(headersDataAttribute));

        List<Map<String, String[]>> cookiesDataAttribute = new ArrayList<>();
        cookies.getRoot().getChildren().forEach(item -> {
            DataAttribute dataAttribute = item.getValue();
            Map<String, String[]> dataAttributeMap = new HashMap<>();
            String[] valueAndDes = new String[]{dataAttribute.getValue(), dataAttribute.getDes()};
            dataAttributeMap.put(dataAttribute.getName(), valueAndDes);
            cookiesDataAttribute.add(dataAttributeMap);
        });
        restWidgetDto.setRequestCookie(JSONObject.toJSONString(cookiesDataAttribute));
    }
}


