package com.opencgl.redis.components;

import com.opencgl.redis.i18n.I18N;
import com.opencgl.redis.model.NewRedisKey;
import com.opencgl.redis.model.RedisKeyInfo.KeyType;
import com.opencgl.redis.service.RedisConnectionManager;
import com.opencgl.base.theme.ThemeManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.*;

/** Type-specific editor; failed submissions keep the user's input intact. */
public class NewRedisKeyDialog extends Dialog<String> {
    private final TextField key = new TextField(), ttl = new TextField(), streamId = new TextField("*");
    private final ComboBox<KeyType> type = new ComboBox<>();
    private final TextArea string = new TextArea();
    private final RedisInitialValuesEditor values = new RedisInitialValuesEditor();
    private final Label status = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private boolean busy;

    public NewRedisKeyDialog(RedisConnectionManager manager, Executor executor, String prefix) {
        setTitle(I18N.get("newkey.title"));
        initStyle(javafx.stage.StageStyle.UNDECORATED);
        getDialogPane().getStyleClass().addAll("root", "redis-new-key-dialog");
        getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/opencgl/redis/new-key-dialog.css")).toExternalForm());
        setResizable(true);
        Label title = new Label(getTitle());
        title.getStyleClass().add("dialog-title");
        HBox header = new HBox(title);
        header.getStyleClass().add("dialog-title-bar");
        final double[] dragOffset = new double[2];
        header.setOnMousePressed(e -> {
            dragOffset[0] = e.getScreenX() - getX(); dragOffset[1] = e.getScreenY() - getY();
        });
        header.setOnMouseDragged(e -> { setX(e.getScreenX() - dragOffset[0]); setY(e.getScreenY() - dragOffset[1]); });
        getDialogPane().setHeader(header);
        key.setId("new-key-name"); key.setText(prefix);
        type.setId("new-key-type");
        type.getItems().setAll(KeyType.STRING, KeyType.HASH, KeyType.LIST, KeyType.SET, KeyType.ZSET, KeyType.STREAM);
        type.setValue(KeyType.STRING);
        string.setId("new-key-string"); string.setPrefRowCount(8);
        streamId.setId("new-key-stream-id");
        Label stringLabel = new Label(I18N.get("newkey.string"));
        Label idLabel = new Label(I18N.get("newkey.id"));
        VBox form = new VBox(8, new Label(I18N.get("newkey.key")), key,
                new Label(I18N.get("newkey.type")), type, new Label(I18N.get("newkey.ttl")), ttl,
                idLabel, streamId, stringLabel, string, values);
        form.setPadding(new Insets(12)); form.setPrefWidth(580);
        VBox.setVgrow(form, Priority.ALWAYS);
        VBox.setVgrow(string, Priority.ALWAYS);
        VBox.setVgrow(values, Priority.ALWAYS);
        progress.setMaxSize(22, 22); visible(progress, false);
        status.setWrapText(true);
        HBox feedback = new HBox(8, progress, status); feedback.setPadding(new Insets(0, 12, 12, 12));
        feedback.visibleProperty().bind(progress.visibleProperty().or(status.textProperty().isNotEmpty()));
        feedback.managedProperty().bind(feedback.visibleProperty());
        getDialogPane().setContent(new VBox(form, feedback));
        ButtonType createType = new ButtonType(I18N.get("newkey.create"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(I18N.get("newkey.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(createType, cancelType);
        Node create = getDialogPane().lookupButton(createType), cancel = getDialogPane().lookupButton(cancelType);
        create.setId("new-key-create");
        create.getStyleClass().add("primary-button");
        cancel.getStyleClass().add("secondary-button");
        create.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (busy) return;
            final NewRedisKey request;
            try { request = request(); }
            catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); return; }
            busy = true; form.setDisable(true); create.setDisable(true); cancel.setDisable(true);
            visible(progress, true); status.setText(I18N.get("newkey.working"));
            try {
                CompletableFuture.supplyAsync(() -> manager.createKey(request), executor).whenComplete((created, error) ->
                    Platform.runLater(() -> {
                        busy = false; form.setDisable(false); create.setDisable(false); cancel.setDisable(false); visible(progress, false);
                        if (error != null) {
                            Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                            status.setText(Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()));
                        } else if (!created) status.setText(I18N.get("newkey.exists"));
                        else { setResult(request.key()); close(); }
                    }));
            } catch (RejectedExecutionException ex) {
                busy = false; form.setDisable(false); create.setDisable(false); cancel.setDisable(false); visible(progress, false);
                status.setText(I18N.get("newkey.disconnected"));
            }
        });
        setOnCloseRequest(e -> { if (busy) e.consume(); });
        setResultConverter(button -> null);
        Runnable update = () -> {
            boolean isString = type.getValue() == KeyType.STRING;
            visible(string, isString); visible(stringLabel, isString);
            visible(values, !isString);
            boolean stream = type.getValue() == KeyType.STREAM;
            visible(streamId, stream); visible(idLabel, stream);
            values.setType(type.getValue());
        };
        type.valueProperty().addListener((o, a, b) -> update.run());
        update.run();
        setOnShown(e -> { ThemeManager.getInstance().registerScene(getDialogPane().getScene()); key.requestFocus(); });
        setOnHidden(e -> ThemeManager.getInstance().unregisterScene(getDialogPane().getScene()));
    }

    NewRedisKey request() {
        return NewRedisKey.of(key.getText(), type.getValue(), ttl.getText(), string.getText(),
                values.values(), streamId.getText());
    }
    private static void visible(Node node, boolean show) { node.setVisible(show); node.setManaged(show); }

}
