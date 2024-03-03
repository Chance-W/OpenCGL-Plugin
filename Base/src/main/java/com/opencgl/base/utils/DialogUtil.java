package com.opencgl.base.utils;

import java.util.Optional;

import com.opencgl.base.utils.i18n.BASE18N;
import com.opencgl.base.view.CustomConfirmDialog;
import com.opencgl.base.view.CustomDialog;
import com.opencgl.base.view.CustomInfoDialog;
import com.opencgl.base.view.FileChooseCustomDialog;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

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
        // customDialog.showAndWait().get();
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
        customConfirmDialog.setHeaderText(BASE18N.getOrDefault("opencgl.base.button.confirmInfo"));
        customConfirmDialog.setLabelText(BASE18N.getOrDefault("opencgl.base.button.confirm.delete"));
        Optional<Boolean> value = customConfirmDialog.showAndWait();
        return value.orElse(false);
        //  return customConfirmDialog.showAndWait().get();
    }

    public static String showImportDialog() {
        FileChooseCustomDialog fileChooseCustomDialog = new FileChooseCustomDialog();
        return fileChooseCustomDialog.alertImportDialog();
    }

    public static String showExportDialog() {
        FileChooseCustomDialog fileChooseCustomDialog = new FileChooseCustomDialog();
        return fileChooseCustomDialog.alertExportDialog();
    }

}
