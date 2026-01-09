package com.opencgl.controller;


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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.LoadingMask;
import com.opencgl.base.utils.OperationHisRecord;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.impl.RestSender;
import com.opencgl.model.DialogStyleDto;
import com.opencgl.model.OperateTypeEnum;
import com.opencgl.model.RestRequest;
import com.opencgl.model.RestResponse;
import com.opencgl.model.SoapWidgetDto;
import com.opencgl.utils.FourTreeViewUtil;
import com.opencgl.utils.NodeListValidate;
import com.opencgl.views.SoapWidgetView;
import com.opencgl.soap.i18n.I18N;
import com.opencgl.utils.wsdl2soap.BuildSoap;
import com.opencgl.utils.wsdl2soap.Dom4jUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.event.EventHandler;
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
    private final RestSender sendMessageService = new RestSender();
    private final EventBus eventBus = new EventBus();
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "soap-test-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Future<?>> backgroundTasks = new CopyOnWriteArrayList<>();
    private volatile boolean disposed;
    private volatile boolean eventBusRegistered;
    private EventHandler<KeyEvent> shortcutHandler;
    List<SoapWidgetDto> soapWidgetDtos = new ArrayList<>();
    private final LoadingMask loadingMask = new LoadingMask();

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        submitBackground(() -> {
            runOnFx(() -> loadingMask.show(mainStackPane));
            try {
                init();
                showTooltip();
                synchronized (eventBus) {
                    if (disposed) return;
                    eventBus.register(this);
                    eventBusRegistered = true;
                }
                if (disposed) return;
                //设置控件
                setControlAndStyle();
                initTree();
                setupKeyboardShortcuts();
                initI18n();
            }
            catch (Exception e) {

                logger.error("", e);
            }
            finally {
                runOnFx(loadingMask::hide);
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
                runOnFx(() -> ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeView));
            }
            catch (Exception e) {
                logger.error("", e);
            }
        }
    }

    public void init() {
     runOnFx(() -> {
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
            DialogController.errorInfo(mainStackPane, I18N.get("msg.url_empty"));
            return;
        }
        submitBackground(() -> {
            String reqMsg = null;
            runOnFx(() -> loadingMask.show(mainStackPane));
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
                runOnFx(loadingMask::hide);
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
            TooltipUtil.showToast(mainStackPane, I18N.get("msg.node_not_selected"));
            return;
        }
        try {
            Dom4jUtil.modSoapDtoXmlValue(soapWidgetDto, inputTextArea.getText());
            TooltipUtil.showToast(mainStackPane, I18N.get("msg.success"));
        }
        catch (Exception e) {

            logger.error("", e);
        }
    }

    @FXML
    public void settingLabelAction() {
        DialogController.webServiceConfigure(eventBus, new DialogStyleDto(), mainStackPane);
    }

    private void initI18n() {
        Tooltip sendTip = new Tooltip();
        sendTip.textProperty().bind(I18N.getBinding("tooltip.send"));
        sendButton.setTooltip(sendTip);
        Tooltip refreshTip = new Tooltip();
        refreshTip.textProperty().bind(I18N.getBinding("tooltip.refresh"));
        refreshButton.setTooltip(refreshTip);
        Tooltip saveTip = new Tooltip();
        saveTip.textProperty().bind(I18N.getBinding("tooltip.save"));
        saveButton.setTooltip(saveTip);
        Tooltip settingTip = new Tooltip();
        settingTip.textProperty().bind(I18N.getBinding("tooltip.settings"));
        settingButton.setTooltip(settingTip);
    }

    public void showTooltip() {
        chooseIntComboBox.setOnMouseEntered(event -> chooseIntComboBox.setTooltip(new Tooltip((String) chooseIntComboBox.getValue())));
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        runOnFx(() -> {
            // Ctrl+Enter 发送请求
            KeyCodeCombination sendShortcut = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);
            // Ctrl+S 保存
            KeyCodeCombination saveShortcut = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
            // Ctrl+R 刷新
            KeyCodeCombination refreshShortcut = new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN);
            // F5 刷新
            KeyCodeCombination f5Shortcut = new KeyCodeCombination(KeyCode.F5);
            
            shortcutHandler = event -> {
                if (sendShortcut.match(event)) {
                    sendLabelAction();
                    event.consume();
                } else if (saveShortcut.match(event)) {
                    saveLabelAction();
                    event.consume();
                } else if (refreshShortcut.match(event) || f5Shortcut.match(event)) {
                    refershLabelAction();
                    event.consume();
                }
            };
            mainStackPane.addEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
        });
    }

    public void setControlAndStyle() {
        runOnFx(() -> {
            try {
                /*style and control*/
                SVGGlyph sendLabelGlyph = SVGGlyphLoader.getIcoMoonGlyph(FILE_NAME + "." + "play");
                sendLabelGlyph.setSize(20);
                sendLabelGlyph.setFill(Paint.valueOf("#4d804d"));
                sendButton.setGraphic(sendLabelGlyph);
                // Tooltips set in initI18n()

                SVGGlyph refreshLabelGlyph = SVGGlyphLoader.getIcoMoonGlyph(FILE_NAME + "." + "refresh");
                refreshLabelGlyph.setSize(20);
                refreshLabelGlyph.setFill(Paint.valueOf("#4d804d"));
                refreshButton.setGraphic(refreshLabelGlyph);

                SVGGlyph saveLabelGlyph = SVGGlyphLoader.getIcoMoonGlyph(FILE_NAME + "." + "floppy-o, save");
                saveLabelGlyph.setSize(20);
                saveLabelGlyph.setFill(Paint.valueOf("#4d804d"));
                saveButton.setGraphic(saveLabelGlyph);

                SVGGlyph settingLabelGlyph = SVGGlyphLoader.getIcoMoonGlyph(FILE_NAME + "." + "cogs2");
                settingLabelGlyph.setSize(20);
                settingLabelGlyph.setFill(Paint.valueOf("#4d804d"));
                settingButton.setGraphic(settingLabelGlyph);
            }catch (Exception e){
                throw new RuntimeException(e);
            }

        });

    }

    @Subscribe
    public void subscribeMessage(DialogStyleDto dialogStyleDto) throws Exception {
        if (disposed) return;
        switch (dialogStyleDto.getType()) {
            case OperateTypeEnum.CREATE -> submitBackground(() -> {
                runOnFx(() -> loadingMask.show(mainStackPane));
                try {
                    BuildSoap.buildSoap(dialogStyleDto.getText().split(",")[1], dialogStyleDto.getText().split(",")[0]);
                    runOnFx(this::initTree);
                }
                catch (Exception e) {
                    runOnFx(() -> DialogController.errorInfo(mainStackPane, e.getMessage() != null ? e.getMessage() : ""));

                    logger.error("", e);
                }
                finally {
                    runOnFx(loadingMask::hide);
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
                    DialogController.errorInfo(mainStackPane, I18N.get("dialog.duplicate_name"));
                    return;
                }
                Dom4jUtil.modSoapDtoXmlName(soapWidgetDto, dialogStyleDto.getText());
                treeView.getSelectionModel().getSelectedItem().setValue(dialogStyleDto.getText());
            }
            case OperateTypeEnum.QUERY, OperateTypeEnum.REFRESH -> refershLabelAction();
            default -> logger.info("");

        }
    }

    private void submitBackground(Runnable action) {
        if (disposed) return;
        backgroundTasks.removeIf(Future::isDone);
        backgroundTasks.add(backgroundExecutor.submit(action));
    }

    private void runOnFx(Runnable action) {
        if (disposed) return;
        Platform.runLater(() -> {
            if (!disposed) action.run();
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Future<?> task : backgroundTasks) {
            try {
                task.cancel(true);
            } catch (Exception ignored) {
            }
        }
        backgroundTasks.clear();
        try {
            backgroundExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
        synchronized (eventBus) {
            if (eventBusRegistered) {
                try {
                    eventBus.unregister(this);
                } catch (Exception ignored) {
                } finally {
                    eventBusRegistered = false;
                }
            }
        }
        try {
            sendMessageService.close();
        } catch (Exception ignored) {
        }
        EventHandler<KeyEvent> handler = shortcutHandler;
        if (handler != null && mainStackPane != null) {
            Runnable removeHandler = () -> mainStackPane.removeEventFilter(KeyEvent.KEY_PRESSED, handler);
            if (Platform.isFxApplicationThread()) {
                removeHandler.run();
            } else {
                Platform.runLater(removeHandler);
            }
            shortcutHandler = null;
        }
    }


}
