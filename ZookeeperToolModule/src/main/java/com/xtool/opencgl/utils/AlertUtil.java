package com.xtool.opencgl.utils;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.Optional;

public class AlertUtil {
    public AlertUtil() {
    }

    public static void showInfoAlert(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setContentText(message);
        alert.show();
    }

    public static void showInfoAlert(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.show();
    }

    public static void showAndWaitInfoAlert(String title,String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();

    }

    public static void showWarnAlert(String message) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setContentText(message);
        alert.show();
    }

    public static void showErrorAlert(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setContentText(message);
        alert.show();
    }

    public static boolean showConfirmAlert(String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setContentText(message);
        Optional<ButtonType> optional = alert.showAndWait();
        return ButtonType.OK == optional.get();
    }

    public static String showInputAlert(String message) {
        TextField textField = new TextField();
        Alert alert = new Alert(AlertType.NONE, (String)null, new ButtonType[]{new ButtonType("取消", ButtonData.NO), new ButtonType("确定", ButtonData.YES)});
        alert.setTitle(message);
        alert.setGraphic(textField);
        alert.setWidth(200.0D);
        Optional<ButtonType> _buttonType = alert.showAndWait();
        return ((ButtonType)_buttonType.get()).getButtonData().equals(ButtonData.YES) ? textField.getText() : null;
    }

    public static String[] showInputAlert(String message, String... names) {
        int row = 0;
        GridPane page1Grid = new GridPane();
        page1Grid.setVgap(10.0D);
        page1Grid.setHgap(10.0D);
        TextField[] textFields = new TextField[names.length];
        String[] var5 = names;
        int var6 = names.length;

        for(int var7 = 0; var7 < var6; ++var7) {
            String string = var5[var7];
            TextField textField = new TextField();
            GridPane.setHgrow(textField, Priority.ALWAYS);
            page1Grid.add(new Label(string), 0, row);
            page1Grid.add(textField, 1, row);
            textFields[row] = textField;
            ++row;
        }

        Alert alert = new Alert(AlertType.NONE, (String)null, new ButtonType[]{new ButtonType("取消", ButtonData.NO), new ButtonType("确定", ButtonData.YES)});
        alert.setTitle(message);
        alert.setGraphic(page1Grid);
        alert.setWidth(200.0D);
        Optional<ButtonType> _buttonType = alert.showAndWait();
        if (!((ButtonType)_buttonType.get()).getButtonData().equals(ButtonData.YES)) {
            return null;
        } else {
            String[] stringS = new String[names.length];

            for(int i = 0; i < textFields.length; ++i) {
                stringS[i] = textFields[i].getText();
            }

            return stringS;
        }
    }
}
