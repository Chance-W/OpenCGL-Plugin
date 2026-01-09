package com.opencgl.pingscan.controller;

import com.opencgl.pingscan.i18n.I18N;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PingScanController implements Initializable {

    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ping-scan-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Future<?>> backgroundTasks = new CopyOnWriteArrayList<>();
    private volatile Process currentProcess;
    private volatile boolean disposed;

    @FXML private TextField hostField;
    @FXML private TextField countField;
    @FXML private TextField timeoutField;
    @FXML private TextField portField;
    @FXML private Button btnPing;
    @FXML private Button btnPortCheck;
    @FXML private TextArea resultArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
    }

    private void initI18n() {
        if (resultArea != null) resultArea.setText(I18N.get("status.ready"));
    }

    @FXML
    private void onPing() {
        String host = hostField.getText();
        if (host == null || host.isBlank()) {
            resultArea.setText(I18N.get("status.ready"));
            return;
        }
        host = host.trim();
        int count = 4;
        int timeoutSec = 5;
        try {
            String c = countField.getText();
            if (c != null && !c.isBlank()) count = Math.max(1, Math.min(100, Integer.parseInt(c.trim())));
        } catch (NumberFormatException ignored) {}
        try {
            String t = timeoutField.getText();
            if (t != null && !t.isBlank()) timeoutSec = Math.max(1, Math.min(60, Integer.parseInt(t.trim())));
        } catch (NumberFormatException ignored) {}

        btnPing.setDisable(true);
        resultArea.setText(I18N.get("status.running"));
        final int cnt = count;
        final int timeout = timeoutSec;

        String finalHost = host;
        submitBackground(() -> {
            String output;
            try {
                output = runPing(finalHost, cnt, timeout);
            } catch (Exception e) {
                output = I18N.get("status.error", e.getMessage());
            }
            String finalOutput = output;
            Platform.runLater(() -> {
                if (disposed) return;
                resultArea.setText(finalOutput);
                btnPing.setDisable(false);
            });
        });
    }

    private String runPing(String host, int count, int timeoutSec) {
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean isWindows = os.contains("win");
        ProcessBuilder pb;
        if (isWindows) {
            pb = new ProcessBuilder("ping", "-n", String.valueOf(count), "-w", String.valueOf(timeoutSec * 1000), host);
        } else {
            pb = new ProcessBuilder("ping", "-c", String.valueOf(count), "-W", String.valueOf(timeoutSec), host);
        }
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            currentProcess = p;
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = p.waitFor();
            if (exit != 0 && out.isBlank()) {
                return tryInetAddressReachable(host, timeoutSec);
            }
            return out;
        } catch (Exception e) {
            return tryInetAddressReachable(host, timeoutSec);
        } finally {
            currentProcess = null;
        }
    }

    private String tryInetAddressReachable(String host, int timeoutSec) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            int timeoutMs = Math.min(5000, timeoutSec * 1000);
            boolean reachable = addr.isReachable(timeoutMs);
            return (reachable ? "Reachable: " + addr.getHostAddress() : "Unreachable: " + host) + "\n(InetAddress.isReachable)";
        } catch (Exception e) {
            return I18N.get("status.error", e.getMessage());
        }
    }

    @FXML
    private void onPortCheck() {
        String host = hostField.getText();
        if (host == null || host.isBlank()) {
            resultArea.setText(I18N.get("status.ready"));
            return;
        }
        host = host.trim();
        String portStr = portField.getText();
        if (portStr == null || portStr.isBlank()) {
            resultArea.setText(I18N.get("error.enterPort"));
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portStr.trim());
            if (port < 1 || port > 65535) throw new NumberFormatException("Port 1-65535");
        } catch (NumberFormatException e) {
            resultArea.setText(I18N.get("error.invalidPort"));
            return;
        }

        btnPortCheck.setDisable(true);
        resultArea.setText(I18N.get("status.running"));

        String finalHost = host;
        submitBackground(() -> {
            String msg;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(finalHost, port), 5000);
                msg = "Port " + port + " open: " + finalHost + ":" + port + "\n";
            } catch (Exception e) {
                msg = I18N.get("status.error", e.getMessage()) + " (port " + port + ")";
            }
            String finalMsg = msg;
            Platform.runLater(() -> {
                if (disposed) return;
                resultArea.setText(finalMsg);
                btnPortCheck.setDisable(false);
            });
        });
    }

    private void submitBackground(Runnable action) {
        if (disposed) return;
        backgroundTasks.removeIf(Future::isDone);
        backgroundTasks.add(backgroundExecutor.submit(action));
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Future<?> task : backgroundTasks) {
            try {
                task.cancel(true);
            } catch (Exception ignored) {
            }
        }
        backgroundTasks.clear();
        Process process = currentProcess;
        if (process != null) {
            try {
                process.destroy();
                if (process.isAlive()) process.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
        try {
            backgroundExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
    }
}
