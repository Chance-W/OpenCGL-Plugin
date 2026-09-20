package com.opencgl.solace.persistence;

import com.alibaba.fastjson2.JSON;
import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import com.opencgl.solace.model.SolaceWorkspaceKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SQLite-backed Solace workspace repository.
 *
 * <p>The tree is stored as individual rows so it can be inspected and upgraded like the Dubbo
 * collection tree. Connection details remain a JSON document per connection to keep future Solace
 * properties backwards-compatible. Both tables are replaced in one transaction on each save.</p>
 */
public final class SqliteSolaceConfigurationRepository implements SolaceConfigurationRepository {
    public static final String TREE_TABLE = "SOLACE_TREE_ITEM";
    public static final String CONNECTION_TABLE = "SOLACE_CONNECTION_CONFIG";

    private final Path database;
    private final SecretProtector secretProtector;
    private final String treeTable;
    private final String connectionTable;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public SqliteSolaceConfigurationRepository(Path database, SecretProtector secretProtector) throws IOException {
        this(database, secretProtector, TREE_TABLE, CONNECTION_TABLE);
    }

    private SqliteSolaceConfigurationRepository(Path database, SecretProtector secretProtector,
                                                 String treeTable, String connectionTable) throws IOException {
        this.database = database.toAbsolutePath().normalize();
        this.secretProtector = secretProtector;
        this.treeTable = treeTable;
        this.connectionTable = connectionTable;
        initialiseTables();
    }

    public static SqliteSolaceConfigurationRepository forWorkspace(Path database, SecretProtector secretProtector,
                                                                    SolaceWorkspaceKind kind) throws IOException {
        String prefix = kind == SolaceWorkspaceKind.SEND ? "SOLACE_SEND" : "SOLACE_LISTEN";
        return new SqliteSolaceConfigurationRepository(database, secretProtector,
            prefix + "_TREE_ITEM", prefix + "_CONNECTION_CONFIG");
    }

    @Override
    public synchronized SolaceWorkspace load() throws IOException {
        SolaceWorkspace workspace = new SolaceWorkspace();
        Map<String, SolaceConnectionConfig> connections = new LinkedHashMap<>();
        try (Connection connection = open();
             PreparedStatement nodeQuery = connection.prepareStatement(
                 "SELECT ID, PARENT_ID, NAME, IS_LEAF, NODE_TYPE, CONNECTION_ID, VARIABLES_JSON, " +
                     "SETTINGS_JSON, SORT_ORDER FROM " + treeTable + " ORDER BY SORT_ORDER, ID");
             ResultSet nodes = nodeQuery.executeQuery()) {
            while (nodes.next()) workspace.getNodes().add(readNode(nodes));

            try (PreparedStatement connectionQuery = connection.prepareStatement(
                     "SELECT CONNECTION_ID, CONFIG_JSON FROM " + connectionTable + " ORDER BY CONNECTION_ID");
                 ResultSet rows = connectionQuery.executeQuery()) {
                while (rows.next()) {
                    SolaceConnectionConfig config = JSON.parseObject(rows.getString("CONFIG_JSON"),
                        SolaceConnectionConfig.class);
                    if (config == null) config = new SolaceConnectionConfig();
                    restoreSecret(config);
                    connections.put(rows.getString("CONNECTION_ID"), config);
                }
            }
        } catch (SQLException | RuntimeException error) {
            throw new IOException("加载 Solace SQLite 配置失败", error);
        }
        workspace.setConnections(connections);
        return workspace;
    }

