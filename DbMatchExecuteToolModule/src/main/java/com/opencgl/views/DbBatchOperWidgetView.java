package com.opencgl.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * @author Chance.W
 */
public class DbBatchOperWidgetView {
    public StackPane mainStackPane;
    public BorderPane contentBorderPane;
    public VBox operateTopVbox;
    public VBox operateCenterVbox;
    public MFXTextField moduleDbInfo;
    public HBox moduleDb;
    public MFXTextField moduleDbTableName;
    public MFXTextField moduleSuffix;
    public MFXButton execute;

}
