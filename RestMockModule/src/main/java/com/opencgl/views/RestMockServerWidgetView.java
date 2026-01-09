package com.opencgl.views;

import com.opencgl.model.RestMockTableBean;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * @author Chance.W
 */
public class RestMockServerWidgetView {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected VBox mainWrap;
    @FXML
    protected SplitPane mainSplitPane;

    // 顶部工具栏
    @FXML
    protected Button startButton;
    @FXML
    protected Button stopButton;
    @FXML
    protected TextField portTextField;
    @FXML
    protected HBox stateHbox;
    @FXML
    protected ProgressIndicator statusProcessBar;
    @FXML
    protected Label statusLabel;

    // 左侧: 规则列表
    @FXML
    protected Button addRuleButton;
    @FXML
    protected TextField searchField;
    @FXML
    protected TableView<RestMockTableBean> tableViewMain;
    @FXML
    protected TableColumn<RestMockTableBean, Boolean> isEnabledTableColumn;
    @FXML
    protected TableColumn<RestMockTableBean, String> methodTableColumn;
    @FXML
    protected TableColumn<RestMockTableBean, String> contentPathTableColumn;
    @FXML
    protected TableColumn<RestMockTableBean, String> desTableColumn;

    // 右侧: 详情面板
    @FXML
    protected StackPane rightContentArea;
    @FXML
    protected VBox emptyState;
    @FXML
    protected VBox ruleDetailPane;
    @FXML
    protected Label ruleDetailTitle;
    @FXML
    protected Button deleteRuleButton;
    @FXML
    protected Button saveRuleButton;
    @FXML
    protected ComboBox<String> httpMethodCombo;
    @FXML
    protected ComboBox<String> statusCodeCombo;
    @FXML
    protected TextField delayMsField;
    @FXML
    protected TextField contentPath;
    @FXML
    protected TextField des;
    @FXML
    protected TextField responseHeader;
    @FXML
    protected TextArea responseContent;

    // 底部日志
    @FXML
    protected TextArea outputTextArea;
    @FXML
    protected TitledPane logDrawerPane;
}
