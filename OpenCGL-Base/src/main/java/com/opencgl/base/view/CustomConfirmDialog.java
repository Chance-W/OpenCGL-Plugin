package com.opencgl.base.view;


import java.util.Objects;

import com.opencgl.base.theme.ThemeManager;
import com.opencgl.base.utils.i18n.BaseI18N;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.enums.ButtonType;
import javafx.application.Platform;
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
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/13 22:56
 * @since v9.0
 */
@SuppressWarnings({"unused", "DuplicatedCode"})
public class CustomConfirmDialog extends Dialog<Boolean> {

    private final Label labelHeader = new Label(BaseI18N.getOrDefault("opencgl.base.info.dialog.labelHeader"));

    private final Label labelText = new Label(BaseI18N.getOrDefault("opencgl.base.info.dialog.labelText"));

    public CustomConfirmDialog() {
        super();
        initCustomDialog();
    }

    private void initCustomDialog() {
        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getStyleClass().addFirst("root");
        getDialogPane().getStyleClass().add("opencgl-dialog");
        
        // 注册到 ThemeManager 以支持主题切换
        this.setOnShown(event -> {
            Scene scene = getDialogPane().getScene();
            if (scene != null) {
                ThemeManager.getInstance().registerScene(scene);
            }
        });
        
        // 当对话框关闭时注销
        this.setOnHidden(event -> {
            Scene scene = getDialogPane().getScene();
            if (scene != null) {
                ThemeManager.getInstance().unregisterScene(scene);
            }
        });
        VBox alertVBox = new VBox();
        alertVBox.setMinWidth(400.0);
        alertVBox.setMinHeight(150.0);
        VBox.setVgrow(alertVBox, Priority.ALWAYS);
        alertVBox.setSpacing(30);

        MFXButton confirmButton = new MFXButton(BaseI18N.getOrDefault("opencgl.base.button.confirm"));
        MFXButton cancelButton = new MFXButton(BaseI18N.getOrDefault("opencgl.base.button.cancel"));

        confirmButton.setButtonType(ButtonType.RAISED);
        confirmButton.setOnAction(event -> {
            Scene scene = this.getDialogPane().getScene();
            Stage stage = (Stage) scene.getWindow();
            stage.close();
            setResult(true);
        });

        cancelButton.setButtonType(ButtonType.RAISED);
        cancelButton.setOnAction(event -> {
            Scene scene = this.getDialogPane().getScene();
            Stage stage = (Stage) scene.getWindow();
            stage.close();
            setResult(false);
        });

        HBox buttonHBox = new HBox();
        buttonHBox.setAlignment(Pos.CENTER_RIGHT);
        buttonHBox.getChildren().addAll(confirmButton, cancelButton);
        buttonHBox.setPadding(new Insets(50.0, 0, 0, 0));
        buttonHBox.setSpacing(20);
        alertVBox.getChildren().addAll(labelHeader, labelText, buttonHBox);

        // END VBox
        getDialogPane().setContent(alertVBox);
        getDialogPane().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmButton.fire();
            }
        });
        getDialogPane().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancelButton.fire();
            }
        });

        this.setOnShown(event -> {
            Scene scene = getDialogPane().getScene();
            if (scene != null) {
                ThemeManager.getInstance().registerScene(scene);
            }
            // 居中定位到当前活跃屏幕，解决多屏下弹窗出现在主屏的问题
            centerOnActiveWindow(this);
        });

    }

    /**
     * 将弹窗居中定位到当前聚焦的 Stage 所在屏幕位置。
     * 解决多屏环境下弹窗出现在主屏而非当前操作屏幕的问题。
     */
    private static void centerOnActiveWindow(Dialog<?> dialog) {
        Platform.runLater(() -> {
            Window owner = Stage.getWindows().stream()
                .filter(w -> w instanceof Stage && ((Stage) w).isFocused())
                .findFirst()
                .orElseGet(() -> Stage.getWindows().isEmpty() ? null : Stage.getWindows().get(0));
            if (owner == null) return;

            double dialogW = dialog.getDialogPane().getWidth();
            double dialogH = dialog.getDialogPane().getHeight();

            double x = owner.getX() + (owner.getWidth() - dialogW) / 2;
            double y = owner.getY() + (owner.getHeight() - dialogH - 100) / 2;

            // 找到 owner 所在的屏幕，做边界 clamp，避免低分辨率下弹窗超出屏幕
            javafx.geometry.Rectangle2D sb = Screen.getScreensForRectangle(
                owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight()
            ).stream().findFirst().map(javafx.stage.Screen::getVisualBounds)
                .orElseGet(() -> Screen.getPrimary().getVisualBounds());

            x = Math.max(sb.getMinX(), Math.min(x, sb.getMaxX() - dialogW));
            y = Math.max(sb.getMinY(), Math.min(y, sb.getMaxY() - dialogH));

            dialog.setX(x);
            dialog.setY(y);
        });
    }

    public void setLabelText(String value) {
        labelText.setText(value);
    }

    public void setCustomHeaderText(String value) {
        labelHeader.setText(value);
    }
}

