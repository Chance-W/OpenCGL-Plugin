package com.opencgl.graphql.controller;

import com.opencgl.graphql.i18n.I18N;
import com.opencgl.graphql.service.GraphQLService;
import com.opencgl.graphql.service.GraphQLService.GraphQLResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;

/**
 * GraphQL 测试控制器
 *
 * @author OpenCGL
 */
public class GraphQLController implements Initializable {

    @FXML private TextField endpointField;
    @FXML private TextField headerKeyField;
    @FXML private TextField headerValueField;
    
    @FXML private TextArea queryArea;
    @FXML private TextArea variablesArea;
    @FXML private TextArea responseArea;
    @FXML private Label statusLabel;

    private final GraphQLService graphqlService = new GraphQLService();
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "graphql-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Future<?>> backgroundTasks = new CopyOnWriteArrayList<>();
    private volatile boolean disposed;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        endpointField.setText("http://localhost:8080/graphql");
        
        // 示例查询
        queryArea.setText("""
            query {
              users {
                id
                name
                email
              }
            }
            """);
        initI18n();
    }

    private void initI18n() {
        // FXML text is resolved via %key; statusLabel is set at runtime with I18N in handlers
    }

    @FXML
    private void onExecute() {
        String endpoint = endpointField.getText();
        String query = queryArea.getText();
        String variables = variablesArea.getText();
        
        if (endpoint.isEmpty() || query.isEmpty()) {
            statusLabel.setText(I18N.get("msg.enter_endpoint_query"));
            return;
        }
        
        graphqlService.setEndpoint(endpoint);
        statusLabel.setText(I18N.get("status.executing"));
        responseArea.setText(I18N.get("status.requesting"));
        
        Map<String, String> headers = new HashMap<>();
        String key = headerKeyField.getText();
        String value = headerValueField.getText();
        if (key != null && !key.isEmpty()) {
            headers.put(key, value);
        }
        
        submitBackground(() -> {
            try {
                GraphQLResult result = graphqlService.execute(query, variables, headers);
                Platform.runLater(() -> {
                    if (disposed) return;
                    responseArea.setText(result.body());
                    statusLabel.setText(I18N.get("status.ok", result.statusCode(), result.elapsed()));
                    statusLabel.setStyle(result.success() ? "-fx-text-fill: #4caf50;" : "-fx-text-fill: #ff9800;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    responseArea.setText(I18N.get("msg.request_failed", e.getMessage()));
                    statusLabel.setText(I18N.get("status.error"));
                    statusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onIntrospect() {
        String endpoint = endpointField.getText();
        if (endpoint.isEmpty()) {
            statusLabel.setText(I18N.get("msg.enter_endpoint"));
            return;
        }
        
        graphqlService.setEndpoint(endpoint);
        statusLabel.setText(I18N.get("status.executing"));
        
        Map<String, String> headers = new HashMap<>();
        String key = headerKeyField.getText();
        String value = headerValueField.getText();
        if (key != null && !key.isEmpty()) {
            headers.put(key, value);
        }
        
        submitBackground(() -> {
            try {
                String schema = graphqlService.introspect(headers);
                Platform.runLater(() -> {
                    if (disposed) return;
                    responseArea.setText(schema);
                    statusLabel.setText(I18N.get("status.schema_ok"));
                    statusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    responseArea.setText(I18N.get("msg.introspect_failed", e.getMessage()));
                    statusLabel.setText(I18N.get("status.error"));
                    statusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onClear() {
        queryArea.clear();
        variablesArea.clear();
        responseArea.clear();
        statusLabel.setText("");
    }

    @FXML
    private void onFormatQuery() {
        // 简单缩进格式化
        String query = queryArea.getText();
        if (query != null && !query.isEmpty()) {
            query = query.replaceAll("\\s+", " ")
                .replace("{ ", "{\n  ")
                .replace(" }", "\n}")
                .replace(", ", "\n  ");
            queryArea.setText(query);
        }
    }

    private void submitBackground(Runnable operation) {
        if (disposed) return;
        backgroundTasks.removeIf(Future::isDone);
        backgroundTasks.add(backgroundExecutor.submit(operation));
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Future<?> task : backgroundTasks) {
            try {
                task.cancel(true);
            } catch (RuntimeException ignored) {
                // Continue cancelling remaining work.
            }
        }
        backgroundTasks.clear();
        backgroundExecutor.shutdownNow();
        graphqlService.close();
    }
}
