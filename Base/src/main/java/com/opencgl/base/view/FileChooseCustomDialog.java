package com.opencgl.base.view;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.StringUtils;

import com.opencgl.base.utils.i18n.BASE18N;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.enums.ButtonType;
import io.github.palexdev.materialfx.enums.FloatMode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2024/01/31 22:56
 * @since v9.0
 */
@SuppressWarnings("unused")
public class FileChooseCustomDialog extends Dialog<String> {

    private final MFXButton confirmButton = new MFXButton(BASE18N.getOrDefault("opencgl.base.button.confirm"));

    private final MFXButton cancelButton = new MFXButton(BASE18N.getOrDefault("opencgl.base.button.cancel"));

    private final MFXButton chooseFileButton = new MFXButton(BASE18N.getOrDefault("opencgl.base.button.chooseFile"));

    private final MFXTextField textField = new MFXTextField();


    public FileChooseCustomDialog() {
        super();
        initCustomDialog();
    }

    @SuppressWarnings("DuplicatedCode")
    private void initCustomDialog() {
        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getStylesheets().setAll(Objects.requireNonNull(this.getClass().getResource("/com/opencgl/base/css/opencgl-dialog.css")).toExternalForm());
        getDialogPane().getStyleClass().setAll("opencgl-dialog");

        Label label = new Label(BASE18N.getOrDefault("opencgl.base.dialog.file.labelHeader"));
        HBox headerHBox = new HBox(label);
        textField.setFloatMode(FloatMode.ABOVE);
        textField.setPromptText(BASE18N.getOrDefault("opencgl.base.dialog.file.labelHeader"));
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (StringUtils.isEmpty(newValue)) {
                textField.setStyle("-fx-border-color: red;");
            }
            else {
                textField.setStyle("");
            }
        });
        chooseFileButton.setButtonType(ButtonType.RAISED);

        getDialogPane().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
            else if (event.getCode() == KeyCode.ESCAPE) {
                cancelButton.fire();
            }
        });
        HBox textHBox = new HBox();
        textHBox.setSpacing(10.0);
        textHBox.getChildren().addAll(textField, chooseFileButton);

        confirmButton.setButtonType(ButtonType.RAISED);
        confirmButton.setOnAction(event -> {
            if (textField.getText().isEmpty() || textField.getText().trim().isEmpty()) {
                textField.setStyle("-fx-border-color: red;");
                return;
            }
            setResult(textField.getText());
            close();
        });

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
        vBox.setSpacing(20);
        vBox.setPadding(new Insets(10));
        // END VBox
        getDialogPane().setContent(vBox);
        Window window = Stage.getWindows().get(0);
        this.initOwner(window);
        this.setOnShown(event -> Platform.runLater(() -> {
            this.setX(window.getX() + (window.getWidth() - this.getDialogPane().getWidth()) / 2);
            // 位置稍微高一点，使用者视觉效果可能会好一点
            this.setY(window.getY() + (window.getHeight() - this.getDialogPane().getHeight() - 100) / 2);
        }));
    }

    public String alertImportDialog() {
        FileChooser fileChooser = new FileChooser();
        chooseFileButton.setOnAction(actionEvent -> {
            Stage fileChooseStage = new Stage();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("json","*.json"));
            fileChooser.setTitle(BASE18N.getOrDefault("opencgl.base.dialog.file.labelHeader"));
            File file = fileChooser.showOpenDialog(fileChooseStage);
            textField.setText(file.getAbsolutePath());
        });
        return this.showAndWait().get();
    }

    public String alertExportDialog() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        chooseFileButton.setOnAction(actionEvent -> {
            Stage fileChooseStage = new Stage();
            directoryChooser.setTitle(BASE18N.getOrDefault("opencgl.base.dialog.file.labelHeader"));
            File file = directoryChooser.showDialog(fileChooseStage);
            textField.setText(file.getAbsolutePath());
        });
        return this.showAndWait().get();
    }
}
