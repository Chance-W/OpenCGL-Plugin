package com.opencgl.sqlclient.controller;

import com.opencgl.base.utils.tree.TreeViewBuilder;
import com.opencgl.sqlclient.model.DbConnection;
import com.opencgl.sqlclient.model.NodeType;
import com.opencgl.sqlclient.model.SqlTreeItem;
import com.opencgl.sqlclient.service.DatabaseService;
import com.opencgl.sqlclient.service.SqlTreeService;
import com.opencgl.sqlclient.i18n.I18N;
import com.opencgl.sqlclient.util.ExportUtil;
import com.opencgl.sqlclient.util.SqlFormatterUtil;
import com.opencgl.sqlclient.util.SqlStatementSplitter;
import com.opencgl.sqlclient.util.SqlCompletionUtil;
import com.opencgl.base.utils.LoadingMask;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Side;
import javafx.stage.FileChooser;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;

public class SqlClientController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(SqlClientController.class);
    
    @FXML public SplitPane mainSplitPane;
    @FXML public VBox treeContainer;
    @FXML public Label dbConnLabel;
    @FXML public Button addConnectionButton;
    @FXML public Button addGroupButton;

    // Right Side - Content Area
    @FXML public StackPane contentArea;
    @FXML public VBox sqlEditorView;
    @FXML public VBox configPanel;
    
    // SQL Editor UI
    @FXML public Label sqlEditorLabel;
    @FXML public Button executeButton;
    @FXML public Button formatButton;
    @FXML public Button clearSqlButton;
    @FXML public CodeArea sqlEditor;
    @FXML public Label resultLabel;
    @FXML public Label statusLabel;
    @FXML public TableView<ObservableList<Object>> resultTable;
    @FXML public TabPane resultTabs;
    @FXML public Button exportCsvButton;
    @FXML public Button exportJsonButton;
    
    // Config Panel UI
    @FXML public Label connConfigLabel;
    @FXML public TitledPane basicInfoPane;
    @FXML public TitledPane advancedPane;
    @FXML public TextField confNameField;
    @FXML public ComboBox<String> confTypeCombo;
    @FXML public TextField confHostField;
    @FXML public TextField confPortField;
    @FXML public TextField confDatabaseField;
    @FXML public TextField confUsernameField;
    @FXML public PasswordField confPasswordField;
    @FXML public Button confTestButton;
    @FXML public Button confSaveButton;
    @FXML public Button confCancelButton;
    
    private DatabaseService dbService;
    private SqlTreeService treeService;
    private TreeView<SqlTreeItem> connectionTree;
    private SqlTreeItem currentConnection;
    private SqlTreeItem editingItem; // Currently editing item in config panel
    private DatabaseService.QueryResult lastResult;
    private final LoadingMask loadingMask = new LoadingMask();
    // 保留旧 API 方法以兼容已编译控制器，但不再绑定快捷键或自动弹出联想。
    private ContextMenu completionMenu;
    private final List<String> sqlHistory = new ArrayList<>();
    private Path historyFile;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dbService = new DatabaseService();
        treeService = new SqlTreeService();
        
        setupUI();
        loadSqlHistory();
        setupTreeView();
        bindEvents();
        initI18n();
    }

    private void initI18n() {
        dbConnLabel.textProperty().bind(I18N.getBinding("label.db_connections"));
        sqlEditorLabel.textProperty().bind(I18N.getBinding("label.sql_editor"));
        executeButton.textProperty().bind(I18N.getBinding("label.execute_f5"));
        formatButton.textProperty().bind(I18N.getBinding("label.format"));
        clearSqlButton.textProperty().bind(I18N.getBinding("label.clear"));
        resultLabel.textProperty().bind(I18N.getBinding("label.query_result"));
        // statusLabel: initial from FXML %label.ready; runtime messages use setText(I18N.get(...))
        exportCsvButton.textProperty().bind(I18N.getBinding("label.export_csv"));
        exportJsonButton.textProperty().bind(I18N.getBinding("label.export_json"));
        addConnectionButton.textProperty().bind(I18N.getBinding("menu.new_conn"));
        addGroupButton.textProperty().bind(I18N.getBinding("menu.new_group"));
        connConfigLabel.textProperty().bind(I18N.getBinding("label.conn_config"));
        basicInfoPane.textProperty().bind(I18N.getBinding("label.basic_settings"));
        advancedPane.textProperty().bind(I18N.getBinding("label.advanced_settings"));
        confTestButton.textProperty().bind(I18N.getBinding("label.test_conn"));
        confSaveButton.textProperty().bind(I18N.getBinding("label.save"));
        confCancelButton.textProperty().bind(I18N.getBinding("label.cancel"));
    }
    
    private void setupTreeView() {
        // Build TreeView
        VBox component = new TreeViewBuilder<SqlTreeItem>()
            .service(treeService)
            .dataType(SqlTreeItem.class)
            .searchPrompt(I18N.get("prompt.search"))
            .enableSearch()
            .enableDragDrop(true)
            // TreeCellFactory 优先使用单元格菜单；必须通过工厂注入，
            // 仅设置 TreeView.setContextMenu 会被单元格默认菜单覆盖。
            .contextMenuFactory(this::createContextMenu)
            .onSelect(this::onTreeNodeSelected)
            .build();
        
        treeContainer.getChildren().clear();
        treeContainer.getChildren().add(component);
        VBox.setVgrow(component, Priority.ALWAYS);
        
        // Retrieve TreeView instance
        if (component.getChildren().size() > 1 && component.getChildren().get(1) instanceof TreeView) {
            this.connectionTree = (TreeView<SqlTreeItem>) component.getChildren().get(1);
        } else if (component.getChildren().get(0) instanceof TreeView) {
            this.connectionTree = (TreeView<SqlTreeItem>) component.getChildren().get(0);
        }
        
        if (connectionTree != null) {
            // Double click handler
            connectionTree.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    handleTreeDoubleClick();
                }
            });
            
        }
    }

    private void setupUI() {
        sqlEditor.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        sqlEditor.replaceText(I18N.get("placeholder.sql"));
        historyFile = Path.of(System.getProperty("user.home"), ".opencgl", "sql-client-history.txt");
        
        confTypeCombo.getItems().addAll("MySQL", "MariaDB", "PostgreSQL", "SQL Server", "Oracle", "SQLite", "H2");
        confTypeCombo.setValue("MySQL");
        confTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || confPortField == null) return;
            try {
                DbConnection.DbType type = DbConnection.DbType.valueOf(newValue.toUpperCase().replace(' ', '_'));
                confPortField.setText(String.valueOf(new DbConnection("", type).getPort()));
            } catch (IllegalArgumentException ignored) {
                // Keep the user-entered port for unknown/custom types.
            }
        });

        showSqlEditor(); // Default view
    }
    
    /**
     * 为树单元格创建 SQL 客户端专用右键菜单。
     * TreeCellFactory 会覆盖 TreeView 级别的 ContextMenu，因此菜单必须
     * 在这里按节点注入，才能保证右键连接节点时始终能看到配置入口。
     */
    private ContextMenu createContextMenu(SqlTreeItem selectedItem) {
        ContextMenu menu = new ContextMenu();
        boolean root = selectedItem == null || selectedItem.getId() == null
                || selectedItem.getId() == 0L;
        boolean group = selectedItem != null
                && NodeType.CONNECTION_GROUP.name().equals(selectedItem.getNodeType());
        boolean connection = selectedItem != null
                && NodeType.CONNECTION.name().equals(selectedItem.getNodeType());

        if (root || group) {
            MenuItem addConnection = new MenuItem(I18N.get("menu.new_conn"));
            addConnection.setOnAction(e -> startCreateConnection(selectedItem));
            MenuItem addGroup = new MenuItem(I18N.get("menu.new_group"));
            addGroup.setOnAction(e -> startCreateGroup(selectedItem));
            menu.getItems().addAll(addConnection, addGroup);
        }

        if (connection) {
            MenuItem edit = new MenuItem(I18N.get("menu.edit_conn"));
            edit.setOnAction(e -> showConfigPanel(selectedItem));
            MenuItem connect = new MenuItem(I18N.get("menu.connect"));
            connect.setOnAction(e -> {
                connectToDatabase(selectedItem);
                showSqlEditor();
            });
            menu.getItems().addAll(edit, connect);
        }

        if (selectedItem != null && !root) {
            if (!menu.getItems().isEmpty()) {
                menu.getItems().add(new SeparatorMenuItem());
            }
            MenuItem delete = new MenuItem(I18N.get("menu.delete"));
            delete.setOnAction(e -> deleteItem(selectedItem));
            menu.getItems().add(delete);
        }
        return menu;
    }

    private void bindEvents() {
        addConnectionButton.setOnAction(e -> startCreateConnection());
        addGroupButton.setOnAction(e -> startCreateGroup());
        // SQL Editor Actions
        executeButton.setOnAction(e -> executeSQL());
        formatButton.setOnAction(e -> formatSQL());
        clearSqlButton.setOnAction(e -> sqlEditor.clear());
        exportCsvButton.setOnAction(e -> exportToCSV());
        exportJsonButton.setOnAction(e -> exportToJSON());
        sqlEditor.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
        sqlEditor.textProperty().addListener((obs, oldText, newText) -> saveSqlHistory(newText));
        
        // Config Panel Actions
        confSaveButton.setOnAction(e -> saveConfig());
        confCancelButton.setOnAction(e -> showSqlEditor());
        confTestButton.setOnAction(e -> testConnection());
    }
    
    // --- View Switching ---
    
    private void showSqlEditor() {
        sqlEditorView.setVisible(true);
        sqlEditorView.setManaged(true);
        configPanel.setVisible(false);
        configPanel.setManaged(false);
        editingItem = null;
    }
    
    private void showConfigPanel(SqlTreeItem item) {
        editingItem = item;
        sqlEditorView.setVisible(false);
        sqlEditorView.setManaged(false);
        configPanel.setVisible(true);
        configPanel.setManaged(true);
        
        // Fill form
        if (item != null) {
            confNameField.setText(item.getName());
            confTypeCombo.setValue(item.getDbType() != null ? item.getDbType() : "MySQL");
            confHostField.setText(item.getHost());
            confPortField.setText(item.getPort() != null ? String.valueOf(item.getPort()) : "3306");
            confDatabaseField.setText(item.getDatabaseName());
            confUsernameField.setText(item.getUsername());
            confPasswordField.setText(item.getPassword()); // Decrypted in service/model logic? Usually need to check.
        } else {
            // New item defaults
            confNameField.setText("");
            confTypeCombo.setValue("MySQL");
            confHostField.setText("localhost");
            confPortField.setText("3306");
            confDatabaseField.setText("");
            confUsernameField.setText("");
            confPasswordField.setText("");
        }
    }
    
    // --- Actions ---
    
    private void startCreateConnection() {
        TreeItem<SqlTreeItem> selected = connectionTree.getSelectionModel().getSelectedItem();
        startCreateConnection(selected != null ? selected.getValue() : null);
    }

    private void startCreateConnection(SqlTreeItem selectedItem) {
        // Determine parent ID based on selection
        Long parentId = 0L;
        if (selectedItem != null && NodeType.CONNECTION_GROUP.name().equals(selectedItem.getNodeType())) {
            parentId = selectedItem.getId();
        }

        SqlTreeItem newItem = new SqlTreeItem();
        newItem.setNodeTypeEnum(NodeType.CONNECTION);
        newItem.setParentId(parentId); // Set parent!
        
        showConfigPanel(newItem); // Mode: Create
        statusLabel.setText(I18N.get("msg.new_conn"));
    }
    
    private void startCreateGroup() {
        TreeItem<SqlTreeItem> selected = connectionTree.getSelectionModel().getSelectedItem();
        startCreateGroup(selected != null ? selected.getValue() : null);
    }

    private void startCreateGroup(SqlTreeItem selectedItem) {
        Long parentId = 0L;
        if (selectedItem != null && NodeType.CONNECTION_GROUP.name().equals(selectedItem.getNodeType())) {
            parentId = selectedItem.getId();
        }
        
        // Simple Input Dialog for Group Name
        TextInputDialog dialog = new TextInputDialog(I18N.get("msg.new_group"));
        dialog.setTitle(I18N.get("title.new_group"));
        dialog.setHeaderText(null);
        dialog.setContentText(I18N.get("msg.group_name"));
        
        Long finalParentId = parentId;
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                treeService.createConnectionGroup(name.trim(), finalParentId);
                refreshTree();
            }
        });
    }
    
    private void deleteSelectedItem() {
        TreeItem<SqlTreeItem> selected = connectionTree.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) return;
        deleteItem(selected.getValue());
    }

    private void deleteItem(SqlTreeItem item) {
        if (item == null || item.getId() == null || item.getId() == 0L) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18N.get("title.confirm_delete"));
        alert.setHeaderText(I18N.get("msg.delete_confirm", item.getName()));
        alert.setContentText(I18N.get("msg.delete_irreversible"));
        
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                treeService.delete(item);
                refreshTree();
            }
        });
    }

    private void onTreeNodeSelected(SqlTreeItem item) {
        if (item == null) return;
        statusLabel.setText(I18N.get("msg.selected", item.getName()));
        
        if (NodeType.CONNECTION.name().equals(item.getNodeType())) {
            showConfigPanel(item);
        } else if (NodeType.CONNECTION_GROUP.name().equals(item.getNodeType())) {
            // Group: maybe show nothing or just label? For now, allow editing group name logic? 
            // Current code doesn't support editing group name in panel nicely (fields are for connection).
            // Let's just show editor view for groups to keep it simple, or hide right side.
            showSqlEditor(); 
        } else if (NodeType.TABLE.name().equals(item.getNodeType())) {
            showSqlEditor();
            generateQueryForObject(item);
        } else {
            showSqlEditor();
        }
        
        if (item.isConnection()) {
            currentConnection = item;
        }
    }
    
    private void handleTreeDoubleClick() {
        TreeItem<SqlTreeItem> selected = connectionTree.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) return;
        
        SqlTreeItem item = selected.getValue();
        if (NodeType.CONNECTION.name().equals(item.getNodeType())) {
            connectToDatabase(item);
            showSqlEditor(); // Switch to editor on connect
        } else if (item.isDatabaseObject()) {
            generateQueryForObject(item);
        }
    }
    
    private void saveConfig() {
        if (editingItem == null) return;
        
        String name = confNameField.getText().trim();
        if (name.isEmpty()) {
            showAlert(I18N.get("title.error"), I18N.get("msg.name_required"));
            return;
        }
        
        editingItem.setName(name);
        editingItem.setDbType(confTypeCombo.getValue());
        editingItem.setHost(confHostField.getText().trim());
        try {
            editingItem.setPort(Integer.parseInt(confPortField.getText().trim()));
        } catch (NumberFormatException e) {
            editingItem.setPort(3306);
        }
        editingItem.setDatabaseName(confDatabaseField.getText().trim());
        editingItem.setUsername(confUsernameField.getText().trim());
        editingItem.setPassword(confPasswordField.getText());
        
        if (editingItem.getId() == null) {
            // Create
            if (NodeType.CONNECTION.name().equals(editingItem.getNodeType())) {
                treeService.createConnection(
                    editingItem.getName(), editingItem.getHost(), editingItem.getPort(),
                    editingItem.getDatabaseName(), editingItem.getUsername(), editingItem.getPassword(),
                    editingItem.getDbType(), editingItem.getParentId() 
                );
            }
        } else {
            // Update
            treeService.update(editingItem);
        }
        
        refreshTree();
        showSqlEditor(); // Switch back after save
        statusLabel.setText(I18N.get("msg.save_success"));
    }
    
    private void testConnection() {
         String host = confHostField.getText().trim();
         int port;
         try {
             port = Integer.parseInt(confPortField.getText());
         } catch (Exception e) {
             showAlert(I18N.get("title.error"), I18N.get("msg.port_invalid"));
             return;
         }
         
         DbConnection dbConn = new DbConnection();
         dbConn.setHost(host);
         dbConn.setDatabase(confDatabaseField.getText());
         dbConn.setUsername(confUsernameField.getText());
         dbConn.setPassword(confPasswordField.getText());
         
         try {
             String typeStr = confTypeCombo.getValue();
            dbConn.setType(parseDbType(typeStr));
         } catch (Exception e) {
             dbConn.setType(DbConnection.DbType.MYSQL);
         }
         dbConn.setPort(port);
         
         try {
             dbService.testConnection(dbConn);
             showAlert(I18N.get("title.success"), I18N.get("msg.test_success"));
         } catch (Exception e) {
             showAlert(I18N.get("title.failed"), I18N.get("msg.test_failed", e.getMessage()));
         }
    }

    private void connectToDatabase(SqlTreeItem connection) {
        statusLabel.setText(I18N.get("msg.connecting", connection.getName()));
        currentConnection = connection;
        
        loadingMask.show(contentArea);
        CompletableFuture.runAsync(() -> { try {
            DbConnection dbConn = new DbConnection();
            dbConn.setName(connection.getName());
            dbConn.setHost(connection.getHost());
            dbConn.setDatabase(connection.getDatabaseName());
            dbConn.setUsername(connection.getUsername());
            dbConn.setPassword(connection.getPassword());
            
            String typeStr = connection.getDbType();
            if (typeStr != null) {
                try {
                    dbConn.setType(parseDbType(typeStr));
                } catch (IllegalArgumentException e) {
                    dbConn.setType(DbConnection.DbType.MYSQL);
                }
            }
            dbConn.setPort(connection.getPort());
            
            dbService.connect(dbConn);
            for (String table : dbService.getTables(dbService.getConnection(connection.getName()))) {
                treeService.createTableIfAbsent(table, connection.getId(), connection.getDbType());
            }
            Platform.runLater(() -> { refreshTree(); statusLabel.setText(I18N.get("msg.connected", connection.getName())); loadingMask.hide(); });
          } catch (Exception e) {
            logger.error("Connection failed", e);
            Platform.runLater(() -> { statusLabel.setText(I18N.get("msg.conn_failed", e.getMessage())); loadingMask.hide(); showAlert(I18N.get("title.conn_failed"), I18N.get("msg.cannot_connect", e.getMessage())); });
          }
        });
    }

    private DbConnection.DbType parseDbType(String value) {
        if (value == null) return DbConnection.DbType.MYSQL;
        try {
            return DbConnection.DbType.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return DbConnection.DbType.MYSQL;
        }
    }

    private void generateQueryForObject(SqlTreeItem item) {
        if (NodeType.TABLE.name().equals(item.getNodeType())) {
            String query;
            String type = currentConnection != null ? currentConnection.getDbType() : "";
            if (type != null && type.equalsIgnoreCase("Oracle")) query = "SELECT * FROM " + item.getName() + " FETCH FIRST 100 ROWS ONLY";
            else if (type != null && type.equalsIgnoreCase("SQL Server")) query = "SELECT TOP 100 * FROM " + item.getName();
            else query = "SELECT * FROM " + item.getName() + " LIMIT 100";
            sqlEditor.replaceText(query);
            statusLabel.setText(I18N.get("msg.query_generated", item.getName()));
            executeSQL();
        }
    }

    private void executeSQL() {
        String selected = sqlEditor.getSelectedText();
        String sql = (selected == null || selected.isBlank() ? sqlEditor.getText() : selected).trim();
        if (sql.isEmpty()) {
            showAlert(I18N.get("title.error"), I18N.get("msg.sql_empty"));
            return;
        }

        if (currentConnection == null) {
            showAlert(I18N.get("title.error"), I18N.get("msg.select_conn_first"));
            return;
        }
        
        statusLabel.setText(I18N.get("msg.executing"));
        loadingMask.show(sqlEditorView);
        
        try {
            java.sql.Connection conn = dbService.getConnection(currentConnection.getName());
            if (conn == null || conn.isClosed()) {
                connectToDatabase(currentConnection);
                conn = dbService.getConnection(currentConnection.getName());
            }
            
            if (conn == null) {
                throw new Exception(I18N.get("msg.cannot_establish"));
            }
            
            List<String> statements = SqlStatementSplitter.split(sql);
            if (statements.isEmpty()) throw new Exception("SQL 仅包含注释或分隔符");
            resultTabs.getTabs().clear();
            for (int i = 0; i < statements.size(); i++) {
                DatabaseService.QueryResult result = dbService.executeQuery(conn, statements.get(i));
                if (i == statements.size() - 1) lastResult = result;
                TableView<ObservableList<Object>> table = new TableView<>();
                populateResultTable(table, result);
                resultTabs.getTabs().add(new Tab("结果 " + (i + 1), table));
            }
            statusLabel.setText("执行完成，共 " + statements.size() + " 条语句");
            recordSql(sql);
            
        } catch (Exception e) {
            logger.error("Execute SQL failed", e);
            statusLabel.setText(I18N.get("msg.exec_failed", e.getMessage()));
            showAlert(I18N.get("title.exec_failed"), I18N.get("msg.exec_failed", e.getMessage()));
        } finally {
            loadingMask.hide();
        }
    }
    
    private void updateResultTable(DatabaseService.QueryResult result) {
        populateResultTable(resultTable, result);
    }

    private void populateResultTable(TableView<ObservableList<Object>> table, DatabaseService.QueryResult result) {
        table.getColumns().clear();
        table.getItems().clear();
        
        if (result.columns.isEmpty()) return;
        
        for (int i = 0; i < result.columns.size(); i++) {
            final int colIndex = i;
            String colName = result.columns.get(i);
            TableColumn<ObservableList<Object>, Object> column = new TableColumn<>(colName);
            column.setCellValueFactory(param -> {
                ObservableList<Object> row = param.getValue();
                if (colIndex < row.size()) {
                    return new javafx.beans.property.SimpleObjectProperty<>(row.get(colIndex));
                }
                return null;
            });
            table.getColumns().add(column);
        }
        
        for (List<Object> rowData : result.rows) {
            table.getItems().add(FXCollections.observableArrayList(rowData));
        }
        table.setContextMenu(createResultContextMenu(table));
    }

    private ContextMenu createResultContextMenu(TableView<ObservableList<Object>> table) {
        ContextMenu menu = new ContextMenu();
        MenuItem copy = new MenuItem("复制选中内容");
        copy.setOnAction(e -> {
            ObservableList<Object> row = table.getSelectionModel().getSelectedItem();
            if (row != null) {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(row.toString());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            }
        });
        MenuItem copyHeaders = new MenuItem("复制列标题");
        copyHeaders.setOnAction(e -> {
            String headers = table.getColumns().stream().map(TableColumn::getText)
                    .collect(java.util.stream.Collectors.joining("\t"));
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(headers);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        MenuItem copyAll = new MenuItem("复制全部结果");
        copyAll.setOnAction(e -> {
            StringBuilder text = new StringBuilder();
            text.append(table.getColumns().stream().map(TableColumn::getText)
                    .collect(java.util.stream.Collectors.joining("\t"))).append('\n');
            for (ObservableList<Object> row : table.getItems()) text.append(row.stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining("\t"))).append('\n');
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text.toString());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        menu.getItems().addAll(copy, copyHeaders, copyAll);
        return menu;
    }

    private void formatSQL() {
        String sql = sqlEditor.getText();
        if (!sql.isEmpty()) {
            String formatted = SqlFormatterUtil.formatForType(sql,
                    currentConnection != null ? currentConnection.getDbType() : null);
            sqlEditor.replaceText(formatted);
            statusLabel.setText(I18N.get("msg.sql_formatted"));
        }
    }

    private void refreshTree() {
        logger.info("刷新树...");
        setupTreeView();
    }
    
    private void exportToCSV() {
        if (lastResult == null) {
            showAlert(I18N.get("title.tip"), I18N.get("msg.no_export_data"));
            return;
        }
        File file = chooseFile("CSV", "*.csv");
        if (file != null) {
            try {
                ExportUtil.exportToCSV(lastResult, file.getAbsolutePath());
                statusLabel.setText(I18N.get("msg.exported_to", file.getName()));
            } catch (Exception e) {
                showAlert(I18N.get("title.error"), I18N.get("msg.export_failed", e.getMessage()));
            }
        }
    }

    private void exportToJSON() {
        if (lastResult == null) {
            showAlert(I18N.get("title.tip"), I18N.get("msg.no_export_data"));
            return;
        }
        File file = chooseFile("JSON", "*.json");
        if (file != null) {
            try {
                ExportUtil.exportToJSON(lastResult, file.getAbsolutePath());
                statusLabel.setText(I18N.get("msg.exported_to", file.getName()));
            } catch (Exception e) {
                showAlert(I18N.get("title.error"), I18N.get("msg.export_failed", e.getMessage()));
            }
        }
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.F5) {
            executeSQL();
            event.consume();
        }
    }

    private void toggleLineComment() {
        int start = sqlEditor.getSelection().getStart();
        int end = sqlEditor.getSelection().getEnd();
        String text = sqlEditor.getText();
        int lineStart = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        int lineEnd = end > 0 ? text.indexOf('\n', end) : -1;
        if (lineEnd < 0) lineEnd = text.length();
        String block = text.substring(lineStart, lineEnd);
        String[] lines = block.split("\\n", -1);
        boolean uncomment = java.util.Arrays.stream(lines).filter(s -> !s.trim().isEmpty())
                .allMatch(s -> s.trim().startsWith("--"));
        StringBuilder replacement = new StringBuilder();
        for (int i=0;i<lines.length;i++) {
            String l=lines[i];
            if (uncomment) l=l.replaceFirst("^(\\s*)-- ?", "$1");
            else if (!l.trim().isEmpty()) l=l.replaceFirst("^(\\s*)", "$1-- ");
            replacement.append(l); if(i<lines.length-1) replacement.append('\n');
        }
        sqlEditor.replaceText(lineStart, lineEnd, replacement.toString());
    }

    private void toggleBlockComment() {
        String selected = sqlEditor.getSelectedText();
        if (selected == null || selected.isEmpty()) return;
        int start = sqlEditor.getSelection().getStart(), end = sqlEditor.getSelection().getEnd();
        if (selected.trim().startsWith("/*") && selected.trim().endsWith("*/")) {
            int a = selected.indexOf("/*") + 2, b = selected.lastIndexOf("*/");
            sqlEditor.replaceText(start, end, selected.substring(a, b).trim());
        } else {
            sqlEditor.replaceText(start, end, "/* " + selected + " */");
        }
    }

    private void loadSqlHistory() {
        try {
            if (historyFile != null && Files.exists(historyFile)) {
                List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
                if (!lines.isEmpty()) sqlEditor.replaceText(new String(Base64.getDecoder().decode(lines.get(0)), StandardCharsets.UTF_8));
                sqlHistory.clear();
                for (int i=1;i<lines.size();i++) sqlHistory.add(new String(Base64.getDecoder().decode(lines.get(i)), StandardCharsets.UTF_8));
            }
        } catch (Exception e) { logger.debug("Unable to load SQL history", e); }
    }

    private void saveSqlHistory(String current) {
        if (historyFile == null || current == null || current.equals(I18N.get("placeholder.sql"))) return;
        try {
            Files.createDirectories(historyFile.getParent());
            List<String> lines = new ArrayList<>();
            lines.add(Base64.getEncoder().encodeToString(current.getBytes(StandardCharsets.UTF_8)));
            for (String item : sqlHistory) lines.add(Base64.getEncoder().encodeToString(item.getBytes(StandardCharsets.UTF_8)));
            Files.write(historyFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) { logger.debug("Unable to save SQL history", e); }
    }

    private void recordSql(String sql) {
        sqlHistory.remove(sql); sqlHistory.add(0, sql);
        while (sqlHistory.size() > 100) sqlHistory.remove(sqlHistory.size()-1);
        saveSqlHistory(sqlEditor.getText());
    }

    private void showCompletionMenu() {
        if (completionMenu == null) completionMenu = new ContextMenu();
        completionMenu.getItems().clear();
        String sql = sqlEditor.getText();
        int caret = sqlEditor.getCaretPosition();
        String token = SqlCompletionUtil.currentToken(sql, caret).toUpperCase();
        List<String> suggestions = new java.util.ArrayList<>();
        for (String keyword : SqlCompletionUtil.keywords()) {
            if (token.isEmpty() || keyword.startsWith(token)) suggestions.add(keyword);
        }
        if (currentConnection != null) {
            try {
                java.sql.Connection conn = dbService.getConnection(currentConnection.getName());
                if (conn != null && !conn.isClosed()) {
                    for (String table : dbService.getTables(conn)) {
                        if (token.isEmpty() || table.toUpperCase().startsWith(token)) suggestions.add(table);
                    }
                }
            } catch (Exception e) {
                logger.debug("Unable to load SQL completion tables", e);
            }
            // 即使连接当前未打开，也从左侧已加载的树节点提供表名联想。
            if (connectionTree != null) {
                TreeItem<SqlTreeItem> root = findTreeItem(connectionTree.getRoot(), currentConnection);
                if (root != null) collectTableNames(root, suggestions, token);
            }
        }
        suggestions.stream().distinct().limit(30).forEach(value -> {
            MenuItem item = new MenuItem(value);
            item.setOnAction(e -> {
                int position = sqlEditor.getCaretPosition();
                String updated = SqlCompletionUtil.replaceCurrentToken(sqlEditor.getText(), position, value);
                sqlEditor.replaceText(updated);
                sqlEditor.moveTo(position - token.length() + value.length());
                sqlEditor.requestFocus();
            });
            completionMenu.getItems().add(item);
        });
        if (!completionMenu.getItems().isEmpty()) {
            completionMenu.show(sqlEditor, Side.BOTTOM, 0, 0);
        }
    }

    private TreeItem<SqlTreeItem> findTreeItem(TreeItem<SqlTreeItem> node, SqlTreeItem target) {
        if (node == null) return null;
        if (node.getValue() == target || (node.getValue() != null && target.getId() != null
                && target.getId().equals(node.getValue().getId()))) return node;
        for (TreeItem<SqlTreeItem> child : node.getChildren()) {
            TreeItem<SqlTreeItem> found = findTreeItem(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private void collectTableNames(TreeItem<SqlTreeItem> node, List<String> suggestions, String token) {
        for (TreeItem<SqlTreeItem> child : node.getChildren()) {
            SqlTreeItem value = child.getValue();
            if (value != null && NodeType.TABLE.name().equals(value.getNodeType())
                    && (token.isEmpty() || value.getName().toUpperCase().startsWith(token))) {
                suggestions.add(value.getName());
            }
            collectTableNames(child, suggestions, token);
        }
    }

    private File chooseFile(String desc, String ext) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("title.save_as", desc));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext));
        if (mainSplitPane != null && mainSplitPane.getScene() != null) {
             return chooser.showSaveDialog(mainSplitPane.getScene().getWindow());
        }
        return null;
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void dispose() {
        DatabaseService serviceToClose = dbService;
        dbService = null;
        if (serviceToClose != null) {
            serviceToClose.disconnectAll();
        }
    }
}
