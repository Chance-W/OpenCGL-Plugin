package com.opencgl.base.view;

import com.opencgl.base.theme.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * 通用的无边框可拖拽自定义弹框，支持设置标题和内容组件。
 */
public class BaseCustomDialog extends Stage {

    private final BorderPane rootPane;
    private final VBox contentPane;
    private final Label titleLabel;

    private double xOffset = 0;
    private double yOffset = 0;

    public BaseCustomDialog() {
        this(550, -1);
    }

    public BaseCustomDialog(double width, double height) {
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.TRANSPARENT);

        StackPane shadowContainer = new StackPane();
        shadowContainer.setStyle("-fx-background-color: transparent; -fx-padding: 15;");

        rootPane = new BorderPane();
        rootPane.getStyleClass().add("root"); // For external theme stylesheets
        rootPane.setStyle(
                "-fx-background-color: -theme-bg-primary; -fx-border-color: -theme-border-color; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.color(0, 0, 0, 0.25));
        shadow.setRadius(12);
        shadow.setOffsetY(4);
        rootPane.setEffect(shadow);

        // 标题栏
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(10, 15, 10, 15));
        titleBar.setStyle(
                "-fx-background-color: -theme-bg-color; -fx-border-color: -theme-border-color; -fx-border-width: 0 0 1 0; -fx-background-radius: 4 4 0 0;");

        titleLabel = new Label("Dialog");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -theme-text-color;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: -theme-text-color; -fx-cursor: hand; -fx-font-size: 14px;");
        closeBtn.setOnAction(e -> this.close());

        titleBar.getChildren().addAll(titleLabel, spacer, closeBtn);

        rootPane.setTop(titleBar);

        // 内容区
        contentPane = new VBox();
        contentPane.setPadding(new Insets(15));
        rootPane.setCenter(contentPane);

        shadowContainer.getChildren().add(rootPane);

        Scene scene;
        if (width > 0 && height > 0) {
            scene = new Scene(shadowContainer, width, height);
        } else if (width > 0) {
            rootPane.setPrefWidth(width);
            scene = new Scene(shadowContainer);
        } else if (height > 0) {
            rootPane.setPrefHeight(height);
            scene = new Scene(shadowContainer);
        } else {
            scene = new Scene(shadowContainer);
        }
        scene.setFill(Color.TRANSPARENT);

        // 支持 ESC 键关闭
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                this.close();
                event.consume();
            }
        });

        this.setScene(scene);

        // 处理主题注册与居中 (立即注册以应用样式表，避免显示时无样式)
        ThemeManager.getInstance().registerScene(scene);

        this.setOnShown(event -> {
            Window parent = getOwner();
            if (parent != null) {
                this.setX(parent.getX() + (parent.getWidth() - this.getWidth()) / 2);
                this.setY(parent.getY() + (parent.getHeight() - this.getHeight()) / 2);
            }
        });
        this.setOnHidden(event -> ThemeManager.getInstance().unregisterScene(scene));
    }

    public void setDialogTitle(String title) {
        titleLabel.setText(title);
    }

    public javafx.beans.property.StringProperty dialogTitleProperty() {
        return titleLabel.textProperty();
    }

    public void setContent(Node content) {
        contentPane.getChildren().setAll(content);
    }
}
