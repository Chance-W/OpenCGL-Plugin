package com.opencgl.base.utils;

import java.util.Objects;

import com.opencgl.base.utils.i18n.BaseI18N;
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

    private static StackPane stackPane;
    private static VBox vBox;
    private static ProgressIndicator progressIndicator;
    private static MFXButton closeButton;
    private static Pane currentRoot;
    private static final int TIMEOUT = 20000;
    private static Task<Void> timeoutTask;
    private static volatile long requestId;

    private static void init(Pane root) {
        if (stackPane != null) {
            return;
        }
        stackPane = new StackPane();
        vBox = new VBox();
        progressIndicator = new ProgressIndicator();
        closeButton = new MFXButton(BaseI18N.getOrDefault("opencgl.base.loading.button"));
        stackPane.setAlignment(Pos.CENTER);
        stackPane.setPrefSize(root.getBoundsInParent().getWidth(), root.getBoundsInParent().getHeight());
        progressIndicator.getStylesheets().add(Objects.requireNonNull(LoadingUtil.class.getResource("/com/opencgl/base/css/ProgressIndicator.css")).toExternalForm());
        progressIndicator.setMinSize(100, 100);
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(10);
        VBox.setVgrow(closeButton, Priority.ALWAYS);
       // closeButton.getStylesheets().setAll(Objects.requireNonNull(LoadingUtil.class.getResource("/com/opencgl/base/css/opencgl-dialog.css")).toExternalForm());
        closeButton.setVisible(false);
        closeButton.setOnAction(event -> remove(currentRoot));
        vBox.getChildren().addAll(progressIndicator, closeButton);
        stackPane.getChildren().setAll(vBox);
    }

    public static void show(Pane root) {
        Objects.requireNonNull(root, "root");
        long requestedId = ++requestId;
        Platform.runLater(() -> {
            if (requestedId != requestId) {
                return;
            }
            Task<Void> previousTask = timeoutTask;
            if (previousTask != null && !previousTask.isDone()) {
                previousTask.cancel();
            }
            init(root);
            timeoutTask = addTimer();
            currentRoot = root;
            if (stackPane.getParent() != root) {
                if (stackPane.getParent() instanceof Pane oldParent) {
                    oldParent.getChildren().remove(stackPane);
                }
                root.getChildren().add(stackPane);
            }
            stackPane.toFront();
            Thread thread = new Thread(timeoutTask);
            thread.setDaemon(true);
            thread.start();
        });
    }

    public static void remove(Pane root) {
        requestId++;
        if (stackPane == null && timeoutTask == null) {
            return;
        }
        Runnable removeAction = () -> removeDirect(root);
        if (Platform.isFxApplicationThread()) {
            removeAction.run();
        } else {
            Platform.runLater(removeAction);
        }
    }

    private static void removeDirect(Pane root) {
        Task<Void> task = timeoutTask;
        timeoutTask = null;
        if (task != null && !task.isDone()) {
            task.cancel();
        }
        if (closeButton != null) {
            closeButton.setVisible(false);
        }
        if (stackPane != null) {
            Pane parent = stackPane.getParent() instanceof Pane pane ? pane : null;
            if (parent != null) {
                parent.getChildren().remove(stackPane);
            }
            if (root != null && root != parent) {
                root.getChildren().remove(stackPane);
            }
        }
        currentRoot = null;
    }

    private static Task<Void> addTimer() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Thread.sleep(TIMEOUT);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            if (timeoutTask == task && stackPane != null && stackPane.getParent() != null) {
                closeButton.setVisible(true);
            }
        });
        task.setOnCancelled(workerStateEvent -> {
            if (timeoutTask == task && closeButton != null) {
                closeButton.setVisible(false);
            }
        });
        return task;
    }
}
