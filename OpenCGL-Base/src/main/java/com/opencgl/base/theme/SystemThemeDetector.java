package com.opencgl.base.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Detects operating-system appearance without introducing a native UI dependency. */
public final class SystemThemeDetector implements AutoCloseable {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-system-theme-detector");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean lastDark = isDarkMode();

    public boolean isDark() {
        return lastDark;
    }

    public void listen(Consumer<Boolean> listener) {
        scheduler.scheduleWithFixedDelay(() -> {
            boolean dark = isDarkMode();
            if (dark != lastDark) {
                lastDark = dark;
                listener.accept(dark);
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    static boolean isDarkAppearance(String appearance) {
        return appearance != null && appearance.trim().toLowerCase(Locale.ROOT).contains("dark");
    }

    private static boolean isDarkMode() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) return false;
        Process process = null;
        try {
            process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0 && isDarkAppearance(output);
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroy();
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    boolean isStopped() {
        return scheduler.isShutdown();
    }
}
