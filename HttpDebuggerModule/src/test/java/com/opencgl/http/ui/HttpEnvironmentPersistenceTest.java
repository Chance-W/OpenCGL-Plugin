package com.opencgl.http.ui;

import com.opencgl.base.model.Base;
import com.opencgl.http.model.HttpTreeItem;
import com.opencgl.http.service.*;
import javafx.scene.Scene;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HttpEnvironmentPersistenceTest {
    @BeforeAll static void start() throws Exception {
        assertTrue(System.getProperty("user.home").endsWith("test-home"));
        Files.createDirectories(Path.of(Base.DB_PATH));
        HttpDebuggerLayoutTest.start();
    }
    private static String stored(Long id) {
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + Base.DB_PATH + "data.db");
             var ps = conn.prepareStatement("SELECT environment_name FROM http_tree_item WHERE id=?")) {
            ps.setLong(1, id);
            try (var row = ps.executeQuery()) { assertTrue(row.next()); return row.getString(1); }
        } catch (SQLException e) { throw new AssertionError(e); }
    }
    @Test void selectionPersistsOnlyEnvironmentAndDeletedEnvironmentNeverReattaches() throws Exception {
        HttpDebuggerLayoutTest.fx(() -> {
            var service = new HttpTreeService();
            var env = new EnvironmentService();
            env.updateEnvironment("测试环境", Map.of("host", "test.local"));
            var a = service.createRequest("env A", "GET", "http://localhost/a", 0L);
            var b = service.createRequest("env B", "GET", "http://localhost/b", 0L);
            var c = HttpUiFixture.load();
            new Scene(c.mainStackPane);
            try {
                HttpUiFixture.set(c, "treeService", service);
                HttpUiFixture.set(c, "environmentService", env);
                HttpUiFixture.call(c, "setupEnvironmentUI");
                HttpUiFixture.call(c, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, a);
                c.urlField.setText("http://localhost/unsaved");
                c.envComboBox.setValue("测试环境");
                assertEquals("测试环境", stored(a.getId()));
                assertEquals("http://localhost/a", service.queryById(a.getId()).getUrl(), "Environment selection must not save URL drafts");
                HttpUiFixture.call(c, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, b);
                assertEquals("None", c.envComboBox.getValue()); assertNull(stored(b.getId()));
                c.envComboBox.setValue("测试环境");
                HttpUiFixture.call(c, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, a);
                assertEquals("测试环境", c.envComboBox.getValue());
                assertEquals("test.local", env.substitute("{{host}}"));
                var folder = new HttpTreeItem(); folder.setId(0L); folder.setIsLeaf(false); folder.setNodeType("FOLDER");
                HttpUiFixture.call(c, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, folder);
                assertSame(a, HttpUiFixture.get(c, "currentTreeItem"));
                assertTrue(c.requestPanel.isVisible(), "Folder selection preserves the displayed request");
                assertEquals("测试环境", c.envComboBox.getValue());
                assertEquals("test.local", env.substitute("{{host}}"));
                assertEquals("测试环境", stored(a.getId()));
                HttpUiFixture.call(c, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, a);
                assertEquals("测试环境", c.envComboBox.getValue());
                // A new plugin instance reads the stored association.
                var reopened = HttpUiFixture.load();
                try {
                    HttpUiFixture.set(reopened, "treeService", new HttpTreeService());
                    HttpUiFixture.set(reopened, "environmentService", new EnvironmentService());
                    HttpUiFixture.call(reopened, "setupEnvironmentUI");
                    HttpUiFixture.call(reopened, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, a);
                    assertEquals("测试环境", reopened.envComboBox.getValue());
                } finally { reopened.dispose(); }
                // The environment dialog has a separate service instance.
                new EnvironmentService().deleteEnvironment("测试环境");
                HttpUiFixture.call(c, "refreshEnvCombo");
                assertNull(stored(a.getId())); assertEquals("None", c.envComboBox.getValue());
                assertNull(stored(b.getId()), "Deletion clears every associated request");
                assertEquals("None", env.getCurrentEnvName()); assertEquals("{{host}}", env.substitute("{{host}}"));
                env.updateEnvironment("测试环境", Map.of("host", "another.local"));
                service.update(a); // A stale tree DTO must not restore a deleted association.
                HttpUiFixture.call(c, "refreshEnvCombo");
                HttpUiFixture.call(c, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, a);
                assertNull(stored(a.getId())); assertEquals("None", c.envComboBox.getValue());
                c.envComboBox.setValue("测试环境"); c.envComboBox.setValue("None");
                assertNull(stored(a.getId()));
            } finally { c.dispose(); service.delete(a); service.delete(b); env.deleteEnvironment("测试环境"); }
        });
    }

    @Test void failedEnvironmentDeletionRollsBackAssociationsAndVariables() throws Exception {
        var service = new HttpTreeService();
        var env = new EnvironmentService();
        env.updateEnvironment("rollback", Map.of("key", "value"));
        var a = service.createRequest("rollback", "GET", "http://localhost/a", 0L);
        service.updateEnvironment(a.getId(), "rollback");
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + Base.DB_PATH + "data.db");
             var sql = conn.createStatement()) {
            sql.executeUpdate("CREATE TRIGGER block_test_env_delete BEFORE DELETE ON http_environment WHEN old.env_name='rollback' BEGIN SELECT RAISE(ABORT,'test failure'); END");
            try {
                assertThrows(IllegalStateException.class, () -> env.deleteEnvironment("rollback"));
                assertEquals("rollback", stored(a.getId()));
                assertEquals("value", env.getEnvironment("rollback").get("key"));
                assertTrue(env.getEnvironmentNames().contains("rollback"));
            } finally {
                sql.executeUpdate("DROP TRIGGER block_test_env_delete");
                service.delete(a); env.deleteEnvironment("rollback");
            }
        }
    }
}
