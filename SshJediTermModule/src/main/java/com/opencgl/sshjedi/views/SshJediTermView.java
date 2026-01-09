package com.opencgl.sshjedi.views;

import javafx.fxml.FXML;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * SSH终端视图 - TabPane多标签版本
 */
public class SshJediTermView {
    @FXML
    public StackPane rootPane;
    @FXML
    public SplitPane mainSplitPane;

    // 左侧树面板
    @FXML
    public VBox treePanel;
    @FXML
    public BorderPane treeBorderPane;

    // 右侧TabPane
    @FXML
    public TabPane sessionTabPane;
}
