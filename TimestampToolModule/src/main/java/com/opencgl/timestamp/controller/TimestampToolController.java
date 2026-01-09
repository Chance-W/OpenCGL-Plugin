package com.opencgl.timestamp.controller;

import com.opencgl.timestamp.i18n.I18N;
import com.opencgl.timestamp.service.TimestampConverter;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 时间戳工具控制器
 */
public class TimestampToolController implements Initializable {

    @FXML
    private Label label_local_time;
    @FXML
    private Label label_utc_time;
    @FXML
    private Label label_unix_seconds;
    @FXML
    private Label label_unix_millis;

    @FXML
    private Label title_converter;
    @FXML
    private Label title_calculator;
    @FXML
    private TitledPane section_diff;
    @FXML
    private TitledPane section_offset;
    @FXML
    private Label section_help;
    @FXML
    private Label help_1;
    @FXML
    private Label help_2;
    @FXML
    private Label help_3;

    @FXML
    private Label currentLocalTimeLabel;
    @FXML
    private Label currentUtcTimeLabel;
    @FXML
    private Label unixSecondsLabel;
    @FXML
    private Label unixMillisLabel;
    @FXML
    private MFXButton pauseBtn;

    // --- 转换区域 ---
    @FXML
    private MFXTextField inputField;
    @FXML
    private MFXComboBox<String> timezoneCombo;
    @FXML
    private MFXButton convertBtn;
    @FXML
    private MFXButton nowBtn;
    @FXML
    private MFXButton clearBtn;

    // --- 结果展示区域 ---
    @FXML
    private VBox resultsBox; // 动态添加结果行

    // --- 时间计算器 ---
    @FXML
    private MFXTextField calcStartTimeField;
    @FXML
    private MFXTextField calcEndTimeField;
    @FXML
    private Label diffResultLabel;

    @FXML
    private MFXTextField calcBaseTimeField;
    @FXML
    private MFXTextField calcAmountField;
    @FXML
    private MFXComboBox<String> calcUnitCombo;
    @FXML
    private MFXButton calcAddBtn;
    @FXML
    private MFXButton calcSubBtn;
    @FXML
    private Label calcResultLabel;

    private Timeline clockTimeline;
    private boolean isPaused = false;
    private final ZoneId localZone = ZoneId.systemDefault();
    private final Map<String, ChronoUnit> unitMap = new HashMap<>();
    private final List<Timer> copyTimers = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean disposed;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        initClock();
        initCombos();
        initEvents();

