package com.opencgl.base.view;

import java.util.Objects;

import org.apache.commons.lang.StringUtils;

import com.opencgl.base.utils.i18n.BaseI18N;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.enums.ButtonType;
import io.github.palexdev.materialfx.enums.FloatMode;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/13 22:56
 * @since v9.0
 */
@SuppressWarnings("unused")
public class CustomDialog extends Dialog<String> {

    private MFXButton confirmButton;

    private MFXButton cancelButton;

    private MFXTextField textField = null;

    public CustomDialog() {
        super();
        initCustomDialog();
    }

    @SuppressWarnings("DuplicatedCode")
    private void initCustomDialog() {
        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
//        getDialogPane().getStylesheets().setAll(Objects.requireNonNull(this.getClass().getResource("/com/opencgl/base/css/opencgl-dialog.css")).toExternalForm());
//        getDialogPane().getStyleClass().setAll("opencgl-dialog");

        Label label = new Label(BaseI18N.getOrDefault("opencgl.base.dialog.labelHeader"));
        HBox headerHBox = new HBox(label);
        textField = new MFXTextField();
        textField.setFloatMode(FloatMode.ABOVE);
        textField.setPromptText(BaseI18N.getOrDefault("opencgl.base.dialog.labelHeader"));
        textField.setMinWidth(420);
        textField.setPrefWidth(420);
        textField.setPrefColumnCount(28);
        textField.setMaxWidth(Double.MAX_VALUE);
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (StringUtils.isEmpty(newValue)) {
                textField.setStyle("-fx-border-color: red;");
            }
            else {
                textField.setStyle("");
            }
        });
        getDialogPane().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
            else if (event.getCode() == KeyCode.ESCAPE) {
                cancelButton.fire();
            }
        });
        HBox textHBox = new HBox();
        textHBox.getChildren().add(textField);
        HBox.setHgrow(textField, Priority.ALWAYS);

        confirmButton = new MFXButton(BaseI18N.getOrDefault("opencgl.base.button.confirm"));
        confirmButton.setButtonType(ButtonType.RAISED);
        confirmButton.setOnAction(event -> {
            if (textField.getText().isEmpty() || textField.getText().trim().isEmpty()) {
                textField.setStyle("-fx-border-color: red;");
                return;
            }
            setResult(textField.getText());
            close();
        });

        cancelButton = new MFXButton(BaseI18N.getOrDefault("opencgl.base.button.cancel"));
        cancelButton.setButtonType(ButtonType.RAISED);
        cancelButton.setOnAction(event -> {
            setResult("");
            close();
        });

        HBox buttonHBox = new HBox();
        buttonHBox.setAlignment(Pos.CENTER_RIGHT);
        buttonHBox.setSpacing(10.0);
        buttonHBox.getChildren().addAll(confirmButton, cancelButton);

        VBox vBox = new VBox();
        vBox.getChildren().addAll(headerHBox, textHBox, buttonHBox);
        vBox.setSpacing(24);
        vBox.setPadding(new Insets(24));
        vBox.setMinWidth(480);
        // END VBox
        getDialogPane().setContent(vBox);
        getDialogPane().setMinWidth(500);
        getDialogPane().setPrefWidth(500);
        Window window = Stage.getWindows().get(0);
        this.initOwner(window);
        this.setOnShown(event -> Platform.runLater(() -> {
            Window win = getDialogPane().getScene() != null ? getDialogPane().getScene().getWindow() : null;
            if (win == null) return;
            double paneW = getDialogPane().getWidth() > 0 ? getDialogPane().getWidth() : getDialogPane().getPrefWidth();
            double paneH = getDialogPane().getHeight() > 0 ? getDialogPane().getHeight() : getDialogPane().getPrefHeight();
            if (paneW <= 0) paneW = 500;
            if (paneH <= 0) paneH = 200;
            win.setX(window.getX() + (window.getWidth() - paneW) / 2);
            win.setY(window.getY() + (window.getHeight() - paneH - 100) / 2);
        }));
    }

    public void setConfirmOnAction(EventHandler<ActionEvent> value) {
        confirmButton.setOnAction(value);
    }

    public void setCancelOnAction(EventHandler<ActionEvent> value) {
        cancelButton.setOnAction(value);
    }

    public void setTextField(String value) {
        Platform.runLater(() -> textField.setText(value));
    }

    public void resetTextField() {
       Platform.runLater(() -> textField.clear());
    }

}
