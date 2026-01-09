package com.opencgl.extractor.controller;

import com.opencgl.extractor.i18n.I18N;
import com.opencgl.extractor.service.ExtractionService;
import com.opencgl.extractor.service.ExtractionService.*;
import com.opencgl.extractor.views.DataExtractorView;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据提取器控制器
 */
public class DataExtractorController extends DataExtractorView implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(DataExtractorController.class);
    
    private final ExtractionService extractionService = new ExtractionService();
    private CodeArea inputDataArea;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-data-extractor");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupCodeArea();
        setupDataTypeComboBox();
        setupTemplatesList();
        bindEvents();
        initI18n();
        setStatus(I18N.get("label.statusReady"));
    }

    private void initI18n() {
        if (titleLabel != null) titleLabel.textProperty().bind(I18N.getBinding("label.title"));
        if (dataTypeLabel != null) dataTypeLabel.textProperty().bind(I18N.getBinding("label.dataType"));
        if (inputDataLabel != null) inputDataLabel.textProperty().bind(I18N.getBinding("label.inputData"));
        if (expressionLabel != null) expressionLabel.textProperty().bind(I18N.getBinding("label.expression"));
        if (extractButton != null) extractButton.textProperty().bind(I18N.getBinding("btn.extract"));
        if (clearButton != null) clearButton.textProperty().bind(I18N.getBinding("btn.clear"));
        if (resultLabel != null) resultLabel.textProperty().bind(I18N.getBinding("label.result"));
        if (templatesLabel != null) templatesLabel.textProperty().bind(I18N.getBinding("label.templates"));
        if (doubleClickLabel != null) doubleClickLabel.textProperty().bind(I18N.getBinding("label.doubleClickTip"));
        if (expressionField != null) expressionField.promptTextProperty().bind(I18N.getBinding("prompt.expression"));
    }
    
    private void setupCodeArea() {
        inputDataArea = new CodeArea();
        inputDataArea.setStyle(
            "-fx-font-family: 'JetBrains Mono', 'Consolas', monospace;" +
            "-fx-font-size: 13px;" +
            "-fx-background-color: #1e1e1e;"
        );
        inputDataArea.setWrapText(true);
        
        // 示例 JSON
        inputDataArea.replaceText("""
            {
              "users": [
                {"id": 1, "name": "Alice", "age": 25, "city": "Beijing"},
                {"id": 2, "name": "Bob", "age": 30, "city": "Shanghai"},
                {"id": 3, "name": "Charlie", "age": 22, "city": "Shenzhen"}
              ],
              "total": 3
            }
            """);
        
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(inputDataArea);
        javafx.scene.layout.VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);
        inputDataContainer.getChildren().add(scrollPane);
    }
    
    private void setupDataTypeComboBox() {
        dataTypeComboBox.selectFirst();
        dataTypeComboBox.valueProperty().addListener((obs, old, newVal) -> {
            updateTemplatesList(newVal);
            if ("XML".equals(newVal)) {
                inputDataArea.replaceText("""
                    <root>
                        <users>
                            <user id="1">
                                <name>Alice</name>
                                <age>25</age>
                                <city>Beijing</city>
                            </user>
                            <user id="2">
                                <name>Bob</name>
                                <age>30</age>
                                <city>Shanghai</city>
                            </user>
                        </users>
                    </root>
                    """);
            }
        });
    }
    
    private void setupTemplatesList() {
        updateTemplatesList("JSON");
        
        templatesList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = templatesList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    applyTemplate(selected);
                }
            }
        });
    }
    
    private void updateTemplatesList(String dataType) {
        templatesList.getItems().clear();
        
        if ("JSON".equals(dataType)) {
            ExtractionService.JSON_TEMPLATES.forEach(t -> {
                templatesList.getItems().add(t.name() + ": " + t.expression());
            });
            expressionField.setText("$.users[*].name");
        } else {
            ExtractionService.XPATH_TEMPLATES.forEach(t -> {
                templatesList.getItems().add(t.name() + ": " + t.expression());
            });
            expressionField.setText("//user/name/text()");
        }
    }
    
    private void applyTemplate(String selectedItem) {
        String expression = selectedItem.split(": ", 2)[1];
        expressionField.setText(expression);
    }
    
    private void bindEvents() {
        extractButton.setOnAction(e -> performExtraction());
        clearButton.setOnAction(e -> onClear());
    }
    
    private void performExtraction() {
        String dataType = dataTypeComboBox.getValue();
        String data = inputDataArea.getText();
        String expression = expressionField.getText().trim();
        
        if (data.isEmpty() || expression.isEmpty()) {
            setStatus(I18N.get("status.pleaseInput"));
            return;
        }
        
        setStatus(I18N.get("status.extracting"));
        
        CompletableFuture.supplyAsync(() -> {
            if ("JSON".equals(dataType)) {
                return extractionService.extractJson(data, expression);
            } else {
                return extractionService.extractXml(data, expression);
            }
        }, executor).thenAccept(result -> Platform.runLater(() -> {
            if (disposed) return;
            if (result.success()) {
                resultArea.setText(result.result());
                setStatus(I18N.get("status.success"));
            } else {
                resultArea.setText(I18N.get("status.error") + " " + result.error());
                setStatus(I18N.get("status.failed"));
            }
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                if (disposed) return;
                resultArea.setText(I18N.get("status.exception") + " " + ex.getMessage());
                setStatus(I18N.get("status.failed"));
                logger.error("数据提取异常", ex);
            });
            return null;
        });
    }
    
    private void onClear() {
        resultArea.clear();
        expressionField.clear();
        setStatus(I18N.get("status.cleared"));
    }
    
    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        executor.shutdownNow();
    }
}
