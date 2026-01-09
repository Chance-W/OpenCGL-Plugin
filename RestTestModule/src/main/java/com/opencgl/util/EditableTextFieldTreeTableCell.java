package com.opencgl.util;

import org.apache.commons.lang.StringUtils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.control.TreeTableCell;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2024/02/12 00:08
 * @since v9.0
 */


public class EditableTextFieldTreeTableCell<S, T> extends TreeTableCell<S, T> {
    private final TextField textField;
    private final BooleanProperty editing = new SimpleBooleanProperty(false);
    private boolean explicitCancel;

    public EditableTextFieldTreeTableCell() {
        textField = new TextField();
        textField.setOnAction(event -> commitEdit((T) textField.getText()));
        textField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                explicitCancel = true;
                cancelEdit();
                event.consume();
            }
        });
        textField.focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue && shouldCommitOnFocusLost(isEditing(), explicitCancel)) {
                commitEdit((T) textField.getText());
            }
        });
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (isEmpty()) {
            return;
        }
        editing.set(true);
        explicitCancel = false;
        setText(null);
        setGraphic(textField);
        if (StringUtils.isNotEmpty(getString())){
            textField.setText(getString());
        }
        textField.requestFocus();
    }

    @Override
    public void commitEdit(T newValue) {
        super.commitEdit(newValue);
        editing.set(false);
    }

    @Override
    public void cancelEdit() {
        if (isEditing() && !explicitCancel) {
            commitEdit((T) textField.getText());
            return;
        }
        super.cancelEdit();
        setText(textField.getText());
        setGraphic(null);
        editing.set(false);
        explicitCancel = false;
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        }
        else {
            if (isEditing()) {
                textField.setText(getString());
                setText(null);
                setGraphic(textField);
            }
            else {
                setText(getString());
                setGraphic(null);
            }
        }
    }

    private String getString() {
        return getItem() == null ? "" : getItem().toString();
    }

    static boolean shouldCommitOnFocusLost(boolean editing, boolean explicitlyCancelled) {
        return editing && !explicitlyCancelled;
    }
}
