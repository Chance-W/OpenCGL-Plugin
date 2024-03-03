package com.opencgl.base.utils;

import java.util.Objects;

import com.opencgl.base.utils.i18n.BASE18N;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/16 13:55
 * @since v9.0
 */
public class LoadingUtil {

    private static final StackPane stackPane = new StackPane();
    private static final VBox vBox = new VBox();
    private static final ProgressIndicator progressIndicator = new ProgressIndicator();
    private static final MFXButton closeButton = new MFXButton(BASE18N.getOrDefault("opencgl.base.loading.button"));
    private static final int TIMEOUT = 20000;
    private static Task<Void> timeoutTask;

    private static void init(Pane root) {
        if (!stackPane.getChildren().isEmpty()) {
            return;
        }
        stackPane.setAlignment(Pos.CENTER);
        stackPane.setPrefSize(root.getBoundsInParent().getWidth(), root.getBoundsInParent().getHeight());
        progressIndicator.getStylesheets().setAll(Objects.requireNonNull(LoadingUtil.class.getResource("/com/opencgl/base/css/ProgressIndicator.css")).toExternalForm());
        progressIndicator.setMinSize(100, 100);
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(10);
        VBox.setVgrow(closeButton, Priority.ALWAYS);
        closeButton.getStylesheets().setAll(Objects.requireNonNull(LoadingUtil.class.getResource("/com/opencgl/base/css/opencgl-dialog.css")).toExternalForm());
        closeButton.setVisible(false);
        closeButton.setOnAction(event -> Platform.runLater(() -> remove(root)));
        vBox.getChildren().addAll(progressIndicator, closeButton);
        stackPane.getChildren().setAll(vBox);
    }

    public static void show(Pane root) {
        timeoutTask = addTimer();
        init(root);
        Platform.runLater(() -> {
            root.getChildren().add(stackPane);
            Thread thread = new Thread(timeoutTask);
            thread.setDaemon(true);
            thread.start();
        });
    }

    public static void remove(Pane root) {
    /*    if (timeoutTask.isRunning()) {
            timeoutTask.cancel();
        }*/
        timeoutTask.cancel();
        Platform.runLater(() -> {
            closeButton.setVisible(false);
            root.getChildren().remove(stackPane);
        });
    }

    private static Task<Void> addTimer() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Thread.sleep(TIMEOUT);
                return null;
            }
        };
        task.setOnSucceeded(event -> closeButton.setVisible(true));
        task.setOnCancelled(workerStateEvent -> closeButton.setVisible(false));
        return task;
    }
}
