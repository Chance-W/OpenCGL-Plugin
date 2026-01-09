package com.opencgl.ftp.controller;


import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.base.utils.CommitOnBlurTableCell;
import com.opencgl.ftp.utils.FileChooserUtil;
import com.opencgl.ftp.utils.FtpServerService;
import com.opencgl.ftp.utils.JavaFxViewUtil;
import com.opencgl.ftp.i18n.I18N;
import com.opencgl.ftp.model.FtpServerTableBean;
import com.opencgl.ftp.views.FtpServerView;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;


/**
 * @author Chance.W
 */
@Getter
@Setter
@Slf4j
public class FtpServerController extends FtpServerView {
    private FtpServerService ftpServerService = new FtpServerService(this);
    private ObservableList<FtpServerTableBean> tableData = FXCollections.observableArrayList();

    private boolean serverRunning = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initView();
        initEvent();
        initService();
        initI18n();
    }

    private void initI18n() {
        isEnabledTableColumn.textProperty().bind(I18N.getBinding("column.enabled"));
        userNameTableColumn.textProperty().bind(I18N.getBinding("column.username"));
        passwordTableColumn.textProperty().bind(I18N.getBinding("column.password"));
        homeDirectoryTableColumn.textProperty().bind(I18N.getBinding("column.shared_dir"));
        downFIleTableColumn.textProperty().bind(I18N.getBinding("column.download"));
        upFileTableColumn.textProperty().bind(I18N.getBinding("column.upload"));
        deleteFileTableColumn.textProperty().bind(I18N.getBinding("column.readwrite"));
    }

    private void initView() {
        ftpServerService.loadingConfigure();
        userNameTableColumn.setCellValueFactory(new PropertyValueFactory<>("userName"));
        userNameTableColumn.setCellFactory(CommitOnBlurTableCell.forStringColumn());
        userNameTableColumn.setOnEditCommit((TableColumn.CellEditEvent<FtpServerTableBean, String> t) -> {
            t.getRowValue().setUserName(t.getNewValue());
        });

        passwordTableColumn.setCellValueFactory(new PropertyValueFactory<>("password"));
        passwordTableColumn.setCellFactory(CommitOnBlurTableCell.forStringColumn());
        passwordTableColumn.setOnEditCommit((TableColumn.CellEditEvent<FtpServerTableBean, String> t) -> {
            t.getRowValue().setPassword(t.getNewValue());
        });

        homeDirectoryTableColumn
            .setCellValueFactory(new PropertyValueFactory<>("homeDirectory"));
        homeDirectoryTableColumn.setCellFactory(CommitOnBlurTableCell.forStringColumn());
        homeDirectoryTableColumn.setOnEditCommit((TableColumn.CellEditEvent<FtpServerTableBean, String> t) -> {
            t.getRowValue().setHomeDirectory(t.getNewValue());
        });

        downFIleTableColumn.setCellValueFactory(new PropertyValueFactory<>("downFIle"));
        downFIleTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(downFIleTableColumn));
        upFileTableColumn.setCellValueFactory(new PropertyValueFactory<>("upFile"));
        upFileTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(upFileTableColumn));
        deleteFileTableColumn.setCellValueFactory(new PropertyValueFactory<>("deleteFile"));
        deleteFileTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(deleteFileTableColumn));
        isEnabledTableColumn.setCellValueFactory(new PropertyValueFactory<>("isEnabled"));
        isEnabledTableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(isEnabledTableColumn));
        tableViewMain.setItems(tableData);

        JavaFxViewUtil.setSpinnerValueFactory(maxConnectCountSpinner, 1, Integer.MAX_VALUE, 1000);
    }

    private void initEvent() {
        FileChooserUtil.setOnDrag(homeDirectoryTextField, FileChooserUtil.FileType.FOLDER);
        FileChooserUtil.setOnDrag(anonymousLoginEnabledTextField, FileChooserUtil.FileType.FOLDER);
        tableData.addListener((ListChangeListener.Change<? extends FtpServerTableBean> tableBean) -> {
            try {
                saveConfigure(null);
            }
            catch (Exception e) {
                e.printStackTrace();
                log.error("", e);
            }
        });
        tableViewMain.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                MenuItem menu_Copy = new MenuItem(I18N.get("menu.copy_row"));
                menu_Copy.setOnAction(event1 -> {
                    FtpServerTableBean tableBean = tableViewMain.getSelectionModel().getSelectedItem();
                    FtpServerTableBean tableBean2 = new FtpServerTableBean(tableBean.getPropertys());
                    tableData.add(tableViewMain.getSelectionModel().getSelectedIndex(), tableBean2);
                });
                MenuItem menu_Remove = new MenuItem(I18N.get("menu.remove_row"));
                menu_Remove.setOnAction(event1 -> {
                    deleteSelectRowAction(null);
                });
                MenuItem menu_RemoveAll = new MenuItem(I18N.get("menu.remove_all"));
                menu_RemoveAll.setOnAction(event1 -> {
                    tableData.clear();
                });
                tableViewMain.setContextMenu(new ContextMenu(menu_Copy, menu_Remove, menu_RemoveAll));
            }
        });
        anonymousLoginEnabledCheckBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    anonymousLoginEnabledTextField.setDisable(false);
                    anonymousLoginEnabledButton.setDisable(false);
                }
                else {
                    anonymousLoginEnabledTextField.setDisable(true);
                    anonymousLoginEnabledButton.setDisable(true);
                }
            }
        });
    }

    private void initService() {
    }

    @FXML
    private void chooseHomeDirectoryAction(ActionEvent event) {
        File file = FileChooserUtil.chooseDirectory();
        if (file != null) {
            homeDirectoryTextField.setText(file.getPath());
        }
    }

    @FXML
    private void anonymousLoginEnabledAction(ActionEvent event) {
        File file = FileChooserUtil.chooseDirectory();
        if (file != null) {
            anonymousLoginEnabledTextField.setText(file.getPath());
        }
    }

    @FXML
    private void addItemAction(ActionEvent event) {
        tableData.add(new FtpServerTableBean(true, userNameTextField.getText(), passwordTextField.getText(),
            homeDirectoryTextField.getText(), downFileCheckBox.isSelected(), upFileCheckBox.isSelected(),
            deleteFileCheckBox.isSelected()));
    }

    @FXML
    private void saveConfigure(ActionEvent event) throws Exception {
        ftpServerService.saveConfigure();
    }

    @FXML
    private void otherSaveConfigureAction(ActionEvent event) throws Exception {
        ftpServerService.otherSaveConfigureAction();
    }

    @FXML
    private void loadingConfigureAction(ActionEvent event) {
        ftpServerService.loadingConfigureAction();
    }

    @FXML
    private void deleteSelectRowAction(ActionEvent event) {
        tableData.remove(tableViewMain.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void startAction(ActionEvent event) {
        if (!serverRunning) {
            try {
                boolean isTrue = ftpServerService.runFtpServerAction();
                if (isTrue) {
                    serverRunning = true;
                    startButton.setText(I18N.get("button.stop"));
                }
                TooltipUtil.showToast(getMainAnchorPane(), I18N.get("msg.start_ok"));
            }
            catch (Exception e) {
                TooltipUtil.showToast(getMainAnchorPane(), I18N.get("msg.start_fail", e.getMessage()));
                log.error("Start failed", e);
            }
        }
        else {
            boolean isTrue = ftpServerService.stopFtpServerAction();
            if (isTrue) {
                serverRunning = false;
                startButton.setText(I18N.get("button.start"));
            }
        }
    }

    /**
     * 父控件被移除前调用
     */
    public void onCloseRequest(Event event) throws Exception {
        ftpServerService.stopFtpServerAction();
    }

    public void dispose() {
        serverRunning = false;
        try {
            ftpServerService.stopFtpServerAction();
        } catch (RuntimeException e) {
            log.error("停止 FTP 服务失败", e);
        }
    }
}
