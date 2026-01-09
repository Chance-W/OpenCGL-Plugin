package com.opencgl.cron.service;

import java.util.Objects;

/**
 * Builds a Quartz-style cron expression (7 fields: second minute hour day month week year)
 * from per-field part strings. Ensures day/week mutual exclusion (one must be ? when the other is set).
 */
public final class CronExpressionBuilder {

    private String second = "*";
    private String minute = "*";
    private String hour = "*";
    private String day = "*";
    private String month = "*";
    private String week = "?";
    private String year = "";

    public CronExpressionBuilder second(String s) { this.second = emptyToStar(Objects.requireNonNull(s)); return this; }
    public CronExpressionBuilder minute(String s) { this.minute = emptyToStar(Objects.requireNonNull(s)); return this; }
    public CronExpressionBuilder hour(String s)   { this.hour   = emptyToStar(Objects.requireNonNull(s)); return this; }
    public CronExpressionBuilder day(String s)   { this.day   = emptyToStar(Objects.requireNonNull(s)); return this; }
    public CronExpressionBuilder month(String s)  { this.month = emptyToStar(Objects.requireNonNull(s)); return this; }
    public CronExpressionBuilder week(String s)   { this.week  = s == null || s.isEmpty() ? "?" : s; return this; }
    public CronExpressionBuilder year(String s)   { this.year  = s == null ? "" : s.trim(); return this; }

    private static String emptyToStar(String s) {
        String t = s.trim();
        return t.isEmpty() ? "*" : t;
    }

    /**
     * Quartz: day-of-month and day-of-week must not both be set; one must be ?.
     * When day is specified (not ? and not *), set week = ?; when week is specified, set day = ?.
     */
    public String build() {
        String d = day.trim().isEmpty() ? "*" : day;
        String w = week.trim().isEmpty() ? "?" : week;
        boolean daySet = !"?".equals(d) && !"*".equals(d);
        boolean weekSet = !"?".equals(w) && !"*".equals(w);
        if (daySet && weekSet) {
            w = "?";  // day takes precedence
        } else if (weekSet) {
            d = "?";  // when week is specified, day must be ?
        } else if (daySet) {
            w = "?";
        }
        // Quartz order: second minute hour dayOfMonth month dayOfWeek [year] (only day and week may be ?)
        StringBuilder sb = new StringBuilder();
        sb.append(second).append(' ').append(minute).append(' ').append(hour).append(' ')
          .append(d).append(' ').append(month).append(' ').append(w);
        if (year != null && !year.isEmpty()) {
            sb.append(' ').append(year);
        }
        return sb.toString();
    }
}
