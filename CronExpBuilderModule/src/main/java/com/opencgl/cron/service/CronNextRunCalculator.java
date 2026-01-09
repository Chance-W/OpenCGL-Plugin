package com.opencgl.cron.service;

import org.quartz.CronExpression;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Computes next N run times for a Quartz cron expression.
 */
public final class CronNextRunCalculator {

    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    /**
     * Returns the next {@code count} run times after now, or fewer if the expression has no more.
     *
     * @param cronExpression Quartz 6- or 7-field cron expression
     * @param count          maximum number of times to return
     * @return list of formatted date strings; empty if expression is invalid
     */
    public static List<String> getNextRunTimes(String cronExpression, int count) {
        List<String> result = new ArrayList<>();
        if (cronExpression == null || (cronExpression = cronExpression.trim()).isEmpty()) {
            return result;
        }
        try {
            CronExpression cron = new CronExpression(cronExpression);
            cron.setTimeZone(TimeZone.getDefault());
            Date from = new Date();
            for (int i = 0; i < count; i++) {
                Date next = cron.getNextValidTimeAfter(from);
                if (next == null) break;
                result.add(FORMAT.format(next));
                from = new Date(next.getTime() + 1000);
            }
        } catch (Exception e) {
            result.add("Invalid expression");
        }
        return result;
    }
}
