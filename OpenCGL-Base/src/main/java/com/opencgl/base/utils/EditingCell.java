package com.opencgl.base.utils;

import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

@SuppressWarnings("unused")
public class EditingCell<T> extends TableCell<T, String> {
    private TextField textField;
    private boolean explicitCancel;
    @Override
    public void startEdit() {
        if (!isEmpty()) {
            super.startEdit();
            explicitCancel = false;
            createTextField();
            setText(null);
            setGraphic(textField);
            textField.selectAll();
        }
    }
    @Override
    public void cancelEdit() {
        if (isEditing() && !explicitCancel) {
            commitEdit(textField.getText());
            return;
        }
        super.cancelEdit();
        explicitCancel = false;
        setText(getItem());
        setGraphic(null);
    }
    @Override
    public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            if (isEditing()) {
                if (textField != null) {
                    textField.setText(getString());
                }
                setText(null);
                setGraphic(textField);
            } else {
                setText(getString());
                setGraphic(null);
            }
        }
    }
    private void createTextField() {
        textField = new TextField(getString());
        textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
        textField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                explicitCancel = true;
                cancelEdit();
                event.consume();
            }
        });
        textField.focusedProperty().addListener((ob, old, now) -> {
            if (!now && isEditing() && !explicitCancel) {
                commitEdit(textField.getText());
            }
        });
    }

    private String getString() {
        return getItem() == null ? "" : getItem();
    }
}