        // 初始显示当前时间转换结果
        handleConvert(String.valueOf(System.currentTimeMillis()));
    }

    private void initI18n() {
        if (label_local_time != null)
            label_local_time.textProperty().bind(I18N.getBinding("label.local_time"));
        if (label_utc_time != null)
            label_utc_time.textProperty().bind(I18N.getBinding("label.utc_time"));
        if (label_unix_seconds != null)
            label_unix_seconds.textProperty().bind(I18N.getBinding("label.unix_seconds"));
        if (label_unix_millis != null)
            label_unix_millis.textProperty().bind(I18N.getBinding("label.unix_millis"));

        pauseBtn.textProperty().bind(I18N.getBinding(isPaused ? "button.resume" : "button.pause"));

        title_converter.textProperty().bind(I18N.getBinding("title.converter"));
        inputField.floatingTextProperty().bind(I18N.getBinding("prompt.input"));
        timezoneCombo.floatingTextProperty().bind(I18N.getBinding("prompt.timezone"));
        convertBtn.textProperty().bind(I18N.getBinding("button.convert"));
        nowBtn.textProperty().bind(I18N.getBinding("button.now"));
        clearBtn.textProperty().bind(I18N.getBinding("button.clear"));

        title_calculator.textProperty().bind(I18N.getBinding("title.calculator"));
        section_diff.textProperty().bind(I18N.getBinding("section.diff"));
        calcStartTimeField.floatingTextProperty().bind(I18N.getBinding("prompt.start_time"));
        calcEndTimeField.floatingTextProperty().bind(I18N.getBinding("prompt.end_time"));
        diffResultLabel.textProperty().bind(I18N.getBinding("label.wait_input"));

        section_offset.textProperty().bind(I18N.getBinding("section.offset"));
        calcBaseTimeField.floatingTextProperty().bind(I18N.getBinding("prompt.base_time"));
        calcAmountField.floatingTextProperty().bind(I18N.getBinding("prompt.amount"));
        calcUnitCombo.floatingTextProperty().bind(I18N.getBinding("prompt.unit"));
        calcAddBtn.textProperty().bind(I18N.getBinding("button.add"));
        calcSubBtn.textProperty().bind(I18N.getBinding("button.sub"));
        calcResultLabel.textProperty().bind(I18N.getBinding("label.wait_calc"));

        section_help.textProperty().bind(I18N.getBinding("section.help"));
        help_1.textProperty().bind(I18N.getBinding("help.1"));
        help_2.textProperty().bind(I18N.getBinding("help.2"));
        help_3.textProperty().bind(I18N.getBinding("help.3"));
    }

    private void initClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.millis(100), e -> updateClock()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    private void updateClock() {
        if (isPaused)
            return;

        ZonedDateTime now = ZonedDateTime.now(localZone);
        ZonedDateTime utc = now.withZoneSameInstant(ZoneId.of("UTC"));

        currentLocalTimeLabel.setText(TimestampConverter.format(now, "yyyy-MM-dd HH:mm:ss"));
        currentUtcTimeLabel.setText(TimestampConverter.format(utc, "yyyy-MM-dd HH:mm:ss'Z'"));
        unixSecondsLabel.setText(String.valueOf(now.toEpochSecond()));
        unixMillisLabel.setText(String.valueOf(now.toInstant().toEpochMilli()));
    }

    private void initCombos() {
        // 时区下拉框
        Set<String> zones = TimestampConverter.getAllTimezones();
        List<String> sortedZones = zones.stream().sorted().collect(Collectors.toList());
        // 将常用时区放在最前
        List<String> priorityZones = new ArrayList<>(TimestampConverter.COMMON_TIMEZONES.values());

        // 简单去重合并
        List<String> finalZones = new ArrayList<>(priorityZones);
        for (String z : sortedZones) {
            if (!finalZones.contains(z)) {
                finalZones.add(z);
            }
        }

        timezoneCombo.setItems(FXCollections.observableArrayList(finalZones));
        // 使用 selectItem 并延迟一下，确保 UI 加载完成后选中
        Platform.runLater(() -> {
            if (disposed) return;
            String defaultZone = ZoneId.systemDefault().getId();
            if (finalZones.contains(defaultZone)) {
                timezoneCombo.selectItem(defaultZone);
            } else {
                timezoneCombo.selectItem(finalZones.get(0));
            }
        });

        // 计算单位下拉框
        calcUnitCombo.setItems(FXCollections.observableArrayList(
                I18N.get("unit.year"), I18N.get("unit.month"), I18N.get("unit.day"),
                I18N.get("unit.hour"), I18N.get("unit.minute"), I18N.get("unit.second"), I18N.get("unit.millis")));
        calcUnitCombo.setValue(I18N.get("unit.day"));

        unitMap.clear();
        unitMap.put(I18N.get("unit.year"), ChronoUnit.YEARS);
        unitMap.put(I18N.get("unit.month"), ChronoUnit.MONTHS);
        unitMap.put(I18N.get("unit.day"), ChronoUnit.DAYS);
        unitMap.put(I18N.get("unit.hour"), ChronoUnit.HOURS);
        unitMap.put(I18N.get("unit.minute"), ChronoUnit.MINUTES);
        unitMap.put(I18N.get("unit.second"), ChronoUnit.SECONDS);
        unitMap.put(I18N.get("unit.millis"), ChronoUnit.MILLIS);
    }

    private void initEvents() {
        pauseBtn.setOnAction(e -> {
            isPaused = !isPaused;
            // pauseBtn.setText(isPaused ? "继续" : "暂停"); // Removed as it is now bound
            initI18n(); // Re-trigger binding update for pause button
        });

        convertBtn.setOnAction(e -> handleConvert(inputField.getText()));

        nowBtn.setOnAction(e -> {
            String nowStr = String.valueOf(System.currentTimeMillis());
            inputField.setText(nowStr);
            handleConvert(nowStr);
        });

        clearBtn.setOnAction(e -> {
            inputField.clear();
            resultsBox.getChildren().clear();
        });

        // 绑定回车事件
        inputField.setOnAction(e -> handleConvert(inputField.getText()));

        // 计算器事件
        calcAddBtn.setOnAction(e -> calculateOffset(true));
        calcSubBtn.setOnAction(e -> calculateOffset(false));

        // 差值计算
        MFXButton diffBtn = new MFXButton(I18N.get("button.calc_diff")); // 动态绑定，或者在FXML里加
        // 这里简化，假设输入框失去焦点时触发或者按钮触发
        calcEndTimeField.textProperty().addListener((obs, old, val) -> calculateDiff());
    }

    private void handleConvert(String input) {
        if (input == null || input.trim().isEmpty())
            return;

        try {
            resultsBox.getChildren().clear();

            String zoneIdStr = timezoneCombo.getValue();
            if (zoneIdStr == null || zoneIdStr.isEmpty()) {
                zoneIdStr = ZoneId.systemDefault().getId();
            }
            ZoneId selectedZone = ZoneId.of(zoneIdStr);
            ZonedDateTime zdt = TimestampConverter.smartParse(input, selectedZone);

            // 1. 生成基本信息行
            addResultRow(I18N.get("msg.unix_s"), String.valueOf(zdt.toEpochSecond()));
            addResultRow(I18N.get("msg.unix_ms"), String.valueOf(zdt.toInstant().toEpochMilli()));

            // 2. 生成预定义格式行
            for (Map.Entry<String, String> entry : TimestampConverter.PREDEFINED_FORMATS.entrySet()) {
                addResultRow(entry.getKey(), TimestampConverter.format(zdt, entry.getValue()));
            }

            // 3. 生成常用时区转换
            addSectionHeader(I18N.get("msg.main_zones"));
            ZonedDateTime utc = zdt.withZoneSameInstant(ZoneId.of("UTC"));
            addResultRow("UTC", TimestampConverter.format(utc, "yyyy-MM-dd HH:mm:ss"));

            ZonedDateTime cst = zdt.withZoneSameInstant(ZoneId.of("Asia/Shanghai"));
            addResultRow(I18N.get("msg.beijing"), TimestampConverter.format(cst, "yyyy-MM-dd HH:mm:ss"));

            ZonedDateTime jst = zdt.withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
            addResultRow(I18N.get("msg.tokyo"), TimestampConverter.format(jst, "yyyy-MM-dd HH:mm:ss"));

            ZonedDateTime ny = zdt.withZoneSameInstant(ZoneId.of("America/New_York"));
            addResultRow(I18N.get("msg.newyork"), TimestampConverter.format(ny, "yyyy-MM-dd HH:mm:ss"));

        } catch (Exception e) {
            Label errorLabel = new Label(I18N.get("msg.convert_failed", e.getMessage()));
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            resultsBox.getChildren().add(errorLabel);
        }
    }

    private void addSectionHeader(String title) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0; -fx-text-fill: #555;");
        resultsBox.getChildren().add(label);
    }

    private void addResultRow(String labelText, String valueText) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        // row.setStyle("-fx-padding: 5; -fx-border-color: #eee; -fx-border-width: 0 0 1
        // 0;");

        Label label = new Label(labelText + ":");
        label.setPrefWidth(180);
        // label.setStyle("-fx-text-fill: #666;");

        TextField valueField = new TextField(valueText);
        valueField.setEditable(false);
        // valueField.setStyle("-fx-background-color: transparent; -fx-text-fill:
        // #333;");
        HBox.setHgrow(valueField, javafx.scene.layout.Priority.ALWAYS);

        MFXButton copyBtn = new MFXButton("");
        copyBtn.textProperty().bind(I18N.getBinding("button.copy"));
        copyBtn.setStyle("-fx-font-size: 10px;");
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(valueText);
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.textProperty().unbind();
            copyBtn.textProperty().bind(I18N.getBinding("button.copied"));
            // 延迟恢复
            Timer copyTimer = new Timer(true);
            copyTimers.add(copyTimer);
            copyTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        if (disposed) return;
                        copyBtn.textProperty().unbind();
                        copyBtn.textProperty().bind(I18N.getBinding("button.copy"));
                    });
                    copyTimers.remove(copyTimer);
                }
            }, 1000);
        });

        row.getChildren().addAll(label, valueField, copyBtn);
        resultsBox.getChildren().add(row);
    }

    private void calculateDiff() {
        String startStr = calcStartTimeField.getText();
        String endStr = calcEndTimeField.getText();

        if (startStr == null || startStr.isEmpty() || endStr == null || endStr.isEmpty())
            return;

        try {
            String zoneIdStr = timezoneCombo.getValue();
            if (zoneIdStr == null || zoneIdStr.isEmpty()) {
                zoneIdStr = ZoneId.systemDefault().getId();
            }
            ZoneId zone = ZoneId.of(zoneIdStr);
            ZonedDateTime start = TimestampConverter.smartParse(startStr, zone);
            ZonedDateTime end = TimestampConverter.smartParse(endStr, zone);

            java.time.Duration duration = java.time.Duration.between(start, end);
            diffResultLabel.textProperty().unbind();
            diffResultLabel.setText(I18N.get("msg.diff_prefix") + TimestampConverter.formatDuration(duration));
            diffResultLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
        } catch (Exception e) {
            diffResultLabel.textProperty().unbind();
            diffResultLabel.textProperty().bind(I18N.getBinding("msg.diff_error"));
            diffResultLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void calculateOffset(boolean isAdd) {
        String baseStr = calcBaseTimeField.getText();
        String amountStr = calcAmountField.getText();
        String unitStr = calcUnitCombo.getValue();

        if (baseStr == null || baseStr.isEmpty() || amountStr == null || amountStr.isEmpty()) {
            calcResultLabel.textProperty().unbind();
            calcResultLabel.textProperty().bind(I18N.getBinding("msg.fill_info"));
            return;
        }

        try {
            String zoneIdStr = timezoneCombo.getValue();
            if (zoneIdStr == null || zoneIdStr.isEmpty()) {
                zoneIdStr = ZoneId.systemDefault().getId();
            }
            ZoneId zone = ZoneId.of(zoneIdStr);
            ZonedDateTime base = TimestampConverter.smartParse(baseStr, zone);
            long amount = Long.parseLong(amountStr);
            if (!isAdd)
                amount = -amount;

            ChronoUnit unit = unitMap.getOrDefault(unitStr, ChronoUnit.DAYS);

            ZonedDateTime result = base.plus(amount, unit);
            calcResultLabel.textProperty().unbind();
            calcResultLabel
                    .setText(I18N.get("msg.result_prefix") + TimestampConverter.format(result, "yyyy-MM-dd HH:mm:ss"));
            calcResultLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold;");

        } catch (Exception e) {
            calcResultLabel.textProperty().unbind();
            calcResultLabel.setText(I18N.get("msg.calc_error", e.getMessage()));
            calcResultLabel.setStyle("-fx-text-fill: red;");
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (clockTimeline != null) {
            clockTimeline.stop();
            clockTimeline = null;
        }
        synchronized (copyTimers) {
            for (Timer timer : copyTimers) {
                try {
                    timer.cancel();
                } catch (RuntimeException ignored) {
                    // Continue cancelling the remaining timers.
                }
            }
            copyTimers.clear();
        }
    }
}
