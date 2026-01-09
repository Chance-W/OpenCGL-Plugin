package com.opencgl.controller;

import org.apache.commons.lang.StringUtils;
import org.greenrobot.eventbus.EventBus;

import com.jfoenix.controls.JFXAlert;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.validation.RequiredFieldValidator;
import com.opencgl.model.DialogStyleDto;
import com.opencgl.model.OperateTypeEnum;
import com.opencgl.soap.i18n.I18N;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;


/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DialogController {

    public static void dialog(EventBus eventBus, DialogStyleDto dialogStyleDto, Node node) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
        Label header = new Label(I18N.get("dialog.enter_name"));
        layout.setHeading(header);
        TextField TextField = new TextField();
        layout.setBody(TextField);
        RequiredFieldValidator message = new RequiredFieldValidator(I18N.get("dialog.enter_name_required"));
        TextField.setText(dialogStyleDto.getText());
        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm"));
        JFXButton cancelButton = new JFXButton(I18N.get("dialog.cancel"));
        cancelButton.getStyleClass().add("dialog-cancle");
        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(event -> {
            if ("".equals(TextField.getText()) || TextField.getText() == null) {
                return;
            }
            alert.hideWithAnimation();
            dialogStyleDto.setText(TextField.getText());
            eventBus.post(dialogStyleDto);
        });


        cancelButton.setOnAction(actionEvent -> alert.hideWithAnimation());
        layout.setActions(cancelButton, confirmButton);
        layout.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
        });
        alert.setContent(layout);
        alert.show();
    }

    public static void secondConfirm(EventBus eventBus, DialogStyleDto dialogStyleDto, Node node) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
        Label header = new Label(I18N.get("dialog.notice"));
        layout.setHeading(header);
        Label label = new Label(I18N.get("dialog.confirm_irreversible"));
        layout.setBody(label);
        JFXButton cancleButton = new JFXButton(I18N.get("dialog.cancel"));
        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm"));
        cancleButton.getStyleClass().add("dialog-cancle");
        cancleButton.setOnAction(event -> alert.hideWithAnimation());
        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(event -> {
            alert.hideWithAnimation();
            eventBus.post(dialogStyleDto);
        });
        layout.setActions(cancleButton, confirmButton);
        alert.setContent(layout);
        alert.show();
    }

    public static void errorInfo(Node node, String text) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
        Label header = new Label(I18N.get("dialog.error"));
        layout.setHeading(header);
        Label label = new Label(text);
        layout.setBody(label);
        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm_btn"));

        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(event -> alert.hideWithAnimation());
        layout.setActions(confirmButton);
        alert.setContent(layout);
        alert.show();
    }


    public static void detailInfo(Node node, String text, JFXAlert<?> topAlert) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        alert.setSize(700, 500);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
        Label header = new Label(I18N.get("dialog.reg_detail"));
        layout.setHeading(header);
        layout.setFillWidth(true);
        Label label = new Label(text);
        layout.setBody(label);
        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm"));
        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(event -> {
            alert.hideWithAnimation();
            topAlert.hideWithAnimation();
        });
        layout.setActions(confirmButton);
        alert.setContent(layout);
        alert.show();
    }


    public static void showDetail(String text, Node node) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
        Label header = new Label(I18N.get("dialog.wsdl_detail"));
        layout.setHeading(header);
        TextField label = new TextField(text);
        layout.setBody(label);
        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm"));
        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(actionEvent -> alert.hideWithAnimation());
        layout.setActions(confirmButton);
        layout.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
        });
        alert.setContent(layout);
        alert.show();
    }


    public static void showText(Node node, String text) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
       /* Label header = new Label("WSDL地址详情");
        layout.setHeading(header);*/
        TextField label = new TextField(text);
        layout.setBody(label);
        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm"));
        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(actionEvent -> alert.hideWithAnimation());
        layout.setActions(confirmButton);
        layout.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
        });
        alert.setContent(layout);
        alert.show();
    }

    public static void showTextArea(EventBus eventBus, String text, Node node) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        JFXTextArea jfxTextArea = new JFXTextArea();
        jfxTextArea.setText(text);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
        Label header = new Label(I18N.get("dialog.request_response"));
        layout.setHeading(header);
        layout.setFillWidth(true);
        layout.setBody(jfxTextArea);
        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm"));
        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(event -> {
            eventBus.post(DialogStyleDto.builder().text(jfxTextArea.getText()).build());
            alert.hideWithAnimation();
        });
        JFXButton cancelButton = new JFXButton(I18N.get("dialog.cancel"));
        cancelButton.getStyleClass().add("dialog-cancle");
        cancelButton.setOnAction(actionEvent -> alert.hideWithAnimation());
        layout.setActions(cancelButton, confirmButton);
        layout.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
        });
        layout.setPrefHeight(400);
        layout.setPrefWidth(500);
        jfxTextArea.setMinHeight(layout.getPrefHeight());
        jfxTextArea.setMinWidth(layout.getPrefWidth());
        alert.setContent(layout);
        alert.show();
    }

    public static void showTextArea2(EventBus eventBus, String text, Node node) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        JFXTextArea jfxTextArea = new JFXTextArea();
        jfxTextArea.setText(text);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
        Label header = new Label(I18N.get("dialog.request_response"));
        layout.setHeading(header);
        layout.setFillWidth(true);
        layout.setBody(jfxTextArea);
        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm"));
        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(event -> {
            eventBus.post(DialogStyleDto.builder().type(OperateTypeEnum.CREATE).text(jfxTextArea.getText()).build());
            alert.hideWithAnimation();
        });
        JFXButton cancelButton = new JFXButton(I18N.get("dialog.cancel"));
        cancelButton.getStyleClass().add("dialog-cancle");
        cancelButton.setOnAction(actionEvent -> alert.hideWithAnimation());
        layout.setActions(cancelButton, confirmButton);
        layout.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
        });
        layout.setPrefHeight(400);
        layout.setPrefWidth(500);
        jfxTextArea.setMinHeight(layout.getPrefHeight());
        jfxTextArea.setMinWidth(layout.getPrefWidth());
        alert.setContent(layout);
        alert.show();
    }

    public static void webServiceConfigure(EventBus eventBus, DialogStyleDto dialogStyleDto, Node node) {
        JFXAlert<?> alert = new JFXAlert<>(node.getScene().getWindow());
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setOverlayClose(false);
        JFXDialogLayout layout = new JFXDialogLayout();
        Label header = new Label(I18N.get("dialog.webservice_config"));
        layout.setHeading(header);
        VBox bodyVbox = new VBox();
        bodyVbox.setSpacing(30);
        TextField projectNameSettingTextField = new TextField();
        projectNameSettingTextField.setPromptText(I18N.get("dialog.project_name_prompt"));
        TextField projectUrlSettingTextField = new TextField();
        projectUrlSettingTextField.setPromptText(I18N.get("dialog.project_url_prompt"));
        bodyVbox.getChildren().addAll(projectNameSettingTextField, projectUrlSettingTextField);
        layout.setBody(bodyVbox);
      /*  RequiredFieldValidator nameMessage = new RequiredFieldValidator("项目名称设置不能为空..");
        RequiredFieldValidator urlMessage = new RequiredFieldValidator("项目url地址配置不能为空..");
        projectNameSettingTextField.setValidators(nameMessage);
        projectUrlSettingTextField.setValidators(urlMessage);*/
        if (!StringUtils.isEmpty(dialogStyleDto.getText())) {
            projectNameSettingTextField.setText(dialogStyleDto.getText().split(",")[0]);
            projectUrlSettingTextField.setText(dialogStyleDto.getText().split(",")[1]);
        }
       /* projectNameSettingTextField.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (!newVal) {
                projectNameSettingTextField.validate();
            }
        });
        projectUrlSettingTextField.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (!newVal) {
                projectUrlSettingTextField.validate();
            }
        });*/

        JFXButton confirmButton = new JFXButton(I18N.get("dialog.confirm"));
        JFXButton cancelButton = new JFXButton(I18N.get("dialog.cancel"));
        cancelButton.getStyleClass().add("dialog-cancle");
        confirmButton.getStyleClass().add("dialog-accept");
        confirmButton.setOnAction(event -> {
            if (projectNameSettingTextField.getText().isEmpty() || projectNameSettingTextField.getText() == null) {
                return;
            }
            if (projectUrlSettingTextField.getText().isEmpty() || projectUrlSettingTextField.getText() == null) {
                return;
            }
            alert.hideWithAnimation();
            dialogStyleDto.setType(OperateTypeEnum.CREATE);
            dialogStyleDto.setText(projectNameSettingTextField.getText() + "," + projectUrlSettingTextField.getText());
            eventBus.post(dialogStyleDto);
        });


        cancelButton.setOnAction(actionEvent -> alert.hideWithAnimation());
        layout.setActions(cancelButton, confirmButton);
        layout.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
        });
        alert.setContent(layout);
        alert.show();
    }

}
