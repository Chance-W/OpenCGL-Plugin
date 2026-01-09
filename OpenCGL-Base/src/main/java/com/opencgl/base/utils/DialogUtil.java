package com.opencgl.base.utils;

import java.util.Optional;

import com.opencgl.base.utils.i18n.BaseI18N;
import com.opencgl.base.view.CustomConfirmDialog;
import com.opencgl.base.view.CustomDialog;
import com.opencgl.base.view.CustomInfoDialog;
import com.opencgl.base.view.FileChooseCustomDialog;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Dialog;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/16 13:39
 * @since v9.0
 */
@SuppressWarnings("unused")
public class DialogUtil {

    private static final CustomInfoDialog customInfoDialog = new CustomInfoDialog();

    private static final CustomConfirmDialog customConfirmDialog = new CustomConfirmDialog();

    public static String show() {
        CustomDialog customDialog = new CustomDialog();
        Optional<String> value = customDialog.showAndWait();
        return value.orElse("");
        //return customDialog.showAndWait().get();
    }

    public static String show(String text) {
        CustomDialog customDialog = new CustomDialog();
        customDialog.setTextField(text);
        Optional<String> value = customDialog.showAndWait();
        return value.orElse("");
    }

    public static void showSuccessInfo(String text) {
        Platform.runLater(() -> {
            customInfoDialog.setLabelText(text);
            customInfoDialog.showAndWait();
        });
    }

    public static void showSuccessInfo(String text, Stage stage) {
        Platform.runLater(() -> {
            customInfoDialog.setLabelText(text);
            customInfoDialog.showAndWait();
            stage.close();
        });
    }

    public static void showErrorInfo(String text) {
        Platform.runLater(() -> {
            customInfoDialog.setHeaderText("ERROR");
            customInfoDialog.setLabelText(text);
            customInfoDialog.showAndWait();
        });
    }

    public static void showErrorInfo(String text, Stage stage) {
        Platform.runLater(() -> {
            customInfoDialog.setHeaderText("ERROR");
            customInfoDialog.setLabelText(text);
            customInfoDialog.showAndWait();
            stage.toFront();
        });
    }

    public static void showErrorInfo(String text, Pane pane) {
        Platform.runLater(() -> {
            customInfoDialog.setHeaderText("ERROR");
            customInfoDialog.setLabelText(text);
            customInfoDialog.showAndWait();
            pane.toFront();
        });
    }

    public static void showCustomInfo(String headerText, String text) {
        Platform.runLater(() -> {
            customInfoDialog.setHeaderText(headerText);
            customInfoDialog.setLabelText(text);
            customInfoDialog.showAndWait();
        });
    }

    public static Boolean deleteConfirm() {
        customConfirmDialog.setHeaderText(BaseI18N.getOrDefault("opencgl.base.button.confirmInfo"));
        customConfirmDialog.setLabelText(BaseI18N.getOrDefault("opencgl.base.button.confirm.delete"));
        Optional<Boolean> value = customConfirmDialog.showAndWait();
        return value.orElse(false);
        //  return customConfirmDialog.showAndWait().get();
    }
    
    public static Boolean showConfirmDialog(String headerText, String message, Pane pane) {
        customConfirmDialog.setHeaderText(headerText);
        customConfirmDialog.setLabelText(message);
        Optional<Boolean> value = customConfirmDialog.showAndWait();
        if (pane != null) {
            pane.toFront();
        }
        return value.orElse(false);
    }

    public static String showImportDialog() {
        FileChooseCustomDialog fileChooseCustomDialog = new FileChooseCustomDialog();
        return fileChooseCustomDialog.alertImportDialog();
    }

    public static String showExportDialog() {
        FileChooseCustomDialog fileChooseCustomDialog = new FileChooseCustomDialog();
        return fileChooseCustomDialog.alertExportDialog();
    }

    /**
     * 将 JavaFX Dialog 居中到指定 owner 窗口所在屏幕，保证多屏/插件窗口时弹窗与当前操作窗口同屏。
     * 需在 dialog.setOnShown 或 show 之后调用（宽高稳定后）。
     */
    public static void centerDialogOnOwner(Dialog<?> dialog, Window owner) {
        if (owner == null || dialog == null) return;
        Platform.runLater(() -> {
            double dialogW = dialog.getDialogPane().getWidth();
            double dialogH = dialog.getDialogPane().getHeight();
            double x = owner.getX() + (owner.getWidth() - dialogW) / 2;
            double y = owner.getY() + (owner.getHeight() - dialogH - 100) / 2;
            Rectangle2D sb = Screen.getScreensForRectangle(
                    owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight()).stream().findFirst()
                    .map(Screen::getVisualBounds)
                    .orElseGet(() -> Screen.getPrimary().getVisualBounds());
            x = Math.max(sb.getMinX(), Math.min(x, sb.getMaxX() - dialogW));
            y = Math.max(sb.getMinY(), Math.min(y, sb.getMaxY() - dialogH));
            dialog.setX(x);
            dialog.setY(y);
        });
    }

    /**
     * 将 Stage 居中到指定 owner 窗口所在屏幕，保证多屏/插件窗口时弹窗与当前操作窗口同屏。
     * 需在 stage.setOnShown 或 show 之后调用（宽高稳定后）。
     */
    public static void centerStageOnOwner(Stage stage, Window owner) {
        if (owner == null) return;
        Platform.runLater(() -> {
            double stageW = stage.getWidth();
            double stageH = stage.getHeight();
            double x = owner.getX() + (owner.getWidth() - stageW) / 2;
            double y = owner.getY() + (owner.getHeight() - stageH - 100) / 2;
            Rectangle2D sb = Screen.getScreensForRectangle(
                    owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight()).stream().findFirst()
                    .map(Screen::getVisualBounds)
                    .orElseGet(() -> Screen.getPrimary().getVisualBounds());
            x = Math.max(sb.getMinX(), Math.min(x, sb.getMaxX() - stageW));
            y = Math.max(sb.getMinY(), Math.min(y, sb.getMaxY() - stageH));
            stage.setX(x);
            stage.setY(y);
        });
    }

}
