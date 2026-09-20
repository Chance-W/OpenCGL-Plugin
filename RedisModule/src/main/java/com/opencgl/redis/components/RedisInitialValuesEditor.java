package com.opencgl.redis.components;

import com.opencgl.redis.i18n.I18N;
import com.opencgl.redis.model.NewRedisKey;
import com.opencgl.redis.model.RedisKeyInfo.KeyType;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import java.util.List;

/** Collection input only; Redis commands and validation remain in NewRedisKey. */
final class RedisInitialValuesEditor extends VBox {
    private static final class Row {
        final StringProperty first = new SimpleStringProperty("");
        final StringProperty second = new SimpleStringProperty("");
    }
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ListView<Row> list = new ListView<>(rows);
    private final TableView<Row> table = new TableView<>(rows);
    private final TableColumn<Row, String> first = column(true), second = column(false);
    private final Button remove = new Button(I18N.get("newkey.remove"));
    private boolean listMode;

    RedisInitialValuesEditor() {
        super(6);
        list.setId("new-key-list"); table.setId("new-key-table");
        list.setEditable(true); table.setEditable(true);
        list.setFixedCellSize(40); table.setFixedCellSize(40);
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getColumns().addAll(first, second);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        list.setCellFactory(v -> new ListCell<>() {
            private Edit edit;
            @Override protected void updateItem(Row item, boolean empty) {
                super.updateItem(item, empty);
                if (edit != null) { edit.detach(); edit = null; }
                setGraphic(null); setText(empty ? null : item.first.get());
            }
            @Override public void startEdit() {
                if (isEmpty()) return;
                super.startEdit();
                edit = new Edit(getItem().first, this::cancelEdit);
                edit.input.prefWidthProperty().bind(widthProperty().subtract(16));
                setText(null); setGraphic(edit.input); edit.input.requestFocus();
            }
            @Override public void cancelEdit() {
                if (edit != null) { edit.detach(); edit = null; }
                super.cancelEdit(); setGraphic(null); setText(isEmpty() ? null : getItem().first.get());
            }
        });
        list.setPlaceholder(new Label(I18N.get("newkey.empty")));
        table.setPlaceholder(new Label(I18N.get("newkey.empty")));
        Button add = new Button("+ " + I18N.get("newkey.add")); add.setId("new-key-add");
        remove.setId("new-key-remove"); remove.setDisable(true);
        add.getStyleClass().add("secondary-button"); remove.getStyleClass().add("secondary-button");
        add.disableProperty().bind(javafx.beans.binding.Bindings.size(rows).greaterThanOrEqualTo(1000));
        add.setOnAction(e -> {
            Row row = new Row(); rows.add(row);
            if (listMode) { list.getSelectionModel().clearAndSelect(rows.size()-1); list.scrollTo(row); }
            else { table.getSelectionModel().clearAndSelect(rows.size()-1); table.scrollTo(row); }
        });
        remove.setOnAction(e -> {
            list.edit(-1); table.edit(-1, null);
            rows.removeAll(List.copyOf(listMode ? list.getSelectionModel().getSelectedItems() : table.getSelectionModel().getSelectedItems()));
        });
        list.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> updateRemove());
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> updateRemove());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, new Label(I18N.get("newkey.rows")), spacer, add, remove);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(toolbar, list, table);
        list.setPrefHeight(220); table.setPrefHeight(220);
        VBox.setVgrow(list, Priority.ALWAYS); VBox.setVgrow(table, Priority.ALWAYS);
        rows.add(new Row());
    }

    void setType(KeyType type) {
        list.edit(-1); table.edit(-1, null);
        listMode = type == KeyType.LIST || type == KeyType.SET;
        list.setVisible(listMode); list.setManaged(listMode);
        table.setVisible(!listMode); table.setManaged(!listMode);
        first.setText(I18N.get(type == KeyType.ZSET ? "newkey.member" : "newkey.field"));
        second.setText(I18N.get(type == KeyType.ZSET ? "newkey.score" : "newkey.value"));
        updateRemove();
    }
    List<NewRedisKey.Row> values() {
        return rows.stream().map(r -> new NewRedisKey.Row(r.first.get(), r.second.get())).toList();
    }
    private void updateRemove() {
        remove.setDisable(listMode ? list.getSelectionModel().isEmpty() : table.getSelectionModel().isEmpty());
    }
    private TableColumn<Row, String> column(boolean isFirst) {
        TableColumn<Row, String> col = new TableColumn<>();
        col.setCellValueFactory(data -> isFirst ? data.getValue().first : data.getValue().second);
        col.setCellFactory(c -> new TableCell<>() {
            private Edit edit;
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (!isEditing()) { setGraphic(null); setText(empty ? null : value); }
            }
            @Override public void startEdit() {
                if (isEmpty()) return;
                super.startEdit();
                Row row = getTableView().getItems().get(getIndex());
                edit = new Edit(isFirst ? row.first : row.second, this::cancelEdit);
                edit.input.prefWidthProperty().bind(widthProperty().subtract(16));
                setText(null); setGraphic(edit.input); edit.input.requestFocus();
            }
            @Override public void cancelEdit() {
                if (edit != null) { edit.detach(); edit = null; }
                super.cancelEdit(); setGraphic(null); setText(getItem());
            }
        });
        return col;
    }
    /** Live binding ensures clicking Create never loses the last uncommitted edit. */
    private static final class Edit {
        final TextArea input = new TextArea();
        final StringProperty value;
        Edit(StringProperty value, Runnable finish) {
            this.value = value;
            String original = value.get();
            input.getStyleClass().add("key-cell-editor"); input.setPrefRowCount(2); input.setWrapText(true);
            input.setMinHeight(30); input.setPrefHeight(30); input.setMaxHeight(30);
            input.textProperty().bindBidirectional(value);
            input.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) { value.set(original); finish.run(); e.consume(); }
                else if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()) { finish.run(); e.consume(); }
            });
            input.focusedProperty().addListener((o,a,b) -> { if (!b) finish.run(); });
        }
        void detach() { input.textProperty().unbindBidirectional(value); input.prefWidthProperty().unbind(); }
    }
}
