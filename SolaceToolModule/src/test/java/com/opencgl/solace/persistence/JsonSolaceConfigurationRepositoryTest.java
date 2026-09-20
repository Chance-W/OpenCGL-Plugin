package com.opencgl.solace.persistence;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JsonSolaceConfigurationRepositoryTest {

    @TempDir Path tempDir;

    @Test
    void returnsEmptyWorkspaceWhenFileDoesNotExist() throws Exception {
        var repository = repository();

        SolaceWorkspace workspace = repository.load();

        assertTrue(workspace.getNodes().isEmpty());
        assertTrue(workspace.getConnections().isEmpty());
    }

    @Test
    void roundTripsTreeConnectionsAndProtectedRememberedPassword() throws Exception {
        var repository = repository();
        var node = new SolaceTreeNode();
        node.setId(1L);
        node.setName("测试环境");
        node.setNodeType(SolaceNodeType.DIRECTORY);
        node.setVariables(Map.of("topic", "orders/new"));
        var connection = new SolaceConnectionConfig();
        connection.setHost("tcp://localhost:55555");
        connection.setMessageVpn("default");
        connection.setUsername("user");
        connection.setPassword("secret");
        connection.setRememberPassword(true);
        var workspace = new SolaceWorkspace();
        workspace.setNodes(List.of(node));
        workspace.setConnections(Map.of("conn-1", connection));

        AtomicInteger notifications = new AtomicInteger();
        try (AutoCloseable ignored = repository.addChangeListener(notifications::incrementAndGet)) {
            repository.save(workspace);
        }

        String stored = Files.readString(tempDir.resolve("workspace.json"));
        assertFalse(stored.contains("\"secret\""));
        assertTrue(stored.contains("ENC(secret)"));
        SolaceWorkspace restored = repository.load();
        assertEquals("测试环境", restored.getNodes().getFirst().getName());
        assertEquals("orders/new", restored.getNodes().getFirst().getVariables().get("topic"));
        assertEquals("secret", restored.getConnections().get("conn-1").getPassword());
        assertEquals(1, notifications.get());
    }

    @Test
    void doesNotPersistPasswordUnlessRememberPasswordIsEnabled() throws Exception {
        var repository = repository();
        var connection = new SolaceConnectionConfig();
        connection.setPassword("must-not-be-written");
        connection.setRememberPassword(false);
        var workspace = new SolaceWorkspace();
        workspace.setConnections(Map.of("conn-1", connection));

        repository.save(workspace);

        String stored = Files.readString(tempDir.resolve("workspace.json"));
        assertFalse(stored.contains("must-not-be-written"));
        assertNull(repository.load().getConnections().get("conn-1").getPassword());
    }

    private JsonSolaceConfigurationRepository repository() {
        SecretProtector protector = new SecretProtector() {
            @Override public String protect(String plainText) { return "ENC(" + plainText + ")"; }
            @Override public String unprotect(String protectedText) {
                return protectedText.substring(4, protectedText.length() - 1);
            }
        };
        return new JsonSolaceConfigurationRepository(tempDir.resolve("workspace.json"), protector);
    }
}
