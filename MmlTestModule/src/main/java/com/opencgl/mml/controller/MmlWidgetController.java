package com.opencgl.mml.controller;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import com.opencgl.mml.views.MmlWidgetView;
import com.opencgl.mml.dao.MmlWidgetDao;
import com.opencgl.mml.factory.MmlSendMessageFactory;
import com.opencgl.mml.model.MmlRequest;
import com.opencgl.mml.model.MmlResponse;
import com.opencgl.mml.model.MmlWidgetDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
public class MmlWidgetController extends MmlWidgetView implements Initializable, TreeOperateService<MmlWidgetDto> {


    private final Logger logger = LoggerFactory.getLogger(MmlWidgetController.class);

    private final SendMessageService<MmlRequest, MmlResponse> sendMessageService = MmlSendMessageFactory.sendMmlMessage();
    private final MmlWidgetDao mmlWidgetDao = new MmlWidgetDao();
    private TreeView<MmlWidgetDto> treeView;
    private final LoadingMask loadingMask = new LoadingMask();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-mml-request");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Future<?> currentRequest;
    private volatile boolean disposed;

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
        setControlAndStyle();
        // 使用新版TreeViewBuilder构建树
        VBox treeViewVbox = new TreeViewBuilder<MmlWidgetDto>()
            .enableSearch(true)
            .onTreeCreated(tree -> this.treeView = tree)
            .service(this)
            .dataType(MmlWidgetDto.class)
            .enableDragDrop(true)
            .build();
        ThemeSwitchUtil.treeStyleSwitch(mainStackPane, contentBorderPane, treeViewVbox);
        setupKeyboardShortcuts();
    }

    public void init() throws Exception {
        mmlWidgetDao.checkTable();
        //设置悬停展示url
        ipTextField.setTooltip(new Tooltip(ipTextField.getText()));
        userTextField.setTooltip(new Tooltip(userTextField.getText()));
        passTextField.setTooltip(new Tooltip(passTextField.getText()));
    }


    @FXML
    public void sendLabelAction() {
        if (StringUtils.isEmpty(ipTextField.getText())
            || StringUtils.isEmpty(portTextField.getText())) {
            DialogUtil.showErrorInfo("请求用户名/密码/IP/端口不能为空!", mainStackPane);
            return;
        }

        currentRequest = executor.submit(() -> {
            if (disposed) return;
            Platform.runLater(() -> loadingMask.show(mainStackPane));
            String requestText = FormatVariableUtil.format(inputTextArea.getText());
            logger.info("request message is {}", requestText);
            try {
                MmlRequest mmlRequest = MmlRequest.builder()
                    .requestUsername(userTextField.getText())
                    .requestPassword(passTextField.getText())
                    .requestIp(ipTextField.getText())
                    .requestPort(portTextField.getText())
                    .requestMessage(requestText)
                    .build();
                MmlResponse response = sendMessageService.send(mmlRequest);
                outputTextArea.setText(response.getResultMsg());
            }
            catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw, true));
                logger.error("", e);
                outputTextArea.setText(sw.toString());
            }
            finally {
                Platform.runLater(() -> {
                    if (!disposed) loadingMask.hide();
                });
                OperationHisRecord.record("SEND MML MESSAGE:\n" + "请求ip:" + userTextField.getText() +
                    "\n" + "请求端口:" + portTextField.getText() + "\n" + "请求IP&端口:" + ipTextField.getText() + ":" + portTextField.getText() + "\n"
                    + "\n" + "输入:\n" + requestText + "\n"
                    + "输出:\n" + outputTextArea.getText() + "\t");
            }
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        Future<?> request = currentRequest;
        currentRequest = null;
        if (request != null) {
            request.cancel(true);
        }
        executor.shutdownNow();
    }

    @FXML
    public void refreshLabelAction() {

    }

    @FXML
    public void saveLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem() == null) {
            TooltipUtil.showToast(mainStackPane, "子节点未选中");
            return;
        }
        MmlWidgetDto mmlWidgetDto = new MmlWidgetDto();
        mmlWidgetDto.setRequestUsername(userTextField.getText());
        mmlWidgetDto.setRequestPassword(passTextField.getText());
        mmlWidgetDto.setRequestIp(ipTextField.getText());
        mmlWidgetDto.setRequestPort(portTextField.getText());
        mmlWidgetDto.setInputText(inputTextArea.getText());
        try {
            mmlWidgetDao.updateData(mmlWidgetDto);
            TooltipUtil.showToast(mainStackPane, "成功");
        }
        catch (Exception e) {
            logger.error("", e);
            TooltipUtil.showToast(mainStackPane, "ERROR");
        }
    }

    @FXML
    public void copyLabelAction() {
        if (treeView.getSelectionModel().getSelectedItem().getValue() != null
            || !treeView.getSelectionModel().getSelectedItem().getValue().getIsLeaf()
        ) {
            TooltipUtil.showToast(mainStackPane, "子节点未选中");
            return;
        }
        MmlWidgetDto mmlWidgetDto = new MmlWidgetDto();
        mmlWidgetDto.setName("copy-" + treeView.getSelectionModel().getSelectedItem().getValue() + "-" + FormatVariableUtil.getRandom(8));
        mmlWidgetDto.setRequestUsername(userTextField.getText());
        mmlWidgetDto.setRequestPassword(ipTextField.getText());
        mmlWidgetDto.setRequestIp(ipTextField.getText());
        mmlWidgetDto.setRequestPort(portTextField.getText());
        mmlWidgetDto.setInputText(inputTextArea.getText());

        try {
            Long newId = mmlWidgetDao.insertData(mmlWidgetDto);
            mmlWidgetDto.setId(newId);
            CustomizeTreeItem<MmlWidgetDto> customizetreeitem = new CustomizeTreeItem<>(mmlWidgetDto);
            treeView.getSelectionModel().getSelectedItem().getParent().getChildren().add(customizetreeitem);
            TooltipUtil.showToast(mainStackPane, "成功");
        }
        catch (Exception e) {
            TooltipUtil.showToast(mainStackPane, "失败");
            logger.error("", e);
        }
    }


    public void setControlAndStyle() {
        sendButton.setTooltip(new Tooltip("发送请求"));
        refreshButton.setTooltip(new Tooltip("刷新"));
        copyButton.setTooltip(new Tooltip("复制"));
        saveButton.setTooltip(new Tooltip("保存"));
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        // Ctrl+Enter 发送请求
        KeyCodeCombination sendShortcut = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);
        // Ctrl+S 保存
        KeyCodeCombination saveShortcut = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
        // Ctrl+R 刷新
        KeyCodeCombination refreshShortcut = new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN);
        // F5 刷新
        KeyCodeCombination f5Shortcut = new KeyCodeCombination(KeyCode.F5);
        // Ctrl+D 复制
        KeyCodeCombination copyShortcut = new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN);

        mainStackPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (sendShortcut.match(event)) {
                sendLabelAction();
                event.consume();
            }
            else if (saveShortcut.match(event)) {
                saveLabelAction();
                event.consume();
            }
            else if (refreshShortcut.match(event) || f5Shortcut.match(event)) {
                refreshLabelAction();
                event.consume();
            }
            else if (copyShortcut.match(event)) {
                copyLabelAction();
                event.consume();
            }
        });
    }

    @Override
    public CustomizeTreeItem<MmlWidgetDto> add(MmlWidgetDto mmlWidgetDto) {
        mmlWidgetDto.setRequestUsername(userTextField.getText());
        mmlWidgetDto.setRequestPassword(passTextField.getText());
        mmlWidgetDto.setRequestIp(ipTextField.getText());
        mmlWidgetDto.setRequestPort(portTextField.getText());
        mmlWidgetDto.setInputText(inputTextArea.getText());
        try {
            Long newId = mmlWidgetDao.insertData(mmlWidgetDto);
            mmlWidgetDto.setId(newId);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return new CustomizeTreeItem<>(mmlWidgetDto);
    }

    @Override
    public CustomizeTreeItem<MmlWidgetDto> importData(MmlWidgetDto mmlWidgetDto) {
        try {
            Long newId = mmlWidgetDao.insertData(mmlWidgetDto);
            mmlWidgetDto.setId(newId);
            return new CustomizeTreeItem<>(mmlWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<MmlWidgetDto> delete(MmlWidgetDto mmlWidgetDto) {
        try {
            mmlWidgetDao.delLevelData(mmlWidgetDto);
            return new CustomizeTreeItem<>(mmlWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public CustomizeTreeItem<MmlWidgetDto> update(MmlWidgetDto mmlWidgetDto) {
        try {
            mmlWidgetDto.setName(mmlWidgetDto.getName());
            mmlWidgetDto.setRequestUsername(userTextField.getText());
            mmlWidgetDto.setRequestPassword(passTextField.getText());
            mmlWidgetDto.setRequestIp(ipTextField.getText());
            mmlWidgetDto.setRequestPort(portTextField.getText());
            mmlWidgetDto.setInputText(inputTextArea.getText());
            mmlWidgetDao.updateData(mmlWidgetDto);
            return new CustomizeTreeItem<>(mmlWidgetDto);
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
        return null;
    }

    @Override
    public void changeToDisplay(MmlWidgetDto mmlWidgetDto) {
        try {
            userTextField.setText(mmlWidgetDto.getRequestUsername());
            passTextField.setText(mmlWidgetDto.getRequestPassword());
            ipTextField.setText(mmlWidgetDto.getRequestIp());
            portTextField.setText(mmlWidgetDto.getRequestPort());
            inputTextArea.setText(mmlWidgetDto.getInputText());
        }
        catch (Exception e) {
            logger.error("", e);
            DialogUtil.showErrorInfo(e.getMessage());
        }
    }

    @Override
    public List<MmlWidgetDto> queryAll() {
        try {
            return mmlWidgetDao.queryAllData();
        }
        catch (Exception e) {
            DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
        }
        return null;
    }

    @Override
    public boolean supportImportAndExport() {
        return true;
    }

    @Override
    public void validateImportData(List<MmlWidgetDto> mmlWidgetDtos) {
    }

    @Override
    public void updatePositionOnly(MmlWidgetDto dto) {
        try {
            mmlWidgetDao.updatePositionOnly(dto);
        }
        catch (Exception e) {
            logger.error("更新位置失败", e);
        }
    }
}