    @Override
    public synchronized void save(SolaceWorkspace workspace) throws IOException {
        SolaceWorkspace source = workspace == null ? new SolaceWorkspace() : workspace;
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement clearNodes = connection.prepareStatement("DELETE FROM " + treeTable);
                     PreparedStatement clearConnections = connection.prepareStatement("DELETE FROM " + connectionTable)) {
                    clearNodes.executeUpdate();
                    clearConnections.executeUpdate();
                }
                writeNodes(connection, source.getNodes());
                writeConnections(connection, source.getConnections());
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (SQLException | RuntimeException error) {
            throw new IOException("保存 Solace SQLite 配置失败", error);
        }
        notifyListeners();
    }

    @Override
    public AutoCloseable addChangeListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public boolean isEmpty() throws IOException {
        try (Connection connection = open();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT (SELECT COUNT(*) FROM " + treeTable + ") + " +
                     "(SELECT COUNT(*) FROM " + connectionTable + ")");
             ResultSet result = query.executeQuery()) {
            return !result.next() || result.getLong(1) == 0L;
        } catch (SQLException error) {
            throw new IOException("检查 Solace SQLite 配置失败", error);
        }
    }

    private void initialiseTables() throws IOException {
        try {
            Path parent = database.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Connection connection = open();
                 PreparedStatement tree = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + treeTable + " (" +
                         "ID INTEGER PRIMARY KEY," +
                         "PARENT_ID INTEGER," +
                         "NAME VARCHAR(150) NOT NULL," +
                         "IS_LEAF BOOLEAN NOT NULL," +
                         "NODE_TYPE VARCHAR(32) NOT NULL," +
                         "CONNECTION_ID VARCHAR(80)," +
                         "VARIABLES_JSON TEXT," +
                         "SETTINGS_JSON TEXT," +
                         "SORT_ORDER INTEGER DEFAULT 0" +
                         ")");
                 PreparedStatement connections = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + connectionTable + " (" +
                         "CONNECTION_ID VARCHAR(80) PRIMARY KEY," +
                         "CONFIG_JSON TEXT NOT NULL" +
                         ")")) {
                tree.executeUpdate();
                connections.executeUpdate();
            }
        } catch (SQLException error) {
            throw new IOException("检测/创建 Solace SQLite 表失败", error);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    private SolaceTreeNode readNode(ResultSet row) throws SQLException {
        SolaceTreeNode node = new SolaceTreeNode();
        node.setId(row.getLong("ID"));
        long parentId = row.getLong("PARENT_ID");
        node.setParentId(row.wasNull() ? null : parentId);
        node.setName(row.getString("NAME"));
        node.setIsLeaf(row.getBoolean("IS_LEAF"));
        node.setNodeType(SolaceNodeType.valueOf(row.getString("NODE_TYPE")));
        node.setConnectionId(row.getString("CONNECTION_ID"));
        node.setVariables(readStringMap(row.getString("VARIABLES_JSON")));
        node.setSettings(readStringMap(row.getString("SETTINGS_JSON")));
        int sortOrder = row.getInt("SORT_ORDER");
        node.setSortOrder(row.wasNull() ? null : sortOrder);
        return node;
    }

    private void writeNodes(Connection connection, List<SolaceTreeNode> nodes) throws SQLException {
        String sql = "INSERT INTO " + treeTable +
            " (ID, PARENT_ID, NAME, IS_LEAF, NODE_TYPE, CONNECTION_ID, VARIABLES_JSON, SETTINGS_JSON, SORT_ORDER)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (SolaceTreeNode node : nodes) {
                insert.setLong(1, node.getId());
                if (node.getParentId() == null) insert.setObject(2, null); else insert.setLong(2, node.getParentId());
                insert.setString(3, node.getName());
                insert.setBoolean(4, Boolean.TRUE.equals(node.getIsLeaf()));
                insert.setString(5, node.getNodeType().name());
                insert.setString(6, node.getConnectionId());
                insert.setString(7, JSON.toJSONString(node.getVariables()));
                insert.setString(8, JSON.toJSONString(node.getSettings()));
                if (node.getSortOrder() == null) insert.setObject(9, null); else insert.setInt(9, node.getSortOrder());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void writeConnections(Connection connection, Map<String, SolaceConnectionConfig> connections)
        throws SQLException {
        String sql = "INSERT INTO " + connectionTable + " (CONNECTION_ID, CONFIG_JSON) VALUES (?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (Map.Entry<String, SolaceConnectionConfig> entry : connections.entrySet()) {
                SolaceConnectionConfig persisted = JSON.parseObject(JSON.toJSONString(entry.getValue()),
                    SolaceConnectionConfig.class);
                prepareSecretForStorage(persisted);
                insert.setString(1, entry.getKey());
                insert.setString(2, JSON.toJSONString(persisted));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        Map<String, String> result = JSON.parseObject(json, LinkedHashMap.class);
        return result == null ? new LinkedHashMap<>() : result;
    }

    private void prepareSecretForStorage(SolaceConnectionConfig connection) {
        String password = connection.getPassword();
        if (!connection.isRememberPassword() || password == null || password.isEmpty()) connection.setPassword(null);
        else connection.setPassword(secretProtector.protect(password));
    }

    private void restoreSecret(SolaceConnectionConfig connection) {
        String password = connection.getPassword();
        if (connection.isRememberPassword() && password != null && !password.isEmpty()) {
            connection.setPassword(secretProtector.unprotect(password));
        } else connection.setPassword(null);
    }

    private void rollback(Connection connection, Exception cause) throws SQLException {
        try { connection.rollback(); }
        catch (SQLException rollbackError) { cause.addSuppressed(rollbackError); }
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try { listener.run(); }
            catch (RuntimeException ignored) { }
        }
    }
}
