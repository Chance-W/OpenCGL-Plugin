package com.opencgl.base.utils;

import com.opencgl.base.theme.ThemeManager;
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
import javafx.scene.Parent;

import java.util.Objects;

/**
 * Instance-based loading mask utility.
 * Each instance manages its own loading overlay independently.
 * 
 * @author Chance.W
 * @version 2.0
 * @since v10.0
 */
public class LoadingMask {

    private final StackPane maskPane = new StackPane();
    private final VBox contentBox = new VBox();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final MFXButton closeButton = new MFXButton(BaseI18N.getOrDefault("opencgl.base.loading.button"));
    private static final int TIMEOUT_MS = 20000;

    private Task<Void> timeoutTask;
    private volatile boolean isShowing = false;
    private volatile long requestId;

    /**
     * Constructor initializes the mask UI components
     */
    public LoadingMask() {
        initComponents();
    }

    /**
     * Initialize UI components
     */
    private void initComponents() {
        // Configure mask pane
        maskPane.setAlignment(Pos.CENTER);

        // Configure progress indicator
        progressIndicator.setMinSize(100, 100);
        // Apply current theme stylesheets
        ThemeManager.getInstance().getCurrentThemeStylesheets().forEach(sheet -> {
            if (!progressIndicator.getStylesheets().contains(sheet)) {
                progressIndicator.getStylesheets().add(sheet);
            }
        });
        // Add ProgressIndicator specific styles
        String progressCSS = Objects.requireNonNull(
                LoadingMask.class.getResource("/com/opencgl/base/css/ProgressIndicator.css")).toExternalForm();
        if (!progressIndicator.getStylesheets().contains(progressCSS)) {
            progressIndicator.getStylesheets().add(progressCSS);
        }

        // Configure content box
        contentBox.setAlignment(Pos.CENTER);
        contentBox.setSpacing(10);
        VBox.setVgrow(closeButton, Priority.ALWAYS);

        // Configure close button (already on FX thread, use hideDirect to remove mask immediately)
        closeButton.setVisible(false);
        closeButton.setOnAction(event -> hideDirect());
        // Apply current theme stylesheets to button
        ThemeManager.getInstance().getCurrentThemeStylesheets().forEach(sheet -> {
            if (!closeButton.getStylesheets().contains(sheet)) {
                closeButton.getStylesheets().add(sheet);
            }
        });
        // Add dialog specific styles
        String dialogCSS = Objects.requireNonNull(
                LoadingMask.class.getResource("/com/opencgl/base/css/opencgl-dialog.css")).toExternalForm();
        if (!closeButton.getStylesheets().contains(dialogCSS)) {
            closeButton.getStylesheets().add(dialogCSS);
        }

        // Assemble components
        contentBox.getChildren().addAll(progressIndicator, closeButton);
        maskPane.getChildren().setAll(contentBox);
    }

    /**
     * Show the loading mask on the specified parent pane
     * 
     * @param parent The parent pane to overlay the mask on
     */
    public void show(Pane parent) {
        Objects.requireNonNull(parent, "parent");
        long requestedId = ++requestId;
        runOnFxThread(() -> {
            if (requestedId == requestId) {
                showDirect(parent);
            }
        });
    }

    /**
     * Show the loading mask on the specified parent pane (non-Platform.runLater
     * version)
     * Use this when already in JavaFX Application Thread
     * 
     * @param parent The parent pane to overlay the mask on
     */
    public void showDirect(Pane parent) {
        Objects.requireNonNull(parent, "parent");
        if (isShowing && maskPane.getParent() == parent) {
            maskPane.toFront();
            return;
        }

        // Always normalize stale state before attaching. The node can still be in
        // a parent even when an earlier asynchronous show/hide changed isShowing.
        detachMask(maskPane);
        maskPane.prefWidthProperty().unbind();
        maskPane.prefHeightProperty().unbind();

        // Bind size to parent
        maskPane.prefWidthProperty().bind(parent.widthProperty());
        maskPane.prefHeightProperty().bind(parent.heightProperty());

        // Add to parent and ensure it's on top
        parent.getChildren().add(maskPane);
        maskPane.toFront();

        isShowing = true;

        // Start timeout task
        startTimeoutTask();
    }

    /**
     * Hide the loading mask
     */
    public void hide() {
        requestId++;
        runOnFxThread(this::hideDirect);
    }

    /**
     * Hide the loading mask (non-Platform.runLater version)
     * Use this when already in JavaFX Application Thread
     */
    public void hideDirect() {
        // Cancel timeout task
        Task<Void> task = timeoutTask;
        timeoutTask = null;
        if (task != null && task.isRunning()) {
            task.cancel();
        }

        // Hide close button
        closeButton.setVisible(false);

        // Unbind and remove from parent
        maskPane.prefWidthProperty().unbind();
        maskPane.prefHeightProperty().unbind();

        detachMask(maskPane);

        isShowing = false;
    }

    static void detachMask(Pane mask) {
        Parent parent = mask.getParent();
        if (parent instanceof Pane pane) {
            pane.getChildren().remove(mask);
        }
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        }
        else {
            Platform.runLater(action);
        }
    }

    /**
     * Start the timeout task that will show the close button after timeout
     */
    private void startTimeoutTask() {
        // Cancel existing task if any
        if (timeoutTask != null && timeoutTask.isRunning()) {
            timeoutTask.cancel();
        }

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Thread.sleep(TIMEOUT_MS);
                return null;
            }
        };

        timeoutTask = task;
        task.setOnSucceeded(event -> {
            // Ignore completion from a timeout belonging to an older display.
            if (timeoutTask == task && isShowing && maskPane.getParent() != null) {
                closeButton.setVisible(true);
            }
        });

        task.setOnCancelled(event -> closeButton.setVisible(false));

        // Start task in daemon thread
        Thread thread = new Thread(task, "opencgl-loading-mask-timeout");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Check if the mask is currently showing
     * 
     * @return true if showing, false otherwise
     */
    public boolean isShowing() {
        return isShowing;
    }
}
