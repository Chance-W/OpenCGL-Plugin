package com.opencgl.base.view;


import java.util.Objects;

import com.opencgl.base.utils.i18n.BaseI18N;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.enums.ButtonType;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
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
public class CustomInfoDialog extends Dialog<Void> {

    private final Label labelHeader = new Label(BaseI18N.getOrDefault("opencgl.base.info.dialog.labelHeader"));

    private final Label labelText = new Label(BaseI18N.getOrDefault("opencgl.base.info.dialog.labelText"));

    public CustomInfoDialog() {
        super();
        initCustomDialog();
    }

    private void initCustomDialog() {
        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
//        getDialogPane().getStylesheets().setAll(Objects.requireNonNull(this.getClass().getResource("/com/opencgl/base/css/opencgl-dialog.css")).toExternalForm());
//        getDialogPane().getStyleClass().addFirst("opencgl-dialog");
        VBox alertVBox = new VBox();
        alertVBox.setMinWidth(400.0);
        alertVBox.setMinHeight(150.0);
        VBox.setVgrow(alertVBox, Priority.ALWAYS);
        alertVBox.setSpacing(30);

        MFXButton confirmButton = new MFXButton(BaseI18N.getOrDefault("opencgl.base.button.confirm"));
        confirmButton.setButtonType(ButtonType.RAISED);
        confirmButton.setOnAction(event -> {
            Scene scene = this.getDialogPane().getScene();
            Stage stage = (Stage) scene.getWindow();
            stage.close();
        });

        HBox buttonHBox = new HBox();
        buttonHBox.setAlignment(Pos.CENTER_RIGHT);
        buttonHBox.getChildren().addAll(confirmButton);
        buttonHBox.setPadding(new Insets(50.0, 0, 0, 0));
        alertVBox.getChildren().addAll(labelHeader, labelText, buttonHBox);

        // END VBox
        getDialogPane().setContent(alertVBox);
        getDialogPane().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.ESCAPE) {
                confirmButton.fire();
            }
        });
        Window window = Stage.getWindows().get(0);
        this.initOwner(window);
        this.setOnShown(event -> Platform.runLater(() -> {
            this.setX(window.getX() + (window.getWidth() - this.getDialogPane().getWidth()) / 2);
            // 位置稍微高一点，使用者视觉效果可能会好一点
            this.setY(window.getY() + (window.getHeight() - this.getDialogPane().getHeight() - 100) / 2);
        }));
    }

    public void setLabelText(String value) {
        labelText.setText(value);
    }

}
