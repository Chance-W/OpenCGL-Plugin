package com.opencgl.solace.persistence;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import com.opencgl.solace.model.SolaceWorkspaceKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SqliteSolaceConfigurationRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void createsRequiredTablesOnFirstUse() throws Exception {
        Path database = tempDir.resolve("data.db");

        new SqliteSolaceConfigurationRepository(database, protector());

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.prepareStatement(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name IN (?, ?) ORDER BY name")) {
            statement.setString(1, SqliteSolaceConfigurationRepository.CONNECTION_TABLE);
            statement.setString(2, SqliteSolaceConfigurationRepository.TREE_TABLE);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(SqliteSolaceConfigurationRepository.CONNECTION_TABLE, result.getString(1));
                assertTrue(result.next());
                assertEquals(SqliteSolaceConfigurationRepository.TREE_TABLE, result.getString(1));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void roundTripsHierarchyConnectionAndEncryptedPassword() throws Exception {
        Path database = tempDir.resolve("data.db");
        var repository = new SqliteSolaceConfigurationRepository(database, protector());
        SolaceTreeNode group = node(1L, 0L, "开发环境", SolaceNodeType.DIRECTORY, null);
        group.setVariables(Map.of("region", "cn"));
        SolaceTreeNode connectionNode = node(2L, 1L, "公共连接", SolaceNodeType.CONNECTION, "conn-1");
        SolaceTreeNode sender = node(3L, 2L, "订单发送", SolaceNodeType.SEND, "conn-1");
        sender.setSettings(Map.of("destination", "orders/new"));
        SolaceConnectionConfig config = new SolaceConnectionConfig();
        config.setHost("tcps://broker.example:55443");
        config.setMessageVpn("default");
        config.setUsername("user");
        config.setPassword("secret");
        config.setRememberPassword(true);
        SolaceWorkspace workspace = new SolaceWorkspace();
        workspace.setNodes(List.of(group, connectionNode, sender));
        workspace.setConnections(Map.of("conn-1", config));
        AtomicInteger notifications = new AtomicInteger();

        try (AutoCloseable ignored = repository.addChangeListener(notifications::incrementAndGet)) {
            repository.save(workspace);
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.prepareStatement(
                 "SELECT CONFIG_JSON FROM " + SqliteSolaceConfigurationRepository.CONNECTION_TABLE +
                     " WHERE CONNECTION_ID = ?")) {
            statement.setString(1, "conn-1");
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                String stored = result.getString(1);
                assertFalse(stored.contains("\"secret\""));
                assertTrue(stored.contains("ENC(secret)"));
            }
        }

        SolaceWorkspace restored = repository.load();
        assertEquals(List.of("开发环境", "公共连接", "订单发送"),
            restored.getNodes().stream().map(SolaceTreeNode::getName).toList());
        assertEquals("cn", restored.getNodes().getFirst().getVariables().get("region"));
        assertEquals("orders/new", restored.getNodes().get(2).getSettings().get("destination"));
        assertEquals("secret", restored.getConnections().get("conn-1").getPassword());
        assertEquals(1, notifications.get());
    }

    @Test
    void replacingWorkspaceRemovesDeletedRowsWithoutDuplicatingNodes() throws Exception {
        var repository = new SqliteSolaceConfigurationRepository(tempDir.resolve("data.db"), protector());
        SolaceWorkspace initial = new SolaceWorkspace();
        initial.setNodes(List.of(
            node(1L, 0L, "保留", SolaceNodeType.DIRECTORY, null),
            node(2L, 1L, "删除", SolaceNodeType.DIRECTORY, null)));
        repository.save(initial);
        SolaceWorkspace updated = new SolaceWorkspace();
        updated.setNodes(List.of(node(1L, 0L, "已更新", SolaceNodeType.DIRECTORY, null)));

        repository.save(updated);

        SolaceWorkspace restored = repository.load();
        assertEquals(1, restored.getNodes().size());
        assertEquals("已更新", restored.getNodes().getFirst().getName());
    }

    @Test
    void senderAndListenerUseIndependentTablesInTheSameDatabase() throws Exception {
        Path database = tempDir.resolve("data.db");
        var sender = SqliteSolaceConfigurationRepository.forWorkspace(database, protector(), SolaceWorkspaceKind.SEND);
        var listener = SqliteSolaceConfigurationRepository.forWorkspace(database, protector(), SolaceWorkspaceKind.LISTEN);
        SolaceWorkspace sendWorkspace = new SolaceWorkspace();
        sendWorkspace.setNodes(List.of(node(1L, 0L, "发送树", SolaceNodeType.DIRECTORY, null)));
        SolaceWorkspace listenWorkspace = new SolaceWorkspace();
        listenWorkspace.setNodes(List.of(node(1L, 0L, "监听树", SolaceNodeType.DIRECTORY, null)));

        sender.save(sendWorkspace);
        listener.save(listenWorkspace);

        assertEquals("发送树", sender.load().getNodes().getFirst().getName());
        assertEquals("监听树", listener.load().getNodes().getFirst().getName());
    }

    private SolaceTreeNode node(long id, long parentId, String name, SolaceNodeType type, String connectionId) {
        SolaceTreeNode node = new SolaceTreeNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setName(name);
        node.setNodeType(type);
        node.setConnectionId(connectionId);
        node.setIsLeaf(type == SolaceNodeType.SEND || type == SolaceNodeType.LISTEN);
        node.setSortOrder((int) id);
        return node;
    }

    private SecretProtector protector() {
        return new SecretProtector() {
            @Override public String protect(String plainText) { return "ENC(" + plainText + ")"; }
            @Override public String unprotect(String protectedText) {
                return protectedText.substring(4, protectedText.length() - 1);
            }
        };
    }
}
