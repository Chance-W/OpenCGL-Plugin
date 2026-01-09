package com.opencgl.cron.web.views;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * View for Cron Expression Builder (JavaFX). FXML injects preset, simple area,
 * advanced pane, and bottom controls.
 */
public abstract class CronExpBuilderView implements Initializable {

    /** center 内容 VBox，VBox.vgrow=ALWAYS，透明填满剩余空间 */
    @FXML
    protected VBox centerContentBox;

    @FXML
    protected ComboBox<String> presetComboBox;
    @FXML
    protected VBox simpleArea;
    @FXML
    protected ComboBox<String> simpleFrequency;
    @FXML
    protected TextField simpleHour;
    @FXML
    protected TextField simpleMinute;
    @FXML
    protected HBox simpleWeekdayBox;
    @FXML
    protected HBox simpleWeekdays;
    @FXML
    protected HBox simpleMonthDayBox;
    @FXML
    protected TextField simpleMonthDay;
    @FXML
    protected TabPane mainTabPane;

    @FXML
    protected TextField fieldSecond;
    @FXML
    protected TextField fieldMinute;
    @FXML
    protected TextField fieldHour;
    @FXML
    protected TextField fieldDay;
    @FXML
    protected TextField fieldMonth;
    @FXML
    protected TextField fieldWeek;
    @FXML
    protected TextField fieldYear;

    @FXML
    protected TextField cronExpressionField;
    @FXML
    protected Button parseFromExpressionButton;
    @FXML
    protected Button copyButton;
    @FXML
    protected Button verifyButton;
    @FXML
    protected javafx.scene.control.Label descriptionLabel;
    @FXML
    protected ListView<String> nextRunTimesList;

    // Badge labels in the overview bar (synced with fieldSecond-Year)
    @FXML
    protected Label badgeSecond;
    @FXML
    protected Label badgeMinute;
    @FXML
    protected Label badgeHour;
    @FXML
    protected Label badgeDay;
    @FXML
    protected Label badgeMonth;
    @FXML
    protected Label badgeWeek;
    @FXML
    protected Label badgeYear;
}
