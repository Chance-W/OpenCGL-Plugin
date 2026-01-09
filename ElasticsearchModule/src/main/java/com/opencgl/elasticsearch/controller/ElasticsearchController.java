package com.opencgl.elasticsearch.controller;

import com.opencgl.elasticsearch.i18n.I18N;
import com.opencgl.elasticsearch.service.ElasticsearchService;
import com.opencgl.elasticsearch.service.ElasticsearchService.IndexInfo;
import com.opencgl.elasticsearch.service.ElasticsearchService.SearchResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Elasticsearch 工具控制器
 *
 * @author OpenCGL
 */
public class ElasticsearchController implements Initializable {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button connectBtn;
    @FXML private Label connectionStatusLabel;
    
    @FXML private ListView<String> indexListView;
    @FXML private TextField sizeField;
    @FXML private TextArea queryArea;
    @FXML private TextArea resultArea;
    
    @FXML private TextArea documentArea;
    @FXML private TextField docIdField;
    @FXML private Label operationStatusLabel;
    
    @FXML private Label hostLabel;
    @FXML private Label portLabel;
    @FXML private Label userLabel;
    @FXML private Label passLabel;
    @FXML private Label indicesLabel;
    @FXML private Button refreshIndicesBtn;
    @FXML private Label dslLabel;
    @FXML private Label sizeLabel;
    @FXML private Button searchBtn;
    @FXML private Button mappingBtn;
    @FXML private Button deleteByQueryBtn;
    @FXML private Button clearBtn;
    @FXML private Label docLabel;
    @FXML private Label idLabel;
    @FXML private Button indexDocBtn;
    @FXML private Button deleteDocBtn;
    @FXML private Label resultLabel;

    private final ElasticsearchService esService = new ElasticsearchService();
    private boolean isConnected = false;
    private String selectedIndex;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        hostField.setText("localhost");
        portField.setText("9200");
        sizeField.setText("100");
        
        indexListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedIndex = newVal;
            }
        });
        initI18n();
    }

    private void initI18n() {
        if (hostLabel != null) hostLabel.textProperty().bind(I18N.getBinding("label.host"));
        if (portLabel != null) portLabel.textProperty().bind(I18N.getBinding("label.port"));
        if (userLabel != null) userLabel.textProperty().bind(I18N.getBinding("label.user"));
        if (passLabel != null) passLabel.textProperty().bind(I18N.getBinding("label.pass"));
        if (connectBtn != null) connectBtn.textProperty().bind(I18N.getBinding("btn.connect"));
        if (indicesLabel != null) indicesLabel.textProperty().bind(I18N.getBinding("label.indices"));
        if (refreshIndicesBtn != null) refreshIndicesBtn.textProperty().bind(I18N.getBinding("btn.refresh"));
        if (dslLabel != null) dslLabel.textProperty().bind(I18N.getBinding("label.dslQuery"));
        if (sizeLabel != null) sizeLabel.textProperty().bind(I18N.getBinding("label.size"));
        if (searchBtn != null) searchBtn.textProperty().bind(I18N.getBinding("btn.search"));
        if (mappingBtn != null) mappingBtn.textProperty().bind(I18N.getBinding("btn.getMapping"));
        if (deleteByQueryBtn != null) deleteByQueryBtn.textProperty().bind(I18N.getBinding("btn.deleteByQuery"));
        if (clearBtn != null) clearBtn.textProperty().bind(I18N.getBinding("btn.clear"));
        if (docLabel != null) docLabel.textProperty().bind(I18N.getBinding("label.document"));
        if (idLabel != null) idLabel.textProperty().bind(I18N.getBinding("label.id"));
        if (indexDocBtn != null) indexDocBtn.textProperty().bind(I18N.getBinding("btn.indexDocument"));
        if (deleteDocBtn != null) deleteDocBtn.textProperty().bind(I18N.getBinding("btn.deleteDocument"));
        if (resultLabel != null) resultLabel.textProperty().bind(I18N.getBinding("label.queryResult"));
        if (queryArea != null) queryArea.promptTextProperty().bind(I18N.getBinding("prompt.query"));
        if (docIdField != null) docIdField.promptTextProperty().bind(I18N.getBinding("prompt.docId"));
        if (documentArea != null) documentArea.promptTextProperty().bind(I18N.getBinding("prompt.document"));
    }

    @FXML
    private void onConnect() {
        String host = hostField.getText();
        int port = Integer.parseInt(portField.getText());
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        connectBtn.setDisable(true);
        connectionStatusLabel.setText(I18N.get("status.connecting"));
        
        CompletableFuture.runAsync(() -> {
            try {
                esService.connect(host, port, username, password);
                boolean success = esService.testConnection();
                Platform.runLater(() -> {
                    if (success) {
                        isConnected = true;
                        connectionStatusLabel.setText(I18N.get("status.connected"));
                        connectionStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                        refreshIndices();
                        showClusterInfo();
                    } else {
                        connectionStatusLabel.setText(I18N.get("status.connectFailed"));
                        connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    }
                    connectBtn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText(I18N.get("status.connectFailed") + " " + e.getMessage());
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    connectBtn.setDisable(false);
                });
            }
        });
    }

    private void showClusterInfo() {
        CompletableFuture.runAsync(() -> {
            try {
                String info = esService.getClusterInfo();
                Platform.runLater(() -> resultArea.setText(info));
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("status.getClusterFailed") + " " + e.getMessage()));
            }
        });
    }

    @FXML
    private void onRefreshIndices() {
        refreshIndices();
    }

    private void refreshIndices() {
        CompletableFuture.runAsync(() -> {
            try {
                var indices = esService.listIndices();
                Platform.runLater(() -> {
                    indexListView.getItems().clear();
                    for (IndexInfo info : indices) {
                        indexListView.getItems().add(info.name());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("status.listIndicesFailed") + " " + e.getMessage()));
            }
        });
    }

    @FXML
    private void onSearch() {
        if (!checkSelection()) return;
        
        String query = queryArea.getText();
        int size = parseSize();
        
        resultArea.setText(I18N.get("status.searching"));
        
        CompletableFuture.runAsync(() -> {
            try {
                SearchResult result = esService.search(selectedIndex, query, size);
                StringBuilder sb = new StringBuilder();
                sb.append("total: ").append(result.total()).append(", took: ").append(result.took()).append("ms\n\n");
                for (String doc : result.documents()) {
                    sb.append(doc).append("\n\n---\n\n");
                }
                Platform.runLater(() -> resultArea.setText(sb.toString()));
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("status.searchFailed") + " " + e.getMessage()));
            }
        });
    }

    @FXML
    private void onGetMapping() {
        if (!checkSelection()) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String mapping = esService.getMapping(selectedIndex);
                Platform.runLater(() -> resultArea.setText(I18N.get("status.mapping") + "\n\n" + mapping));
            } catch (Exception e) {
                Platform.runLater(() -> resultArea.setText(I18N.get("status.getMappingFailed") + " " + e.getMessage()));
            }
        });
    }

    @FXML
    private void onIndexDocument() {
        if (!checkSelection()) return;
        
        String docJson = documentArea.getText();
        String docId = docIdField.getText();
        
        if (docJson == null || docJson.isEmpty()) {
            operationStatusLabel.setText(I18N.get("msg.pleaseInputDoc"));
            return;
        }
        
        operationStatusLabel.setText(I18N.get("status.indexing"));
        
        CompletableFuture.runAsync(() -> {
            try {
                String id = esService.indexDocument(selectedIndex, docId, docJson);
                Platform.runLater(() -> {
                    operationStatusLabel.setText(I18N.get("status.indexSuccess") + " " + id);
                    operationStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    operationStatusLabel.setText(I18N.get("status.connectFailed") + " " + e.getMessage());
                    operationStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onDeleteDocument() {
        if (!checkSelection()) return;
        
        String docId = docIdField.getText();
        if (docId == null || docId.isEmpty()) {
            operationStatusLabel.setText(I18N.get("status.docIdRequired"));
            return;
        }
        
        operationStatusLabel.setText(I18N.get("status.deleting"));
        
        CompletableFuture.runAsync(() -> {
            try {
                boolean deleted = esService.deleteDocument(selectedIndex, docId);
                Platform.runLater(() -> {
                    operationStatusLabel.setText(deleted ? I18N.get("status.deleteSuccess") : I18N.get("status.docNotFound"));
                    operationStatusLabel.setStyle(deleted ? "-fx-text-fill: #4caf50;" : "-fx-text-fill: #ff9800;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    operationStatusLabel.setText(I18N.get("status.connectFailed") + " " + e.getMessage());
                    operationStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onDeleteByQuery() {
        if (!checkSelection()) return;
        
        String query = queryArea.getText();
        if (query == null || query.isEmpty()) {
            operationStatusLabel.setText(I18N.get("msg.pleaseInputQuery"));
            return;
        }
        
        operationStatusLabel.setText(I18N.get("status.batchDeleting"));
        
        CompletableFuture.runAsync(() -> {
            try {
                long count = esService.deleteByQuery(selectedIndex, query);
                Platform.runLater(() -> {
                    operationStatusLabel.setText(I18N.get("status.deleteCount", count));
                    operationStatusLabel.setStyle("-fx-text-fill: #4caf50;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    operationStatusLabel.setText(I18N.get("status.connectFailed") + " " + e.getMessage());
                    operationStatusLabel.setStyle("-fx-text-fill: #f44336;");
                });
            }
        });
    }

    @FXML
    private void onClear() {
        queryArea.clear();
        documentArea.clear();
        resultArea.clear();
        docIdField.clear();
        operationStatusLabel.setText("");
    }

    private boolean checkSelection() {
        if (!isConnected) {
            resultArea.setText(I18N.get("msg.connectFirst"));
            return false;
        }
        if (selectedIndex == null) {
            resultArea.setText(I18N.get("msg.selectIndex"));
            return false;
        }
        return true;
    }

    private int parseSize() {
        try {
            return Integer.parseInt(sizeField.getText());
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    public void dispose() {
        esService.close();
    }
}
