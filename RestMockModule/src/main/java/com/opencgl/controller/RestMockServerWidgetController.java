package com.opencgl.controller;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.EditingCell;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.dao.RestMockServerWidgetDao;
import com.opencgl.model.RestMockServerWidgetDto;
import com.opencgl.model.RestMockTableBean;
import com.opencgl.views.RestMockServerWidgetView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
public class RestMockServerWidgetController extends RestMockServerWidgetView implements Initializable {


    private final static Logger logger = LoggerFactory.getLogger(RestMockServerWidgetController.class);
    private final RestMockServerWidgetDao restMockServerWidgetDao = new RestMockServerWidgetDao();
    private final ObservableList<RestMockTableBean> tableData = FXCollections.observableArrayList();
    private static final Pattern P = Pattern.compile("\\s*|\t|\r|\n");

    private ServerSocket serverSocket = null;

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        restMockServerWidgetDao.checkTable();
        initData();
        initView();
        initTableView();
        initEvent();
    }


    public void initData() {
        try {
            List<RestMockTableBean> list = new ArrayList<>();
            for (RestMockServerWidgetDto restMockServerWidgetDto : restMockServerWidgetDao.queryAllData()) {
                list.add(new RestMockTableBean(restMockServerWidgetDto.getIsEnable(), restMockServerWidgetDto.getContentPath()
                    , restMockServerWidgetDto.getResponseHeader(), replaceBlank(restMockServerWidgetDto.getResponseContent()), restMockServerWidgetDto.getDescription()));
            }
            tableData.addAll(list);
        }
        catch (Exception e) {
            DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
        }
    }

    @SneakyThrows
    private void initView() {
        responseContentHbox.getChildren().remove(responseContent);
        startButton.setTooltip(new Tooltip("启动"));
        stopButton.setTooltip(new Tooltip("停止"));
    }

    private void initTableView() {
        isEnabledTableColumn.setCellValueFactory(new PropertyValueFactory<>("isEnable"));
        isEnabledTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(isEnabledTableColumn));

        contentPathTableColumn.setCellValueFactory(new PropertyValueFactory<>("contentTextPath"));
        contentPathTableColumn.setCellFactory((TableColumn<RestMockTableBean, String> p) -> new EditingCell<>());

        responseHeaderTableColumn.setCellValueFactory(new PropertyValueFactory<>("responseHeader"));
        responseHeaderTableColumn.setCellFactory((TableColumn<RestMockTableBean, String> p) -> new EditingCell<>());


        responseContentTableColumn.setCellValueFactory(new PropertyValueFactory<>("responseContent"));
        //responseContentTableColumn.setCellFactory((TableColumn<RestMockTableBean, String> p) -> new EditingCell<>());

        desTableColumn.setCellValueFactory(new PropertyValueFactory<>("des"));
        desTableColumn.setCellFactory((TableColumn<RestMockTableBean, String> p) -> new EditingCell<>());

      /*  stateTableColumn.setCellValueFactory(new PropertyValueFactory<>("state"));
        stateTableColumn.setCellFactory((TableColumn<RestMockTableBean,String> p) -> new EditingCell<>());
*/
        tableViewMain.setItems(tableData);
    }


    @FXML
    private void addItemAction() {
        try {
            //|| Strings.isNullOrEmpty(responseContent.getText())
            if (StringUtils.isEmpty(contentPath.getText()) || StringUtils.isEmpty(responseHeader.getText())) {
                TooltipUtil.showToast(mainStackPane, "参数不能为空");
                return;
            }
            restMockServerWidgetDao.insertData(RestMockServerWidgetDto.builder()
                .isEnable(false)
                .contentPath(contentPath.getText())
                .responseHeader(responseHeader.getText())
                .responseContent(responseContent.getText())
                .description(des.getText())
                .build());
            tableData.add(new RestMockTableBean(false, contentPath.getText(),
                responseHeader.getText(), responseContent.getText(), des.getText()));
        }
        catch (Exception e) {
            DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
        }
    }

    Socket socket = null;

    private void initEvent() {
        stateHbox.getChildren().remove(statusProcessBar);
        startButton.setOnAction(event -> {
            if (!isNumeric(portTextField.getText()) || Integer.parseInt(portTextField.getText()) > 65535 || Integer.parseInt(portTextField.getText()) < 1) {
                DialogUtil.showErrorInfo("请输入1到65535范围之间端口!", mainStackPane);
                return;
            }
            outputTextArea.clear();
            tableViewMain.setDisable(true);

            //   HttpServer httpServer = null;
            CompletableFuture.runAsync(() -> {
                try {
                    serverSocket = new ServerSocket(Integer.parseInt(portTextField.getText()));
                    List<RestMockTableBean> restMockTableBeans = tableData;
                    while (true) {
                        // 通信socket的获得
                        socket = serverSocket.accept();
                        logger.info("client connected...");
                        //读取数据
                        InputStream in = socket.getInputStream();
                        byte[] b = new byte[1024];
                        int len = in.read(b);
                        String s = new String(b, 0, len);

                        //返回数据
                        String res = "";

                        for (RestMockTableBean restMockTableBean : restMockTableBeans) {
                            if (restMockTableBean.getIsEnable() && s.contains(restMockTableBean.getContentTextPath())) {
                                res = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + restMockTableBean.getResponseHeader() + "\r\n" + "\r\n" +
                                    restMockTableBean.getResponseContent();
                            }
                        }
                        String finalRes = res;
                        Platform.runLater(() -> {
                            outputTextArea.appendText("客户端:" + socket.getRemoteSocketAddress() + "\r\n请求信息:" + s + "\r\n" + "返回信息:" + finalRes + "\r\n\n\n\n");
                            outputTextArea.resize(outputTextArea.getWidth(), outputTextArea.getHeight());
                        });
                        OutputStream out = socket.getOutputStream();
                        out.write(res.getBytes());
                        //关闭连接，会将in和out一起关闭
                        socket.close();
                        logger.info("connection closed...");

                    }

                }
                catch (IOException e) {

                }
            });
            startButton.setDisable(true);
            stateHbox.getChildren().add(statusProcessBar);
        });

        stopButton.setOnAction(event -> {
            if (socket != null) {
                try {
                    socket.close();
                }
                catch (IOException e) {

                }
            }
            if (serverSocket != null) {
                try {

                    serverSocket.close();
                }
                catch (IOException e) {

                }
            }
            startButton.setDisable(false);
            stateHbox.getChildren().remove(statusProcessBar);
            tableViewMain.setDisable(false);
        });

//        responseContentButton.setOnAction(event -> DialogController.showTextArea2(eventBus, responseContent.getText(), mainStackPane));


        ContextMenu contextMenu = new ContextMenu();
        MenuItem editMenuItem = new MenuItem("编辑返回参数");
        MenuItem delMenuItem = new MenuItem("删除当前节点");
        MenuItem copyMenuItem = new MenuItem("复制当前节点");

        contextMenu.getItems().addAll(editMenuItem, delMenuItem, copyMenuItem);
        tableViewMain.setContextMenu(contextMenu);

        editMenuItem.setOnAction(event -> {
            try {
                DialogUtil.show(restMockServerWidgetDao.queryByContentPath(tableViewMain.getSelectionModel().getSelectedItem().getContentTextPath()));
            }
            catch (Exception e) {
                logger.error("", e);
            }

        });


        delMenuItem.setOnAction(event -> {
            RestMockServerWidgetDto restMockServerWidgetDto = RestMockServerWidgetDto.builder().contentPath(tableViewMain.getSelectionModel().getSelectedItem().getContentTextPath()).build();
            try {
                restMockServerWidgetDao.delLevelData(restMockServerWidgetDto);
                tableData.remove(tableViewMain.getSelectionModel().getSelectedItem());
            }
            catch (Exception e) {
                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
            }

        });

        copyMenuItem.setOnAction(event -> {
            RestMockTableBean restMockTableBean = tableViewMain.getSelectionModel().getSelectedItem();
            String newName = FormatVariableUtil.format("-copy-${Random} ");
            RestMockServerWidgetDto restMockServerWidgetDto = RestMockServerWidgetDto.builder()
                .isEnable(false)
                .contentPath(newName + restMockTableBean.getContentTextPath())
                .responseHeader(restMockTableBean.getResponseHeader())
                .responseContent(restMockTableBean.getResponseContent())
                .description(restMockTableBean.getDes())
                .build();
            try {
                restMockServerWidgetDao.insertData(restMockServerWidgetDto);
                RestMockTableBean restMockTableBean1 = new RestMockTableBean(false, restMockTableBean.getContentTextPath(), restMockTableBean.getResponseHeader(), restMockTableBean.getResponseContent(), restMockTableBean.getDes());
                tableData.add(restMockTableBean1);
            }
            catch (Exception e) {

                logger.error("", e);
                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);

            }
        });

      /*  isEnabledTableColumn.setOnEditCommit(event -> {
            try {
                RestMockTableBean restMockTableBean = tableViewMain.getSelectionModel().getSelectedItem();
                restMockServerWidgetDao.updateData(RestMockServerWidgetDto.builder()
                        .isEnable(event.getNewValue())
                        .contentPath(restMockTableBean.getContentTextPath())
                        .responseHeader(restMockTableBean.getResponseHeader())
                        .responseContent(restMockTableBean.getResponseContent())
                        .description(restMockTableBean.getDes()).build(), restMockTableBean.getContentTextPath());
            } catch (Exception e) {
                
                DialogUtil.showErrorInfo(mainStackPane, e.getMessage());
            }
        });*/

        contentPathTableColumn.setOnEditCommit(event -> {
            try {
                RestMockTableBean restMockTableBean = tableViewMain.getSelectionModel().getSelectedItem();
                restMockServerWidgetDao.updateData(RestMockServerWidgetDto.builder()
                    .isEnable(restMockTableBean.getIsEnable())
                    .contentPath(event.getNewValue())
                    .responseHeader(restMockTableBean.getResponseHeader())
                    .responseContent(restMockTableBean.getResponseContent())
                    .description(restMockTableBean.getDes()).build(), restMockTableBean.getContentTextPath());
            }
            catch (Exception e) {

                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
            }
        });

        responseHeaderTableColumn.setOnEditCommit(event -> {
            try {
                RestMockTableBean restMockTableBean = tableViewMain.getSelectionModel().getSelectedItem();
                restMockServerWidgetDao.updateData(RestMockServerWidgetDto.builder()
                    .isEnable(restMockTableBean.getIsEnable())
                    .contentPath(restMockTableBean.getContentTextPath())
                    .responseHeader(event.getNewValue())
                    .responseContent(restMockTableBean.getResponseContent())
                    .description(restMockTableBean.getDes()).build(), restMockTableBean.getContentTextPath());
            }
            catch (Exception e) {

                logger.error("", e);
                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
            }
        });


        desTableColumn.setOnEditCommit(event -> {
            try {
                RestMockTableBean restMockTableBean = tableViewMain.getSelectionModel().getSelectedItem();
                restMockServerWidgetDao.updateData(RestMockServerWidgetDto.builder()
                    .isEnable(restMockTableBean.getIsEnable())
                    .contentPath(restMockTableBean.getContentTextPath())
                    .responseHeader(restMockTableBean.getResponseHeader())
                    .responseContent(restMockTableBean.getResponseContent())
                    .description(event.getNewValue()).build(), restMockTableBean.getContentTextPath());
            }
            catch (Exception e) {

                DialogUtil.showErrorInfo(e.getMessage(), mainStackPane);
            }
        });

    }


    public static String replaceBlank(String str) {
        String dest = "";
        if (str != null) {
            Matcher m = P.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }


    public static boolean isNumeric(String s) {
        if (s != null && !s.trim().isEmpty()) {
            return s.matches("^[0-9]*$");
        }
        else {
            return false;
        }
    }
}
