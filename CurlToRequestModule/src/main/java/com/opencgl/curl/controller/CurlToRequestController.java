package com.opencgl.curl.controller;

import com.opencgl.curl.i18n.I18N;
import com.opencgl.curl.util.BodyFormatter;
import com.opencgl.curl.util.CurlParser;
import com.opencgl.curl.views.CurlToRequestView;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import okhttp3.*;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class CurlToRequestController extends CurlToRequestView implements Initializable {

    private CurlParser.ParsedCurl lastParsed;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initI18n();
        parseButton.setOnAction(e -> parseAction());
        sendButton.setOnAction(e -> sendAction());
        clearButton.setOnAction(e -> clearAction());
    }

    private void initI18n() {
        if (curlInputLabel != null) curlInputLabel.textProperty().bind(I18N.getBinding("label.curl_input"));
        parsedResultLabel.textProperty().bind(I18N.getBinding("label.parsed_result"));
        responseLabel.textProperty().bind(I18N.getBinding("label.response"));
        parseButton.textProperty().bind(I18N.getBinding("button.parse"));
        sendButton.textProperty().bind(I18N.getBinding("button.send"));
        clearButton.textProperty().bind(I18N.getBinding("button.clear"));
        statusLabel.setText(I18N.get("label.ready"));
    }

    @FXML
    private void parseAction() {
        String curl = curlInputArea.getText();
        if (curl == null || curl.isBlank()) {
            setStatus(I18N.get("msg.curl_empty"));
            return;
        }
        try {
            lastParsed = CurlParser.parse(curl);
            StringBuilder sb = new StringBuilder();
            sb.append(I18N.get("label.method")).append(": ").append(lastParsed.getMethod()).append("\n");
            sb.append(I18N.get("label.url")).append(": ").append(lastParsed.getUrl()).append("\n");
            sb.append(I18N.get("label.headers")).append(":\n");
            for (Map.Entry<String, String> e : lastParsed.getHeaders().entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            if (lastParsed.getBody() != null && !lastParsed.getBody().isEmpty()) {
                String contentType = getHeaderValueIgnoreCase(lastParsed.getHeaders(), "Content-Type");
                String formattedBody = BodyFormatter.formatByContentType(contentType, lastParsed.getBody());
                sb.append(I18N.get("label.body")).append(":\n").append(formattedBody);
            }
            parsedResultArea.setText(sb.toString());
            setStatus(I18N.get("msg.parse_ok"));
        } catch (Exception e) {
            parsedResultArea.setText(e.getMessage());
            setStatus(I18N.get("msg.parse_fail", e.getMessage()));
        }
    }

    @FXML
    private void sendAction() {
        if (lastParsed == null) {
            parseAction();
            if (lastParsed == null) return;
        }
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        Request.Builder reqBuilder = new Request.Builder().url(lastParsed.getUrl());
        for (Map.Entry<String, String> e : lastParsed.getHeaders().entrySet()) {
            reqBuilder.addHeader(e.getKey(), e.getValue());
        }
        String method = lastParsed.getMethod();
        RequestBody body = null;
        if (lastParsed.getBody() != null && !lastParsed.getBody().isEmpty()) {
            String contentType = lastParsed.getHeaders().entrySet().stream()
                    .filter(h -> "Content-Type".equalsIgnoreCase(h.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("application/json; charset=utf-8");
            body = RequestBody.create(lastParsed.getBody(), MediaType.parse(contentType));
        }
        RequestBody emptyBody = RequestBody.create("", MediaType.parse("text/plain; charset=utf-8"));
        switch (method) {
            case "GET" -> reqBuilder.get();
            case "POST" -> reqBuilder.post(body != null ? body : emptyBody);
            case "PUT" -> reqBuilder.put(body != null ? body : emptyBody);
            case "PATCH" -> reqBuilder.patch(body != null ? body : emptyBody);
            case "DELETE" -> reqBuilder.delete(body != null ? body : emptyBody);
            default -> reqBuilder.method(method, body != null ? body : emptyBody);
        }
        try (Response response = client.newCall(reqBuilder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            String responseContentType = response.header("Content-Type");
            String formattedBody = BodyFormatter.formatByContentType(responseContentType, responseBody);
            responseArea.setText("HTTP " + response.code() + " " + response.message() + "\n\n" + formattedBody);
            setStatus(I18N.get("msg.send_ok"));
        } catch (Exception e) {
            responseArea.setText(e.toString());
            setStatus(I18N.get("msg.send_fail", e.getMessage()));
        } finally {
            closeClient(client);
        }
    }

    private static void closeClient(OkHttpClient client) {
        try {
            client.dispatcher().cancelAll();
        } catch (Exception ignored) {
        }
        try {
            client.dispatcher().executorService().shutdown();
        } catch (Exception ignored) {
        }
        try {
            client.connectionPool().evictAll();
        } catch (Exception ignored) {
        }
        if (client.cache() != null) {
            try {
                client.cache().close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String getHeaderValueIgnoreCase(Map<String, String> headers, String headerName) {
        if (headers == null || headers.isEmpty() || headerName == null || headerName.isBlank()) {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && headerName.equalsIgnoreCase(e.getKey().trim())) {
                return e.getValue();
            }
        }
        return null;
    }

    @FXML
    private void clearAction() {
        curlInputArea.clear();
        parsedResultArea.clear();
        responseArea.clear();
        lastParsed = null;
        setStatus(I18N.get("msg.cleared"));
    }

    private void setStatus(String message) {
        if (statusLabel != null) statusLabel.setText(message);
    }
}
