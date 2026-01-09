package com.opencgl.cron.service;

/**
 * Returns a short human-readable description for common cron patterns.
 * Used for display only; complex expressions may return "custom" or the expression prefix.
 */
public final class CronDescribeUtil {

    private CronDescribeUtil() {}

    /**
     * Returns a brief description of the cron expression for display.
     * Normalizes whitespace before matching.
     */
    public static String describe(String cron) {
        if (cron == null || (cron = cron.trim()).isEmpty()) {
            return "";
        }
        String n = cron.replaceAll("\\s+", " ").trim();
        if (n.equals("0 * * * * ? *")) return "every_minute";
        if (n.equals("0 0/5 * * * ? *")) return "every_5min";
        if (n.equals("0 0/15 * * * ? *")) return "every_15min";
        if (n.equals("0 0/30 * * * ? *")) return "every_30min";
        if (n.equals("0 0 * * * ? *")) return "every_hour";
        if (n.equals("0 0 0 * * ? *")) return "daily_00";
        if (n.equals("0 0 9 * * ? *")) return "daily_09";
        if (n.equals("0 0 9 ? * 2 *")) return "weekly_mon_09";
        if (n.equals("0 0 0 1 * ? *")) return "monthly_1st";
        if (n.matches("0 0 \\d{1,2} \\* \\* \\? \\*")) {
            String hour = n.split("\\s+")[2];
            return "daily_" + hour + ":00";
        }
        if (n.matches("0 0 \\d{1,2} \\? \\* [1-7] \\*")) {
            String[] p = n.split("\\s+");
            return "weekly_" + p[5] + "_" + p[2] + ":00";
        }
        return "custom";
    }
}
