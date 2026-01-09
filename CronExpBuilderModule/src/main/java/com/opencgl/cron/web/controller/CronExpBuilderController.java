package com.opencgl.cron.web.controller;

import com.opencgl.cron.i18n.I18N;
import com.opencgl.cron.service.CronDescribeUtil;
import com.opencgl.cron.service.CronExpressionBuilder;
import com.opencgl.cron.service.CronNextRunCalculator;
import com.opencgl.cron.service.CronPreset;
import com.opencgl.cron.web.views.CronExpBuilderView;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CronExpBuilderController extends CronExpBuilderView {

    private static final int DEFAULT_NEXT_RUN_COUNT = 5;
    /** Quartz: 1=Sun, 2=Mon, ..., 7=Sat */
    private static final int[] WEEK_QUARTZ = { 1, 2, 3, 4, 5, 6, 7 };
    private static final String[] SIMPLE_FREQ_KEYS = {
            "simple.freq_minute", "preset.every_5min", "preset.every_15min", "preset.every_30min",
            "simple.freq_hour", "simple.freq_day", "simple.freq_week", "simple.freq_month"
    };
    private static final String[] WEEK_KEYS = { "simple.week_sun", "simple.week_mon", "simple.week_tue",
            "simple.week_wed", "simple.week_thu", "simple.week_fri", "simple.week_sat" };

    // Advanced tabs (only created when preset = Custom)
    private ToggleGroup secondTg;
    private Spinner<Integer> secondCycleStart, secondCycleEnd, secondStartOnStart, secondStartOnEnd;
    private List<CheckBox> secondChecks;
    private ToggleGroup minTg;
    private Spinner<Integer> minCycleStart, minCycleEnd, minStartOnStart, minStartOnEnd;
    private List<CheckBox> minChecks;
    private ToggleGroup hourTg;
    private Spinner<Integer> hourCycleStart, hourCycleEnd, hourStartOnStart, hourStartOnEnd;
    private List<CheckBox> hourChecks;
    private ToggleGroup dayTg;
    private Spinner<Integer> dayCycleStart, dayCycleEnd, dayStartOnStart, dayStartOnEnd, dayWorkDay;
    private List<CheckBox> dayChecks;
    private ToggleGroup monthTg;
    private Spinner<Integer> monthCycleStart, monthCycleEnd, monthStartOnStart, monthStartOnEnd;
    private List<CheckBox> monthChecks;
    private ToggleGroup weekTg;
    private Spinner<Integer> weekCycleStart, weekCycleEnd, weekOfDayN, weekOfDayW, weekLastW;
    private List<CheckBox> weekChecks;
    private ToggleGroup yearTg;
    private Spinner<Integer> yearCycleStart, yearCycleEnd;

    private final ChangeListener<Object> anyAdvancedChange = (a, b, c) -> refreshCronFromAdvanced();

    private boolean skipPresetSync;
    private boolean skipSimpleSync;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        skipSimpleSync = true;
        skipPresetSync = true;
        initPresetComboBox();
        initSimpleArea();
        bindCenterScrollHeight();
        bindParseButton();
        bindCronFieldListener();
        bindCopyVerifyButtons();
        // Default: Daily 09:00
        selectPreset(CronPreset.DAILY_09);
        applyPresetToCronAndSimple(CronPreset.DAILY_09);
        refreshBottomFromCron(CronPreset.DAILY_09.getCronExpression());
        skipPresetSync = false;
        skipSimpleSync = false;
    }

    /** 高级面板是否当前已显示 */
    private boolean advancedShown = false;

    /** 将 mainTabPane 显示，VBox 自动拉高 */
    private void showAdvanced() {
        if (advancedShown)
            return;
        ensureAdvancedTabsBuilt();
        mainTabPane.setVisible(true);
        mainTabPane.setManaged(true);
        advancedShown = true;
    }

    /** 将 mainTabPane 隐藏，VBox 自动收缩 */
    private void hideAdvanced() {
        if (!advancedShown)
            return;
        mainTabPane.setVisible(false);
        mainTabPane.setManaged(false);
        advancedShown = false;
    }

    private void bindCopyVerifyButtons() {
        if (copyButton != null) {
            copyButton.setOnAction(e -> {
                String expr = cronExpressionField.getText();
                if (expr == null || expr.isBlank())
                    return;
                Clipboard cb = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(expr.trim());
                cb.setContent(content);
            });
        }
        if (verifyButton != null) {
            verifyButton.setOnAction(e -> {
                String expr = cronExpressionField.getText();
                if (expr == null || expr.isBlank())
                    return;
                java.util.List<String> times = com.opencgl.cron.service.CronNextRunCalculator
                        .getNextRunTimes(expr.trim(), 10);
                String msg = times.isEmpty()
                        ? I18N.get("verify.invalid")
                        : String.join("\n", times);
                String header = times.isEmpty() ? I18N.get("verify.invalid") : I18N.get("verify.next_runs");
                com.opencgl.base.utils.DialogUtil.showCustomInfo(header, msg);
            });
        }
    }

    private void initPresetComboBox() {
        List<String> items = new ArrayList<>();
        for (CronPreset p : CronPreset.values()) {
            items.add(I18N.get(p.getI18nKey()));
        }
        presetComboBox.setItems(FXCollections.observableArrayList(items));
        presetComboBox.getSelectionModel().selectedIndexProperty().addListener((a, b, idx) -> {
            if (skipPresetSync || idx == null || idx.intValue() < 0)
                return;
            CronPreset p = CronPreset.values()[idx.intValue()];
            if (p.isCustom()) {
                showAdvanced();
                parseCronToAdvancedOnly(cronExpressionField.getText());
                refreshBottomFromCron(cronExpressionField.getText());
            } else {
                hideAdvanced();
                applyPresetToCronAndSimple(p);
                refreshBottomFromCron(p.getCronExpression());
            }
        });
    }

    private void initSimpleArea() {
        simpleFrequency.setItems(FXCollections.observableArrayList(
                IntStream.range(0, SIMPLE_FREQ_KEYS.length).mapToObj(i -> I18N.get(SIMPLE_FREQ_KEYS[i]))
                        .collect(Collectors.toList())));
        simpleFrequency.getSelectionModel().select(5); // 每天
        for (int i = 0; i < 7; i++) {
            CheckBox cb = new CheckBox(I18N.get(WEEK_KEYS[i]));
            cb.setUserData(WEEK_QUARTZ[i]);
            cb.selectedProperty().addListener((a, b, v) -> onSimpleChanged());
            simpleWeekdays.getChildren().add(cb);
        }
        simpleFrequency.getSelectionModel().selectedIndexProperty().addListener((a, b, idx) -> {
            if (idx == null || idx.intValue() < 0)
                return;
            int i = idx.intValue();
            simpleWeekdayBox.setVisible(i == 6);
            simpleWeekdayBox.setManaged(i == 6);
            simpleMonthDayBox.setVisible(i == 7);
            simpleMonthDayBox.setManaged(i == 7);
            onSimpleChanged();
        });
        simpleHour.setText("9");
        simpleMinute.setText("0");
        simpleHour.textProperty().addListener((a, b, v) -> onSimpleChanged());
        simpleMinute.textProperty().addListener((a, b, v) -> onSimpleChanged());
        simpleMonthDay.setText("1");
        simpleMonthDay.textProperty().addListener((a, b, v) -> onSimpleChanged());
    }

    private void onSimpleChanged() {
        if (skipSimpleSync)
            return;
        String cron = buildCronFromSimple();
        if (cron == null)
            return;
        skipPresetSync = true;
        presetComboBox.getSelectionModel().select(CronPreset.CUSTOM.ordinal());
        skipPresetSync = false;
        showAdvanced();
        parseCronToAdvancedOnly(cron);
        cronExpressionField.setText(cron);
        refreshBottomFromCron(cron);
    }

    private String buildCronFromSimple() {
        int idx = simpleFrequency.getSelectionModel().getSelectedIndex();
        if (idx < 0)
            idx = 5;
        int h = parseInt(simpleHour.getText(), 0, 23, 0);
        int m = parseInt(simpleMinute.getText(), 0, 59, 0);
        switch (idx) {
            case 0:
                return "0 * * * * ? *";
            case 1:
                return "0 0/5 * * * ? *";
            case 2:
                return "0 0/15 * * * ? *";
            case 3:
                return "0 0/30 * * * ? *";
            case 4:
                return "0 0 * * * ? *";
            case 5:
                return String.format("0 %d %d * * ? *", m, h);
            case 6: {
                List<Integer> w = new ArrayList<>();
                for (javafx.scene.Node n : simpleWeekdays.getChildren()) {
                    if (n instanceof CheckBox && ((CheckBox) n).isSelected())
                        w.add((Integer) ((CheckBox) n).getUserData());
                }
                if (w.isEmpty())
                    w.add(2);
                String weekPart = w.stream().map(String::valueOf).collect(Collectors.joining(","));
                return String.format("0 %d %d ? * %s *", m, h, weekPart);
            }
            case 7: {
                int d = parseInt(simpleMonthDay.getText(), 1, 31, 1);
                return String.format("0 %d %d %d * ? *", m, h, d);
            }
            default:
                return String.format("0 %d %d * * ? *", m, h);
        }
    }

    private static int parseInt(String s, int min, int max, int def) {
        if (s == null)
            return def;
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void applyPresetToCronAndSimple(CronPreset p) {
        if (p.isCustom())
            return;
        skipSimpleSync = true;
        if (p == CronPreset.EVERY_MINUTE) {
            simpleFrequency.getSelectionModel().select(0);
            simpleHour.setText("0");
            simpleMinute.setText("0");
        } else if (p == CronPreset.EVERY_5_MIN) {
            simpleFrequency.getSelectionModel().select(1);
            simpleHour.setText("0");
            simpleMinute.setText("0");
        } else if (p == CronPreset.EVERY_15_MIN) {
            simpleFrequency.getSelectionModel().select(2);
            simpleHour.setText("0");
            simpleMinute.setText("0");
        } else if (p == CronPreset.EVERY_30_MIN) {
            simpleFrequency.getSelectionModel().select(3);
            simpleHour.setText("0");
            simpleMinute.setText("0");
        } else if (p == CronPreset.EVERY_HOUR) {
            simpleFrequency.getSelectionModel().select(4);
            simpleHour.setText("0");
            simpleMinute.setText("0");
        } else if (p == CronPreset.DAILY_00) {
            simpleFrequency.getSelectionModel().select(5);
            simpleHour.setText("0");
            simpleMinute.setText("0");
        } else if (p == CronPreset.DAILY_09) {
            simpleFrequency.getSelectionModel().select(5);
            simpleHour.setText("9");
            simpleMinute.setText("0");
        } else if (p == CronPreset.WEEKLY_MON_09) {
            simpleFrequency.getSelectionModel().select(6);
            simpleHour.setText("9");
            simpleMinute.setText("0");
            for (javafx.scene.Node n : simpleWeekdays.getChildren())
                if (n instanceof CheckBox)
                    ((CheckBox) n).setSelected(((Integer) ((CheckBox) n).getUserData()) == 2);
        } else if (p == CronPreset.MONTHLY_1ST) {
            simpleFrequency.getSelectionModel().select(7);
            simpleHour.setText("0");
            simpleMinute.setText("0");
            simpleMonthDay.setText("1");
        }
        skipSimpleSync = false;
    }

    private void selectPreset(CronPreset p) {
        skipPresetSync = true;
        presetComboBox.getSelectionModel().select(p.ordinal());
        skipPresetSync = false;
    }

    private void ensureAdvancedTabsBuilt() {
        if (mainTabPane.getTabs().isEmpty()) {
            mainTabPane.getTabs().addAll(
                    buildTab(I18N.get("tab.second"), buildSecondTab()),
                    buildTab(I18N.get("tab.minute"), buildMinuteTab()),
                    buildTab(I18N.get("tab.hour"), buildHourTab()),
                    buildTab(I18N.get("tab.day"), buildDayTab()),
                    buildTab(I18N.get("tab.month"), buildMonthTab()),
                    buildTab(I18N.get("tab.week"), buildWeekTab()),
                    buildTab(I18N.get("tab.year"), buildYearTab()));
            bindDayWeekMutualExclusion();
        }
    }

    /**
     * Quartz 规则：日(day-of-month)与周(day-of-week)互斥，二者只能有一个为“具体值”，另一个必须为 ?。
     * 业界做法：当用户在「日」Tab 选择具体日（范围/步长/W/L/指定日）时，自动把「周」设为「不指定」；
     * 当用户在「周」Tab 选择具体周（范围/#n/L/指定周几）时，自动把「日」设为「不指定」，使 UI 与表达式一致。
     */
    private void bindDayWeekMutualExclusion() {
        if (dayTg == null || weekTg == null)
            return;
        dayTg.selectedToggleProperty().addListener((a, b, c) -> {
            int idx = getRadioIndex(dayTg);
            if (idx >= 2 && idx <= 6) {
                if (weekTg.getToggles().size() > 1 && getRadioIndex(weekTg) != 1)
                    weekTg.getToggles().get(1).setSelected(true);
            }
        });
        weekTg.selectedToggleProperty().addListener((a, b, c) -> {
            int idx = getRadioIndex(weekTg);
            if (idx >= 2 && idx <= 5) {
                if (dayTg.getToggles().size() > 1 && getRadioIndex(dayTg) != 1)
                    dayTg.getToggles().get(1).setSelected(true);
            }
        });
    }

    /** centerScrollPane 已移除，此方法保留为空以避免编译错误的调用点。 */
    private void bindCenterScrollHeight() {
        // 已改为动态 add/remove mainTabPane，无需此方法
    }

    private Tab buildTab(String title, VBox content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(false);
        sp.setStyle("-fx-background-color: transparent;");
        Tab tab = new Tab(title);
        tab.setContent(sp);
        return tab;
    }

    @SuppressWarnings("unchecked")
    private VBox buildSimpleFieldTab(int min, int max, String everyLabel, String cycleLabel, String startOnLabel,
            String specifyLabel,
            ToggleGroup tg, Spinner<Integer>[] cycleSpinners, Spinner<Integer>[] startOnSpinners,
            List<CheckBox> checks) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        RadioButton every = new RadioButton(everyLabel);
        RadioButton cycle = new RadioButton(cycleLabel);
        RadioButton startOn = new RadioButton(startOnLabel);
        RadioButton specify = new RadioButton(specifyLabel);
        every.setToggleGroup(tg);
        cycle.setToggleGroup(tg);
        startOn.setToggleGroup(tg);
        specify.setToggleGroup(tg);
        every.setSelected(true);
        Spinner<Integer> cycleStart = new Spinner<>(min, max, min);
        Spinner<Integer> cycleEnd = new Spinner<>(min, max, Math.min(min + 1, max));
        Spinner<Integer> startOnStart = new Spinner<>(min, max, min);
        Spinner<Integer> startOnEnd = new Spinner<>(1, max - min, 1);
        cycleSpinners[0] = cycleStart;
        cycleSpinners[1] = cycleEnd;
        startOnSpinners[0] = startOnStart;
        startOnSpinners[1] = startOnEnd;
        HBox cycleBox = new HBox(8, cycle, cycleStart, new Label("-"), cycleEnd);
        HBox startOnBox = new HBox(8, startOn, startOnStart, new Label(I18N.get("label.start_from")), startOnEnd,
                new Label(I18N.get("label.interval_unit")));
        FlowPane specifyPane = new FlowPane(4, 4);
        checks.clear();
        for (int i = min; i <= max; i++) {
            CheckBox cb = new CheckBox(String.format("%02d", i));
            cb.setUserData(i);
            checks.add(cb);
            specifyPane.getChildren().add(cb);
        }
        box.getChildren().addAll(every, cycleBox, startOnBox, specify, specifyPane);
        addListeners(tg, cycleStart.valueProperty(), cycleEnd.valueProperty(), startOnStart.valueProperty(),
                startOnEnd.valueProperty(), checks);
        return box;
    }

    private void addListeners(ToggleGroup tg, Object... observables) {
        tg.selectedToggleProperty().addListener(anyAdvancedChange);
        for (Object o : observables) {
            if (o instanceof javafx.beans.property.Property)
                ((javafx.beans.property.Property<?>) o).addListener(anyAdvancedChange);
        }
        if (observables.length > 0 && observables[observables.length - 1] instanceof List) {
            @SuppressWarnings("unchecked")
            List<CheckBox> list = (List<CheckBox>) observables[observables.length - 1];
            for (CheckBox cb : list)
                cb.selectedProperty().addListener(anyAdvancedChange);
        }
    }

    private VBox buildSecondTab() {
        secondTg = new ToggleGroup();
        secondChecks = new ArrayList<>();
        Spinner<Integer>[] cycleSpinners = new Spinner[2];
        Spinner<Integer>[] startOnSpinners = new Spinner[2];
        VBox box = buildSimpleFieldTab(0, 59,
                I18N.get("mode.every_second"), I18N.get("mode.cycle_second"), I18N.get("mode.start_on_second"),
                I18N.get("mode.specify_second"),
                secondTg, cycleSpinners, startOnSpinners, secondChecks);
        secondCycleStart = cycleSpinners[0];
        secondCycleEnd = cycleSpinners[1];
        secondStartOnStart = startOnSpinners[0];
        secondStartOnEnd = startOnSpinners[1];
        return box;
    }

    private VBox buildMinuteTab() {
        minTg = new ToggleGroup();
        minChecks = new ArrayList<>();
        Spinner<Integer>[] cycleSpinners = new Spinner[2];
        Spinner<Integer>[] startOnSpinners = new Spinner[2];
        VBox box = buildSimpleFieldTab(0, 59,
                I18N.get("mode.every_minute"), I18N.get("mode.cycle_minute"), I18N.get("mode.start_on_minute"),
                I18N.get("mode.specify_minute"),
                minTg, cycleSpinners, startOnSpinners, minChecks);
        minCycleStart = cycleSpinners[0];
        minCycleEnd = cycleSpinners[1];
        minStartOnStart = startOnSpinners[0];
        minStartOnEnd = startOnSpinners[1];
        return box;
    }

    private VBox buildHourTab() {
        hourTg = new ToggleGroup();
        hourChecks = new ArrayList<>();
        Spinner<Integer>[] cycleSpinners = new Spinner[2];
        Spinner<Integer>[] startOnSpinners = new Spinner[2];
        VBox box = buildSimpleFieldTab(0, 23,
                I18N.get("mode.every_hour"), I18N.get("mode.cycle_hour"), I18N.get("mode.start_on_hour"),
                I18N.get("mode.specify_hour"),
                hourTg, cycleSpinners, startOnSpinners, hourChecks);
        hourCycleStart = cycleSpinners[0];
        hourCycleEnd = cycleSpinners[1];
        hourStartOnStart = startOnSpinners[0];
        hourStartOnEnd = startOnSpinners[1];
        return box;
    }

    private VBox buildDayTab() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        Label dayWeekHint = new Label(I18N.get("hint.day_week_exclusive"));
        dayWeekHint.setStyle("-fx-text-fill: gray; -fx-font-size: 0.9em;");
        dayWeekHint.setWrapText(true);
        dayTg = new ToggleGroup();
        dayChecks = new ArrayList<>();
        RadioButton every = new RadioButton(I18N.get("mode.every_day"));
        RadioButton unappoint = new RadioButton(I18N.get("mode.unappoint"));
        RadioButton cycle = new RadioButton(I18N.get("mode.cycle_day"));
        RadioButton startOn = new RadioButton(I18N.get("mode.start_on_day"));
        RadioButton workDay = new RadioButton(I18N.get("mode.work_day"));
        RadioButton lastDay = new RadioButton(I18N.get("mode.last_day"));
        RadioButton specify = new RadioButton(I18N.get("mode.specify_day"));
        for (RadioButton r : Arrays.asList(every, unappoint, cycle, startOn, workDay, lastDay, specify))
            r.setToggleGroup(dayTg);
        every.setSelected(true);
        dayCycleStart = new Spinner<>(1, 31, 1);
        dayCycleEnd = new Spinner<>(1, 31, 2);
        dayStartOnStart = new Spinner<>(1, 31, 1);
        dayStartOnEnd = new Spinner<>(1, 31, 1);
        dayWorkDay = new Spinner<>(1, 31, 1);
        HBox cycleBox = new HBox(8, cycle, dayCycleStart, new Label("-"), dayCycleEnd,
                new Label(I18N.get("label.day")));
        HBox startOnBox = new HBox(8, startOn, dayStartOnStart, new Label(I18N.get("label.day")), dayStartOnEnd,
                new Label(I18N.get("label.day_interval")));
        HBox workDayBox = new HBox(8, workDay, dayWorkDay, new Label(I18N.get("mode.work_day_suffix")));
        FlowPane specifyPane = new FlowPane(4, 4);
        for (int i = 1; i <= 31; i++) {
            CheckBox cb = new CheckBox(String.valueOf(i));
            cb.setUserData(i);
            dayChecks.add(cb);
            specifyPane.getChildren().add(cb);
        }
        box.getChildren().addAll(dayWeekHint, every, unappoint, cycleBox, startOnBox, workDayBox, lastDay, specify,
                specifyPane);
        addListeners(dayTg, dayCycleStart.valueProperty(), dayCycleEnd.valueProperty(), dayStartOnStart.valueProperty(),
                dayStartOnEnd.valueProperty(), dayWorkDay.valueProperty(), dayChecks);
        return box;
    }

    private VBox buildMonthTab() {
        monthTg = new ToggleGroup();
        monthChecks = new ArrayList<>();
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        RadioButton every = new RadioButton(I18N.get("mode.every_month"));
        RadioButton unappoint = new RadioButton(I18N.get("mode.unappoint"));
        RadioButton cycle = new RadioButton(I18N.get("mode.cycle_month"));
        RadioButton startOn = new RadioButton(I18N.get("mode.start_on_month"));
        RadioButton specify = new RadioButton(I18N.get("mode.specify_month"));
        for (RadioButton r : Arrays.asList(every, unappoint, cycle, startOn, specify))
            r.setToggleGroup(monthTg);
        every.setSelected(true);
        monthCycleStart = new Spinner<>(1, 12, 1);
        monthCycleEnd = new Spinner<>(1, 12, 2);
        monthStartOnStart = new Spinner<>(1, 12, 1);
        monthStartOnEnd = new Spinner<>(1, 12, 1);
        HBox cycleBox = new HBox(8, cycle, monthCycleStart, new Label("-"), monthCycleEnd,
                new Label(I18N.get("label.month")));
        HBox startOnBox = new HBox(8, startOn, monthStartOnStart, new Label(I18N.get("label.month")), monthStartOnEnd,
                new Label(I18N.get("label.month_interval")));
        FlowPane specifyPane = new FlowPane(4, 4);
        for (int i = 1; i <= 12; i++) {
            CheckBox cb = new CheckBox(String.valueOf(i));
            cb.setUserData(i);
            monthChecks.add(cb);
            specifyPane.getChildren().add(cb);
        }
        box.getChildren().addAll(every, unappoint, cycleBox, startOnBox, specify, specifyPane);
        addListeners(monthTg, monthCycleStart.valueProperty(), monthCycleEnd.valueProperty(),
                monthStartOnStart.valueProperty(), monthStartOnEnd.valueProperty(), monthChecks);
        return box;
    }

    private VBox buildWeekTab() {
        weekTg = new ToggleGroup();
        weekChecks = new ArrayList<>();
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        Label dayWeekHint = new Label(I18N.get("hint.day_week_exclusive"));
        dayWeekHint.setStyle("-fx-text-fill: gray; -fx-font-size: 0.9em;");
        dayWeekHint.setWrapText(true);
        RadioButton every = new RadioButton(I18N.get("mode.every_week"));
        RadioButton unappoint = new RadioButton(I18N.get("mode.unappoint"));
        RadioButton cycle = new RadioButton(I18N.get("mode.cycle_week"));
        RadioButton weekOfDay = new RadioButton(I18N.get("mode.week_of_day"));
        RadioButton lastWeek = new RadioButton(I18N.get("mode.last_week"));
        RadioButton specify = new RadioButton(I18N.get("mode.specify_week"));
        for (RadioButton r : Arrays.asList(every, unappoint, cycle, weekOfDay, lastWeek, specify))
            r.setToggleGroup(weekTg);
        every.setSelected(true);
        weekCycleStart = new Spinner<>(1, 7, 1);
        weekCycleEnd = new Spinner<>(1, 7, 2);
        weekOfDayN = new Spinner<>(1, 4, 1);
        weekOfDayW = new Spinner<>(1, 7, 1);
        weekLastW = new Spinner<>(1, 7, 1);
        HBox cycleBox = new HBox(8, cycle, weekCycleStart, new Label("-"), weekCycleEnd);
        HBox weekOfDayBox = new HBox(8, weekOfDay, weekOfDayN, new Label(I18N.get("label.week_of")), weekOfDayW);
        HBox lastWeekBox = new HBox(8, lastWeek, weekLastW);
        FlowPane specifyPane = new FlowPane(4, 4);
        for (int i = 1; i <= 7; i++) {
            CheckBox cb = new CheckBox(String.valueOf(i));
            cb.setUserData(i);
            weekChecks.add(cb);
            specifyPane.getChildren().add(cb);
        }
        box.getChildren().addAll(dayWeekHint, every, unappoint, cycleBox, weekOfDayBox, lastWeekBox, specify,
                specifyPane);
        addListeners(weekTg, weekCycleStart.valueProperty(), weekCycleEnd.valueProperty(), weekOfDayN.valueProperty(),
                weekOfDayW.valueProperty(), weekLastW.valueProperty(), weekChecks);
        return box;
    }

    private VBox buildYearTab() {
        yearTg = new ToggleGroup();
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        RadioButton unappoint = new RadioButton(I18N.get("mode.unappoint_year"));
        RadioButton every = new RadioButton(I18N.get("mode.every_year"));
        RadioButton cycle = new RadioButton(I18N.get("mode.cycle_year"));
        for (RadioButton r : Arrays.asList(unappoint, every, cycle))
            r.setToggleGroup(yearTg);
        unappoint.setSelected(true);
        yearCycleStart = new Spinner<>(2013, 3000, 2013);
        yearCycleEnd = new Spinner<>(2013, 3000, 2014);
        HBox cycleBox = new HBox(8, cycle, yearCycleStart, new Label("-"), yearCycleEnd);
        box.getChildren().addAll(unappoint, every, cycleBox);
        addListeners(yearTg, yearCycleStart.valueProperty(), yearCycleEnd.valueProperty());
        return box;
    }

    private void bindParseButton() {
        parseFromExpressionButton.setOnAction(e -> parseCronToUI(cronExpressionField.getText()));
    }

    private void bindCronFieldListener() {
        cronExpressionField.focusedProperty().addListener((a, was, now) -> {
            if (!now)
                parseCronToUI(cronExpressionField.getText());
        });
    }

    private void refreshBottomFromCron(String cron) {
        if (cron == null || cron.isEmpty())
            return;
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 6)
            return;
        String sec = parts[0], min = parts[1], hour = parts[2], day = parts[3], month = parts[4], week = parts[5];
        String year = parts.length > 6 ? parts[6] : "";

        fieldSecond.setText(sec);
        fieldMinute.setText(min);
        fieldHour.setText(hour);
        fieldDay.setText(day);
        fieldMonth.setText(month);
        fieldWeek.setText(week);
        fieldYear.setText(year);

        // Sync badge labels in overview bar
        if (badgeSecond != null)
            badgeSecond.setText(sec);
        if (badgeMinute != null)
            badgeMinute.setText(min);
        if (badgeHour != null)
            badgeHour.setText(hour);
        if (badgeDay != null)
            badgeDay.setText(day);
        if (badgeMonth != null)
            badgeMonth.setText(month);
        if (badgeWeek != null)
            badgeWeek.setText(week);
        if (badgeYear != null)
            badgeYear.setText(year.isEmpty() ? "*" : year);

        cronExpressionField.setText(cron);
        refreshNextRunTimes(cron);
        String descKey = CronDescribeUtil.describe(cron);
        descriptionLabel.setText("custom".equals(descKey) ? I18N.get("desc.custom") : I18N.get("preset." + descKey));
    }

    private void refreshNextRunTimes(String cron) {
        List<String> times = CronNextRunCalculator.getNextRunTimes(cron, DEFAULT_NEXT_RUN_COUNT);
        nextRunTimesList.setItems(FXCollections.observableArrayList(times));
    }

    private void refreshCronFromAdvanced() {
        if (mainTabPane.getTabs().isEmpty())
            return;
        String second = getSecondPart();
        String minute = getMinutePart();
        String hour = getHourPart();
        String day = getDayPart();
        String month = getMonthPart();
        String week = getWeekPart();
        String year = getYearPart();
        String cron = new CronExpressionBuilder()
                .second(second).minute(minute).hour(hour).day(day).month(month).week(week)
                .year(year != null ? year : "")
                .build();
        cronExpressionField.setText(cron);
        refreshBottomFromCron(cron);
    }

    private void parseCronToUI(String cron) {
        if (cron == null || (cron = cron.trim()).isEmpty())
            return;
        String normalized = cron.replaceAll("\\s+", " ").trim();
        for (CronPreset p : CronPreset.values()) {
            if (p.isCustom())
                continue;
            if (normalized.equals(p.getCronExpression())) {
                selectPreset(p);
                applyPresetToCronAndSimple(p);
                hideAdvanced();
                refreshBottomFromCron(cron);
                return;
            }
        }
        selectPreset(CronPreset.CUSTOM);
        showAdvanced();
        parseCronToAdvancedOnly(cron);
        refreshBottomFromCron(cron);
    }

    private void parseCronToAdvancedOnly(String cron) {
        if (cron == null || (cron = cron.trim()).isEmpty())
            return;
        String[] parts = cron.split("\\s+");
        if (parts.length < 6)
            return;
        Platform.runLater(() -> {
            setSecondPart(parts[0]);
            setMinutePart(parts[1]);
            setHourPart(parts[2]);
            setDayPart(parts[3]);
            setMonthPart(parts[4]);
            setWeekPart(parts[5]);
            if (parts.length > 6)
                setYearPart(parts[6]);
            else
                setYearPart("");
        });
    }

    // --- get/set parts for advanced tabs (same as before) ---
    private String getSecondPart() {
        return getSimplePart(secondTg, secondCycleStart, secondCycleEnd, secondStartOnStart, secondStartOnEnd,
                secondChecks, 0, 59);
    }

    private String getMinutePart() {
        return getSimplePart(minTg, minCycleStart, minCycleEnd, minStartOnStart, minStartOnEnd, minChecks, 0, 59);
    }

    private String getHourPart() {
        return getSimplePart(hourTg, hourCycleStart, hourCycleEnd, hourStartOnStart, hourStartOnEnd, hourChecks, 0, 23);
    }

    private String getSimplePart(ToggleGroup tg, Spinner<Integer> cStart, Spinner<Integer> cEnd,
            Spinner<Integer> sStart, Spinner<Integer> sEnd, List<CheckBox> checks, int min, int max) {
        if (tg == null)
            return "*";
        Toggle t = tg.getSelectedToggle();
        if (t == null)
            return "*";
        int idx = getRadioIndex(tg);
        if (idx == 0)
            return "*";
        if (idx == 1)
            return cStart.getValue() + "-" + cEnd.getValue();
        if (idx == 2)
            return sStart.getValue() + "/" + sEnd.getValue();
        if (idx == 3) {
            List<Integer> sel = checks.stream().filter(CheckBox::isSelected).map(cb -> (Integer) cb.getUserData())
                    .sorted().collect(Collectors.toList());
            if (sel.isEmpty())
                return "*";
            if (sel.size() == max - min + 1)
                return "*";
            return sel.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return "*";
    }

    private int getRadioIndex(ToggleGroup tg) {
        List<Toggle> list = tg.getToggles();
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).isSelected())
                return i;
        return 0;
    }

    private String getDayPart() {
        if (dayTg == null)
            return "*";
        int idx = getRadioIndex(dayTg);
        if (idx == 0)
            return "*";
        if (idx == 1)
            return "?";
        if (idx == 2)
            return dayCycleStart.getValue() + "-" + dayCycleEnd.getValue();
        if (idx == 3)
            return dayStartOnStart.getValue() + "/" + dayStartOnEnd.getValue();
        if (idx == 4)
            return dayWorkDay.getValue() + "W";
        if (idx == 5)
            return "L";
        if (idx == 6) {
            List<Integer> sel = dayChecks.stream().filter(CheckBox::isSelected).map(cb -> (Integer) cb.getUserData())
                    .sorted().collect(Collectors.toList());
            if (sel.isEmpty())
                return "?";
            if (sel.size() == 31)
                return "*";
            return sel.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return "*";
    }

    private String getMonthPart() {
        if (monthTg == null)
            return "*";
        int idx = getRadioIndex(monthTg);
        if (idx == 0)
            return "*";
        if (idx == 1)
            return "*";
        if (idx == 2)
            return monthCycleStart.getValue() + "-" + monthCycleEnd.getValue();
        if (idx == 3)
            return monthStartOnStart.getValue() + "/" + monthStartOnEnd.getValue();
        if (idx == 4) {
            List<Integer> sel = monthChecks.stream().filter(CheckBox::isSelected).map(cb -> (Integer) cb.getUserData())
                    .sorted().collect(Collectors.toList());
            if (sel.isEmpty())
                return "*";
            if (sel.size() == 12)
                return "*";
            return sel.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return "*";
    }

    private String getWeekPart() {
        if (weekTg == null)
            return "?";
        int idx = getRadioIndex(weekTg);
        if (idx == 0)
            return "*";
        if (idx == 1)
            return "?";
        if (idx == 2)
            return weekCycleStart.getValue() + "-" + weekCycleEnd.getValue();
        if (idx == 3)
            return weekOfDayN.getValue() + "#" + weekOfDayW.getValue();
        if (idx == 4)
            return weekLastW.getValue() + "L";
        if (idx == 5) {
            List<Integer> sel = weekChecks.stream().filter(CheckBox::isSelected).map(cb -> (Integer) cb.getUserData())
                    .sorted().collect(Collectors.toList());
            if (sel.isEmpty())
                return "?";
            if (sel.size() == 7)
                return "*";
            return sel.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return "?";
    }

    private String getYearPart() {
        if (yearTg == null)
            return "";
        int idx = getRadioIndex(yearTg);
        if (idx == 0)
            return "";
        if (idx == 1)
            return "*";
        if (idx == 2)
            return yearCycleStart.getValue() + "-" + yearCycleEnd.getValue();
        return "";
    }

    private void setSimplePart(String val, ToggleGroup tg, Spinner<Integer> cStart, Spinner<Integer> cEnd,
            Spinner<Integer> sStart, Spinner<Integer> sEnd, List<CheckBox> checks, int min, int max) {
        if (tg == null)
            return;
        if ("*".equals(val)) {
            tg.getToggles().get(0).setSelected(true);
            return;
        }
        if (val.contains("-")) {
            String[] a = val.split("-");
            tg.getToggles().get(1).setSelected(true);
            try {
                cStart.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                cEnd.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
            return;
        }
        if (val.contains("/")) {
            String[] a = val.split("/");
            tg.getToggles().get(2).setSelected(true);
            try {
                sStart.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                sEnd.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
            return;
        }
        tg.getToggles().get(3).setSelected(true);
        Set<String> set = new HashSet<>(Arrays.asList(val.split(",")));
        for (CheckBox cb : checks)
            cb.setSelected(set.contains(String.valueOf(cb.getUserData())));
    }

    private void setSecondPart(String val) {
        setSimplePart(val, secondTg, secondCycleStart, secondCycleEnd, secondStartOnStart, secondStartOnEnd,
                secondChecks, 0, 59);
    }

    private void setMinutePart(String val) {
        setSimplePart(val, minTg, minCycleStart, minCycleEnd, minStartOnStart, minStartOnEnd, minChecks, 0, 59);
    }

    private void setHourPart(String val) {
        setSimplePart(val, hourTg, hourCycleStart, hourCycleEnd, hourStartOnStart, hourStartOnEnd, hourChecks, 0, 23);
    }

    private void setDayPart(String val) {
        if (dayTg == null)
            return;
        if ("*".equals(val)) {
            dayTg.getToggles().get(0).setSelected(true);
            return;
        }
        if ("?".equals(val)) {
            dayTg.getToggles().get(1).setSelected(true);
            return;
        }
        if (val.contains("-")) {
            String[] a = val.split("-");
            dayTg.getToggles().get(2).setSelected(true);
            try {
                dayCycleStart.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                dayCycleEnd.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
            return;
        }
        if (val.contains("/")) {
            String[] a = val.split("/");
            dayTg.getToggles().get(3).setSelected(true);
            try {
                dayStartOnStart.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                dayStartOnEnd.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
            return;
        }
        if (val.endsWith("W")) {
            dayTg.getToggles().get(4).setSelected(true);
            try {
                dayWorkDay.getValueFactory().setValue(Integer.parseInt(val.substring(0, val.length() - 1)));
            } catch (Exception ignored) {
            }
            return;
        }
        if ("L".equals(val)) {
            dayTg.getToggles().get(5).setSelected(true);
            return;
        }
        dayTg.getToggles().get(6).setSelected(true);
        Set<String> set = new HashSet<>(Arrays.asList(val.split(",")));
        for (CheckBox cb : dayChecks)
            cb.setSelected(set.contains(String.valueOf(cb.getUserData())));
    }

    private void setMonthPart(String val) {
        if (monthTg == null)
            return;
        if ("*".equals(val)) {
            monthTg.getToggles().get(0).setSelected(true);
            return;
        }
        if ("?".equals(val)) {
            monthTg.getToggles().get(1).setSelected(true);
            return;
        }
        if (val.contains("-")) {
            String[] a = val.split("-");
            monthTg.getToggles().get(2).setSelected(true);
            try {
                monthCycleStart.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                monthCycleEnd.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
            return;
        }
        if (val.contains("/")) {
            String[] a = val.split("/");
            monthTg.getToggles().get(3).setSelected(true);
            try {
                monthStartOnStart.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                monthStartOnEnd.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
            return;
        }
        monthTg.getToggles().get(4).setSelected(true);
        Set<String> set = new HashSet<>(Arrays.asList(val.split(",")));
        for (CheckBox cb : monthChecks)
            cb.setSelected(set.contains(String.valueOf(cb.getUserData())));
    }

    private void setWeekPart(String val) {
        if (weekTg == null)
            return;
        if ("*".equals(val)) {
            weekTg.getToggles().get(0).setSelected(true);
            return;
        }
        if ("?".equals(val)) {
            weekTg.getToggles().get(1).setSelected(true);
            return;
        }
        if (val.contains("-") && !val.endsWith("L")) {
            String[] a = val.split("-");
            weekTg.getToggles().get(2).setSelected(true);
            try {
                weekCycleStart.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                weekCycleEnd.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
            return;
        }
        if (val.contains("#")) {
            String[] a = val.split("#");
            weekTg.getToggles().get(3).setSelected(true);
            try {
                weekOfDayN.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                weekOfDayW.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
            return;
        }
        if (val.endsWith("L")) {
            weekTg.getToggles().get(4).setSelected(true);
            try {
                weekLastW.getValueFactory().setValue(Integer.parseInt(val.substring(0, val.length() - 1)));
            } catch (Exception ignored) {
            }
            return;
        }
        weekTg.getToggles().get(5).setSelected(true);
        Set<String> set = new HashSet<>(Arrays.asList(val.split(",")));
        for (CheckBox cb : weekChecks)
            cb.setSelected(set.contains(String.valueOf(cb.getUserData())));
    }

    private void setYearPart(String val) {
        if (yearTg == null)
            return;
        if (val == null || val.isEmpty()) {
            yearTg.getToggles().get(0).setSelected(true);
            return;
        }
        if ("*".equals(val)) {
            yearTg.getToggles().get(1).setSelected(true);
            return;
        }
        if (val.contains("-")) {
            String[] a = val.split("-");
            yearTg.getToggles().get(2).setSelected(true);
            try {
                yearCycleStart.getValueFactory().setValue(Integer.parseInt(a[0].trim()));
                yearCycleEnd.getValueFactory().setValue(Integer.parseInt(a[1].trim()));
            } catch (Exception ignored) {
            }
        }
    }
}
