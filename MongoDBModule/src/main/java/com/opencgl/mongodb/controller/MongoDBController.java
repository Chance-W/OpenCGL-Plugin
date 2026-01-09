package com.opencgl.mongodb.controller;

import com.opencgl.mongodb.i18n.I18N;
import com.opencgl.mongodb.service.MongoDBService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.bson.Document;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * MongoDB 工具控制器
 *
 * @author OpenCGL
 */
public class MongoDBController implements Initializable {

    @FXML private TextField connectionStringField;
    @FXML private Button connectBtn;
    @FXML private Label connectionStatusLabel;
    
    @FXML private ListView<String> databaseListView;
    @FXML private ListView<String> collectionListView;
    
    @FXML private TextArea queryArea;
    @FXML private TextArea resultArea;
    @FXML private TextField limitField;
    
    @FXML private TextArea documentArea;
    @FXML private Label operationStatusLabel;

    private final MongoDBService mongoService = new MongoDBService();
    private boolean isConnected = false;
    private String selectedDatabase;
    private String selectedCollection;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        connectionStringField.setText("mongodb://localhost:27017");
        limitField.setText("100");
        
        databaseListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedDatabase = newVal;
                refreshCollections();
            }
        });
        
        collectionListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedCollection = newVal;
            }
        });
        initI18n();
    }

    private void initI18n() {
        // Static texts are in FXML with %key; status messages use I18N.get() at runtime
    }

    @FXML
    private void onConnect() {
        String connStr = connectionStringField.getText();
        connectBtn.setDisable(true);
        connectionStatusLabel.setText(I18N.get("msg.connecting"));
        
        CompletableFuture.runAsync(() -> {
            try {
                mongoService.connect(connStr);
                boolean success = mongoService.testConnection();
                Platform.runLater(() -> {
                    if (success) {
                        isConnected = true;
                        connectionStatusLabel.setText(I18N.get("msg.connected"));
                        connectionStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                        refreshDatabases();
                    } else {
                        connectionStatusLabel.setText(I18N.get("msg.connectFailed"));
                        connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    }
                    connectBtn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("✗ " + e.getMessage());
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    connectBtn.setDisable(false);
                });
            }
        });
    }

    private void refreshDatabases() {
        CompletableFuture.runAsync(() -> {
            try {
                List<String> databases = mongoService.listDatabases();
                Platform.runLater(() -> {
                    databaseListView.getItems().clear();
                    databaseListView.getItems().addAll(databases);
                });
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("msg.listDbFailed", e.getMessage())));
            }
        });
    }

    private void refreshCollections() {
        if (selectedDatabase == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                List<String> collections = mongoService.listCollections(selectedDatabase);
                Platform.runLater(() -> {
                    collectionListView.getItems().clear();
                    collectionListView.getItems().addAll(collections);
                });
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("msg.listCollFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onFind() {
        if (!checkSelection()) return;
        
        String filter = queryArea.getText();
        int limit = parseLimit();
        
        resultArea.setText(I18N.get("msg.querying"));
        
        CompletableFuture.runAsync(() -> {
            try {
                List<Document> docs = mongoService.find(selectedDatabase, selectedCollection, filter, limit);
                StringBuilder result = new StringBuilder();
                result.append(I18N.get("msg.foundRecords", docs.size())).append("\n\n");
                for (Document doc : docs) {
                    result.append(doc.toJson()).append("\n\n");
                }
                Platform.runLater(() -> resultArea.setText(result.toString()));
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("msg.queryFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onCount() {
        if (!checkSelection()) return;
        
        String filter = queryArea.getText();
        
        CompletableFuture.runAsync(() -> {
            try {
                long count = mongoService.countDocuments(selectedDatabase, selectedCollection, filter);
                Platform.runLater(() -> resultArea.setText(I18N.get("msg.documentCount", count)));
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("msg.countFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onInsert() {
        if (!checkSelection()) return;
        
        String docJson = documentArea.getText();
        if (docJson == null || docJson.isEmpty()) {
            operationStatusLabel.setText(I18N.get("msg.pleaseEnterDoc"));
            return;
        }
        
        operationStatusLabel.setText(I18N.get("msg.inserting"));
        
        CompletableFuture.runAsync(() -> {
            try {
                String id = mongoService.insertOne(selectedDatabase, selectedCollection, docJson);
                Platform.runLater(() -> {
                    operationStatusLabel.setText(I18N.get("msg.insertSuccess", id));
                    operationStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    operationStatusLabel.setText("✗ " + e.getMessage());
                    operationStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onUpdate() {
        if (!checkSelection()) return;
        
        String filter = queryArea.getText();
        String update = documentArea.getText();
        
        if (filter == null || filter.isEmpty()) {
            operationStatusLabel.setText(I18N.get("msg.pleaseEnterFilter"));
            return;
        }
        
        operationStatusLabel.setText(I18N.get("msg.updating"));
        
        CompletableFuture.runAsync(() -> {
            try {
                long count = mongoService.updateOne(selectedDatabase, selectedCollection, filter, update);
                Platform.runLater(() -> {
                    operationStatusLabel.setText(I18N.get("msg.updateSuccess", count));
                    operationStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    operationStatusLabel.setText("✗ " + e.getMessage());
                    operationStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onDelete() {
        if (!checkSelection()) return;
        
        String filter = queryArea.getText();
        if (filter == null || filter.isEmpty()) {
            operationStatusLabel.setText(I18N.get("msg.pleaseEnterDeleteFilter"));
            return;
        }
        
        operationStatusLabel.setText(I18N.get("msg.deleting"));
        
        CompletableFuture.runAsync(() -> {
            try {
                long count = mongoService.deleteMany(selectedDatabase, selectedCollection, filter);
                Platform.runLater(() -> {
                    operationStatusLabel.setText(I18N.get("msg.deleteSuccess", count));
                    operationStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    operationStatusLabel.setText("✗ " + e.getMessage());
                    operationStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onAggregate() {
        if (!checkSelection()) return;
        
        String pipeline = queryArea.getText();
        if (pipeline == null || pipeline.isEmpty()) {
            resultArea.setText(I18N.get("msg.pleaseEnterPipeline"));
            return;
        }
        
        resultArea.setText(I18N.get("msg.aggregating"));
        
        CompletableFuture.runAsync(() -> {
            try {
                List<Document> docs = mongoService.aggregate(selectedDatabase, selectedCollection, pipeline);
                StringBuilder result = new StringBuilder();
                result.append(I18N.get("msg.aggregateResult", docs.size())).append("\n\n");
                for (Document doc : docs) {
                    result.append(doc.toJson()).append("\n\n");
                }
                Platform.runLater(() -> resultArea.setText(result.toString()));
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("msg.aggregateFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onListIndexes() {
        if (!checkSelection()) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                List<Document> indexes = mongoService.listIndexes(selectedDatabase, selectedCollection);
                StringBuilder result = new StringBuilder();
                result.append(I18N.get("msg.indexList")).append("\n\n");
                for (Document index : indexes) {
                    result.append(index.toJson()).append("\n\n");
                }
                Platform.runLater(() -> resultArea.setText(result.toString()));
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("msg.indexFailed", e.getMessage())));
            }
        });
    }

    @FXML
    private void onClear() {
        queryArea.clear();
        documentArea.clear();
        resultArea.clear();
        operationStatusLabel.setText("");
    }

    private boolean checkSelection() {
        if (!isConnected) {
            resultArea.setText(I18N.get("msg.pleaseConnect"));
            return false;
        }
        if (selectedDatabase == null) {
            resultArea.setText(I18N.get("msg.pleaseSelectDb"));
            return false;
        }
        if (selectedCollection == null) {
            resultArea.setText(I18N.get("msg.pleaseSelectColl"));
            return false;
        }
        return true;
    }

    private int parseLimit() {
        try {
            return Integer.parseInt(limitField.getText());
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    public void dispose() {
        mongoService.close();
    }
}
