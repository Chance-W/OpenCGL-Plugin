package com.opencgl.base.utils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * 监听ComboBox，自动完成搜索功能
 *
 * @author Chance.w
 * @date 2020-01-14 20:45:53
 **/
@SuppressWarnings("unused")
public class AutoCompleteComboBoxListenerUtil implements EventHandler<KeyEvent> {

    private ComboBox<Object> comboBox;

    private ObservableList<Object> data;
    private boolean moveCaretToPos = false;
    private int caretPos;

    public AutoCompleteComboBoxListenerUtil(ComboBox<Object> comboBox) {
        this.comboBox = comboBox;
        data = comboBox.getItems();
        this.comboBox.setEditable(true);
        this.comboBox.setOnKeyPressed(t -> comboBox.hide());
        this.comboBox.setOnKeyReleased(AutoCompleteComboBoxListenerUtil.this);
    }

    @Override
    public void handle(KeyEvent event) {

        if (event.getCode() == KeyCode.UP) {
            caretPos = -1;
            moveCaret(comboBox.getEditor().getText().length());
            return;
        } else if (event.getCode() == KeyCode.DOWN) {
            if (!comboBox.isShowing()) {
                comboBox.show();
            }
            caretPos = -1;
            moveCaret(comboBox.getEditor().getText().length());
            return;
        } else if (event.getCode() == KeyCode.BACK_SPACE) {
            moveCaretToPos = true;
            caretPos = comboBox.getEditor().getCaretPosition();
        } else if (event.getCode() == KeyCode.DELETE) {
            moveCaretToPos = true;
            caretPos = comboBox.getEditor().getCaretPosition();
        } else if (event.getCode() == KeyCode.ENTER) {
            int i = comboBox.getSelectionModel().getSelectedIndex();
            comboBox.getSelectionModel().select(i);
        }

        if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.LEFT
                || event.isControlDown() || event.getCode() == KeyCode.HOME
                || event.getCode() == KeyCode.END || event.getCode() == KeyCode.TAB) {
            return;
        }


        ObservableList<Object> list = FXCollections.observableArrayList();
        for (Object datum : data) {
            if (datum.toString().toLowerCase().contains(
                    AutoCompleteComboBoxListenerUtil.this.comboBox
                            .getEditor().getText().toLowerCase())) {
                list.add(datum);
            }
        }
        String t = comboBox.getEditor().getText();

        comboBox.setItems(list);

        comboBox.getEditor().setText(t);

        if (!moveCaretToPos) {
            caretPos = -1;
        }
        moveCaret(t.length());
        if (!list.isEmpty()) {
            comboBox.show();
        }
    }

    private void moveCaret(int textLength) {
        if (caretPos == -1) {
            comboBox.getEditor().positionCaret(textLength);
        } else {
            comboBox.getEditor().positionCaret(caretPos);
        }
        moveCaretToPos = false;
    }
}