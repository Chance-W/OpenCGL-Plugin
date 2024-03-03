package com.xtool.opencgl.controller;


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.dom4j.DocumentException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.jfoenix.svg.SVGGlyph;
import com.jfoenix.svg.SVGGlyphLoader;
import com.opencgl.base.ViewControllerUtil.ThemeSwitchUtil;
import com.opencgl.base.model.Base;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.LoadingUtil;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.utils.TooltipUtil;
import com.xtool.opencgl.factory.RestSendMessageFactory;
import com.xtool.opencgl.model.DialogStyleDto;
import com.xtool.opencgl.model.OperateTypeEnum;
import com.xtool.opencgl.model.RestRequest;
import com.xtool.opencgl.model.RestResponse;
import com.xtool.opencgl.model.SoapWidgetDto;
import com.xtool.opencgl.utils.FourTreeViewUtil;
import com.xtool.opencgl.utils.NodeListValidate;
import com.xtool.opencgl.utils.wsdl2soap.BuildSoap;
import com.xtool.opencgl.utils.wsdl2soap.Dom4jUtil;
import com.xtool.opencgl.views.SoapWidgetView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
public class SoapWidgetController extends SoapWidgetView implements Initializable {
    private final Logger logger = LoggerFactory.getLogger(SoapWidgetController.class);
    private TreeView<String> treeView;
    private static final String FILE_NAME = "icomoon.svg";
    private static final String BASE_PATH = Base.BASE_PATH + "conf" + File.separator + "webservice" + File.separator;
    private final SoapWidgetDto soapWidgetDto = new SoapWidgetDto();
    private final SendMessageService<RestRequest, RestResponse> sendMessageService = RestSendMessageFactory.sendRestMessage();
    private final EventBus eventBus = new EventBus();
    List<SoapWidgetDto> soapWidgetDtos = new ArrayList<>();

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> LoadingUtil.show(mainStackPane));
            try {
                init();
                showTooltip();
                eventBus.register(this);
                //设置控件
                setControlAndStyle();
                initTree();
            }
            catch (Exception e) {

                logger.error("", e);
            }
            finally {
                Platform.runLater(() -> LoadingUtil.remove(mainStackPane));
            }
        });
    }

    private void initTree() {
        File[] fs = new File(BASE_PATH).listFiles();
        if (fs != null && fs.length != 0) {
            try {
                soapWidgetDtos = Dom4jUtil.parseUtil(Objects.requireNonNull(fs));
                soapWidgetDtos.sort(Comparator.comparing(SoapWidgetDto::getThirdLevel));
                treeView = new FourTreeViewUtil().buildTreeView(soapWidgetDtos, mainStackPane, eventBus);
                Platform.runLater(() -> ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeView));
            }
            catch (Exception e) {
                logger.error("", e);
            }
        }
    }

    public void init() {
     Platform.runLater(() -> {
         Font.loadFont(SoapWidgetController.class.getResourceAsStream("/css/font-awesome-4.7.0/fonts/fontawesome-webfont.ttf"), -1);
         try {
             SVGGlyphLoader.loadGlyphsFont(SoapWidgetController.class.getResourceAsStream("/fonts/icomoon.svg"), "icomoon.svg");
         }
         catch (IOException e) {
             throw new RuntimeException(e);
         }
     });
    }

    @FXML
    public void sendLabelAction() {
        if (StringUtils.isEmpty((String) chooseIntComboBox.getValue())) {
            DialogController.errorInfo(mainStackPane, "请求地址不能为空!");
            return;
        }
        CompletableFuture.runAsync(() -> {
            String reqMsg = null;
            Platform.runLater(() -> LoadingUtil.show(mainStackPane));
            try {
                Dom4jUtil.modSoapDtoXmlValue(soapWidgetDto, inputTextArea.getText());
                reqMsg = FormatVariableUtil.format(inputTextArea.getText());
                outputTextArea.setText(sendMessage(RestRequest.builder().requestUrl((String) chooseIntComboBox.getValue()).requestMessage(reqMsg).requestMethod("POST").build()).getResultMsg());
            }
            catch (Exception e) {
                logger.error("", e);
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                outputTextArea.setText(sw.toString());
            }
            finally {
                Platform.runLater(() -> LoadingUtil.remove(mainStackPane));
                try {
                    OperationHisRecord.record("Send WebService Message:\n" + "请求URL:" + chooseIntComboBox.getValue().toString() +
                        "\n" + "请求详情:\n" + soapWidgetDto.getFirstLevel() + " > " + soapWidgetDto.getSecondLevel() + " > " + soapWidgetDto.getThirdLevel() + " > " + soapWidgetDto.getFourthLevel() + "\n请求消息:\n"
                        + reqMsg + "\n返回详情:\n" +
                        outputTextArea.getText() + "\t");
                }
                catch (Exception e) {
                    logger.error("写入历史记录失败,", e);
                }
            }
        });
    }


    public RestResponse sendMessage(RestRequest restRequest) throws Exception {
        if (restRequest.getRequestUrl().contains("Soap12")) {
            restRequest.setMediaType("application/soap+xml;charset=utf-8");
        }
        else {
            restRequest.setMediaType("text/xml;charset=UTF-8");
        }
        restRequest.setRequestMessage(restRequest.getRequestMessage());
        RestResponse res = sendMessageService.send(restRequest);
        if (res.getResultCode() == 200 && res.getResultMsg().startsWith("<?xml ")) {
            res.setResultMsg(Dom4jUtil.formatXml(res.getResultMsg()));
        }
        else if (res.getResultCode() == 200) {
            JSONObject object = JSONObject.parseObject(Objects.requireNonNull(res.getResultMsg()));
            res.setResultMsg(JSON.toJSONString(object, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteDateUseDateFormat));
        }
        else {
            res.setResultMsg(res.getResultMsg());
        }
        logger.info("end response and response is {}", res);
        return res;
    }


    @FXML
    public void refershLabelAction() {
        if (!treeView.getSelectionModel().getSelectedItem().isLeaf()) {
            return;
        }
        soapWidgetDto.setFourthLevel(treeView.getSelectionModel().getSelectedItem().getValue());
        soapWidgetDto.setThirdLevel(treeView.getSelectionModel().getSelectedItem().getParent().getValue());
        soapWidgetDto.setSecondLevel(treeView.getSelectionModel().getSelectedItem().getParent().getParent().getValue());
        soapWidgetDto.setFirstLevel(treeView.getSelectionModel().getSelectedItem().getParent().getParent().getParent().getValue());
        try {
            soapWidgetDto.setSoapRequestMessage(Dom4jUtil.getSoapRequestMessage(soapWidgetDto).getSoapRequestMessage());
            chooseIntComboBox.setItems(FXCollections.observableArrayList(Dom4jUtil.getRequestUrl(soapWidgetDto).split(",")));
            if (soapWidgetDto.getSecondLevel().contains("12")) {
                chooseIntComboBox.getSelectionModel().select(0);
            }
            else {
                chooseIntComboBox.getSelectionModel().select(1);
            }
            inputTextArea.setText(Dom4jUtil.formatXml(soapWidgetDto.getSoapRequestMessage()));
        }
        catch (Exception e) {

            logger.error("", e);
        }
    }

    @FXML
    public void saveLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null) {
            TooltipUtil.showToast(mainStackPane, "节点未选中");
            return;
        }
        try {
            Dom4jUtil.modSoapDtoXmlValue(soapWidgetDto, inputTextArea.getText());
            TooltipUtil.showToast(mainStackPane, "成功");
        }
        catch (Exception e) {

            logger.error("", e);
        }
    }

    @FXML
    public void settingLabelAction() {
        DialogController.webServiceConfigure(eventBus, new DialogStyleDto(), mainStackPane);
    }

    public void showTooltip() {
        chooseIntComboBox.setOnMouseEntered(event -> chooseIntComboBox.setTooltip(new Tooltip((String) chooseIntComboBox.getValue())));
    }

    public void setControlAndStyle() throws Exception {
        Platform.runLater(() -> {
            try {
                /*style and control*/
                SVGGlyph sendLabelGlyph = SVGGlyphLoader.getIcoMoonGlyph(FILE_NAME + "." + "play");
                sendLabelGlyph.setSize(20);
                sendLabelGlyph.setFill(Paint.valueOf("#4d804d"));
                sendButton.setGraphic(sendLabelGlyph);
                sendButton.setTooltip(new Tooltip("发送请求"));


                SVGGlyph refreshLabelGlyph = SVGGlyphLoader.getIcoMoonGlyph(FILE_NAME + "." + "refresh");
                refreshLabelGlyph.setSize(20);
                refreshLabelGlyph.setFill(Paint.valueOf("#4d804d"));
                refreshButton.setGraphic(refreshLabelGlyph);
                refreshButton.setTooltip(new Tooltip("刷新"));


                SVGGlyph saveLabelGlyph = SVGGlyphLoader.getIcoMoonGlyph(FILE_NAME + "." + "floppy-o, save");
                saveLabelGlyph.setSize(20);
                saveLabelGlyph.setFill(Paint.valueOf("#4d804d"));
                saveButton.setGraphic(saveLabelGlyph);
                saveButton.setTooltip(new Tooltip("保存"));

                SVGGlyph settingLabelGlyph = SVGGlyphLoader.getIcoMoonGlyph(FILE_NAME + "." + "cogs2");
                settingLabelGlyph.setSize(20);
                settingLabelGlyph.setFill(Paint.valueOf("#4d804d"));
                settingButton.setGraphic(settingLabelGlyph);
                settingButton.setTooltip(new Tooltip("设置/新增环境"));
            }catch (Exception e){
                throw new RuntimeException(e);
            }

        });

    }

    @Subscribe
    public void subscribeMessage(DialogStyleDto dialogStyleDto) throws Exception {
        switch (dialogStyleDto.getType()) {
            case OperateTypeEnum.CREATE -> CompletableFuture.runAsync(() -> {
                Platform.runLater(() -> LoadingUtil.show(mainStackPane));
                try {
                    BuildSoap.buildSoap(dialogStyleDto.getText().split(",")[1], dialogStyleDto.getText().split(",")[0]);
                    Platform.runLater(this::initTree);
                }
                catch (Exception e) {
                    Platform.runLater(() -> DialogController.errorInfo(mainStackPane, e.getMessage()));

                    logger.error("", e);
                }
                finally {
                    Platform.runLater(() -> LoadingUtil.remove(mainStackPane));
                }
            });
            case OperateTypeEnum.ADD -> {
                List<TreeItem<String>> list = treeView.getSelectionModel().getSelectedItem().getChildren();
                List<Integer> numberList = new ArrayList<>();
                String regEx = "[^0-9]";
                Pattern p = Pattern.compile(regEx);
                int size = 0;
                if (!treeView.getSelectionModel().getSelectedItem().getChildren().isEmpty()) {
                    for (Object o : list) {
                        Matcher m = p.matcher(o.toString());
                        String trim = m.replaceAll("").trim();
                        if (StringUtils.isNotEmpty(trim) && StringUtils.isNumeric(trim)) {
                            numberList.add(Integer.parseInt(trim));
                        }
                        else {
                            numberList.add(-1);
                        }
                    }

                    size = Collections.max(numberList) + 1;
                }
                treeView.getSelectionModel().getSelectedItem().getChildren()
                    .add(new TreeItem<>("Request " +
                        size));
                SoapWidgetDto widgetDto = new SoapWidgetDto();
                widgetDto.setThirdLevel(treeView.getSelectionModel().getSelectedItem().getValue());
                widgetDto.setSecondLevel(treeView.getSelectionModel().getSelectedItem().getParent().getValue());
                widgetDto.setFirstLevel(treeView.getSelectionModel().getSelectedItem().getParent().getParent().getValue());
                try {
                    Dom4jUtil.copySampleToRequest(widgetDto, "Request " + size);
                }
                catch (Exception ex) {
                    logger.error("", ex);
                }
            }
            case OperateTypeEnum.DEL -> {
                if (dialogStyleDto.getLevel() == 1) {
                    File file = new File(BASE_PATH + treeView.getSelectionModel().getSelectedItem().getValue() + ".xml");
                    if (file.exists()) {
                        if (file.delete()) {
                            treeView.getRoot().getChildren().remove(treeView.getSelectionModel().getSelectedItem());
                        }
                    }
                }
                else if (dialogStyleDto.getLevel() == 4) {
                    try {
                        Dom4jUtil.removeSpecifiedNode(soapWidgetDto);
                        treeView.getSelectionModel().getSelectedItem().getParent().getChildren().
                            remove(treeView.getSelectionModel().getSelectedItem());
                    }
                    catch (DocumentException | IOException e) {
                        logger.error("", e);
                    }
                }
            }
            case OperateTypeEnum.MOD -> {
                //修改只存在于父节点下的子节点,也就是当前节点
                if (!NodeListValidate.treeValidate(dialogStyleDto.getText(), treeView.getSelectionModel().getSelectedItem().getParent().getChildren())) {
                    DialogController.errorInfo(mainStackPane, "名称重复了");
                    return;
                }
                Dom4jUtil.modSoapDtoXmlName(soapWidgetDto, dialogStyleDto.getText());
                treeView.getSelectionModel().getSelectedItem().setValue(dialogStyleDto.getText());
            }
            case OperateTypeEnum.QUERY, OperateTypeEnum.REFRESH -> refershLabelAction();
            default -> logger.info("");

        }
    }


}
