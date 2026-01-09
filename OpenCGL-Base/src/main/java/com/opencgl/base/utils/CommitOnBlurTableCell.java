package com.opencgl.base.utils;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

import java.util.function.Predicate;

/**
 * Editable table cell that commits its editor value before a TableView-driven
 * focus change cancels the edit. Escape remains an explicit cancel action.
 */
public final class CommitOnBlurTableCell<S, T> extends TextFieldTableCell<S, T> {
    private final StringConverter<T> converter;
    private final Predicate<S> editableRow;
    private TextField editor;
    private boolean explicitCancel;

    private CommitOnBlurTableCell(StringConverter<T> converter, Predicate<S> editableRow) {
        super(converter);
        this.converter = converter;
        this.editableRow = editableRow;
    }

    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> forStringColumn() {
        return forStringColumn(row -> true);
    }

    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> forStringColumn(
            Predicate<S> editableRow) {
        return column -> new CommitOnBlurTableCell<>(new DefaultStringConverter(), editableRow);
    }

    @Override
    public void startEdit() {
        S row = getTableRow() == null ? null : getTableRow().getItem();
        if (row == null || !editableRow.test(row)) return;

        explicitCancel = false;
        super.startEdit();
        if (isEditing() && getGraphic() instanceof TextField currentEditor && currentEditor != editor) {
            editor = currentEditor;
            editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) explicitCancel = true;
            });
            editor.focusedProperty().addListener((observable, wasFocused, focused) -> {
                if (wasFocused && !focused && isEditing() && !explicitCancel) commitEditorValue();
            });
        }
    }

    @Override
    public void cancelEdit() {
        if (isEditing() && !explicitCancel) {
            commitEditorValue();
            return;
        }
        super.cancelEdit();
        explicitCancel = false;
    }

    private void commitEditorValue() {
        if (editor == null || !isEditing()) return;
        try {
            commitEdit(converter.fromString(editor.getText()));
        } catch (RuntimeException conversionError) {
            explicitCancel = true;
            super.cancelEdit();
        }
    }
}
