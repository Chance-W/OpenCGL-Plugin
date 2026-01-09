package com.opencgl.tcpudp.controller;

import com.opencgl.tcpudp.i18n.I18N;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.net.URL;

public class TcpUdpToolController implements Initializable {

    private static final String TCP = "TCP";
    private static final String UDP = "UDP";

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private ComboBox<String> protocolCombo;
    @FXML private javafx.scene.control.Button btnConnectOrSend;
    @FXML private TextArea sendArea;
    @FXML private TextArea receiveArea;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tcpudp-worker");
        t.setDaemon(true);
        return t;
    });
    private Socket tcpSocket;
    private volatile boolean tcpConnected;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initI18n();
        protocolCombo.setItems(FXCollections.observableArrayList(TCP, UDP));
        protocolCombo.getSelectionModel().select(TCP);
        protocolCombo.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> updateButtonText());
        updateButtonText();
    }

    private void initI18n() {
        // Static texts are in FXML with %key
    }

    private void updateButtonText() {
        boolean isTcp = TCP.equals(protocolCombo.getSelectionModel().getSelectedItem());
        btnConnectOrSend.setText(isTcp ? I18N.get("button.connect") : I18N.get("button.send"));
    }

    @FXML
    private void onConnectOrSend() {
        String host = hostField.getText() != null ? hostField.getText().trim() : "";
        if (host.isEmpty()) host = "127.0.0.1";
        int port;
        try {
            port = Integer.parseInt(portField.getText() != null ? portField.getText().trim() : "0");
        } catch (NumberFormatException e) {
            appendReceive("Invalid port.");
            return;
        }
        boolean isTcp = TCP.equals(protocolCombo.getSelectionModel().getSelectedItem());
        if (isTcp) {
            if (tcpConnected) {
                sendTcp();
            } else {
                connectTcp(host, port);
            }
        } else {
            sendUdp(host, port);
        }
    }

    private void connectTcp(String host, int port) {
        executor.execute(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 5000);
                tcpSocket = s;
                tcpConnected = true;
                Platform.runLater(() -> {
                    btnConnectOrSend.setText(I18N.get("button.send"));
                    appendReceive("Connected to " + host + ":" + port);
                });
                // Simple read loop
                byte[] buf = new byte[4096];
                while (tcpConnected && tcpSocket != null && !tcpSocket.isClosed()) {
                    int n = tcpSocket.getInputStream().read(buf);
                    if (n <= 0) break;
                    String line = new String(buf, 0, n, StandardCharsets.UTF_8);
                    String finalLine = line;
                    Platform.runLater(() -> appendReceive(finalLine));
                }
            } catch (IOException e) {
                Platform.runLater(() -> appendReceive("Error: " + e.getMessage()));
            } finally {
                tcpConnected = false;
                Platform.runLater(this::updateButtonText);
            }
        });
    }

    private void sendTcp() {
        String text = sendArea.getText();
        if (text == null) text = "";
        byte[] data = parseSendData(text);
        if (tcpSocket == null || !tcpSocket.isConnected()) {
            appendReceive("Not connected.");
            return;
        }
        executor.execute(() -> {
            try {
                tcpSocket.getOutputStream().write(data);
                tcpSocket.getOutputStream().flush();
            } catch (IOException e) {
                Platform.runLater(() -> appendReceive("Send error: " + e.getMessage()));
            }
        });
    }

    private void sendUdp(String host, int port) {
        String text = sendArea.getText();
        if (text == null) text = "";
        byte[] data = parseSendData(text);
        executor.execute(() -> {
            try (DatagramSocket ds = new DatagramSocket()) {
                DatagramPacket p = new DatagramPacket(data, data.length, new InetSocketAddress(host, port));
                ds.send(p);
                Platform.runLater(() -> appendReceive("UDP sent " + data.length + " bytes to " + host + ":" + port));
            } catch (IOException e) {
                Platform.runLater(() -> appendReceive("UDP error: " + e.getMessage()));
            }
        });
    }

    private byte[] parseSendData(String text) {
        text = text.trim();
        if (text.isEmpty()) return new byte[0];
        if (text.startsWith("0x") || text.matches("^[0-9A-Fa-f\\s]+$")) {
            String hex = text.replaceAll("\\s+", "").replace("0x", "");
            if (hex.length() % 2 != 0) hex = "0" + hex;
            byte[] out = new byte[hex.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private void appendReceive(String s) {
        receiveArea.appendText(s + "\n");
    }

    public void dispose() {
        tcpConnected = false;
        Socket socket = tcpSocket;
        tcpSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The executor is stopped below; there is no remaining recovery action.
            }
        }
        executor.shutdownNow();
    }
}
