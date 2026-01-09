package com.opencgl.cron.service;

/**
 * Built-in schedule presets. Each has a fixed Quartz 7-field cron and an i18n key for display name.
 */
public enum CronPreset {
    EVERY_MINUTE("0 * * * * ? *", "preset.every_minute"),
    EVERY_5_MIN("0 0/5 * * * ? *", "preset.every_5min"),
    EVERY_15_MIN("0 0/15 * * * ? *", "preset.every_15min"),
    EVERY_30_MIN("0 0/30 * * * ? *", "preset.every_30min"),
    EVERY_HOUR("0 0 * * * ? *", "preset.every_hour"),
    DAILY_00("0 0 0 * * ? *", "preset.daily_00"),
    DAILY_09("0 0 9 * * ? *", "preset.daily_09"),
    WEEKLY_MON_09("0 0 9 ? * 2 *", "preset.weekly_mon_09"),
    MONTHLY_1ST("0 0 0 1 * ? *", "preset.monthly_1st"),
    CUSTOM(null, "preset.custom");

    private final String cronExpression;
    private final String i18nKey;

    CronPreset(String cronExpression, String i18nKey) {
        this.cronExpression = cronExpression;
        this.i18nKey = i18nKey;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getI18nKey() {
        return i18nKey;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }
}
