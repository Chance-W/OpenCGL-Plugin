package com.opencgl.http.service;

import com.opencgl.base.model.Base;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTransferTest {
    private EnvironmentService service;
    private final String name = "transfer-" + UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        assertTrue(System.getProperty("user.home").endsWith("test-home"));
        Files.createDirectories(Path.of(Base.DB_PATH));
        service = new EnvironmentService();
        service.updateEnvironment(name, Map.of("中文", "你好\n世界😀", "empty", ""));
    }
    @AfterEach void cleanup() {
        for (String n : service.getEnvironmentNames()) if (n.startsWith(name)) service.deleteEnvironment(n);
    }
    @Test void copyPersistsIndependentVariablesAndAllocatesDistinctNames() {
        String first = service.copyEnvironment(name);
        String second = service.copyEnvironment(name);
        assertEquals(name + " 副本", first);
        assertEquals(name + " 副本 (2)", second);
        service.updateEnvironment(first, Map.of("中文", "修改"));
        assertEquals("你好\n世界😀", new EnvironmentService().getEnvironment(name).get("中文"));
        assertEquals("你好\n世界😀", new EnvironmentService().getEnvironment(second).get("中文"));
    }
    @Test void exportImportRoundTripPreservesUnicodeAndDoesNotOverwrite() {
        String json = service.exportEnvironment(name);
        assertTrue(json.contains("你好"));
        String imported = service.importEnvironment(json);
        assertNotEquals(name, imported);
        assertEquals(service.getEnvironment(name), new EnvironmentService().getEnvironment(imported));
    }
    @Test void transferOfFormSnapshotDoesNotRewriteSource() {
        String copy = service.copyEnvironment(name, Map.of("draft", "未保存"));
        assertEquals(Map.of("draft", "未保存"), service.getEnvironment(copy));
        String json = service.exportEnvironment(name, Map.of("draft", "未保存"));
        assertTrue(json.contains("未保存"));
        assertEquals("你好\n世界😀", service.getEnvironment(name).get("中文"));
    }
    @Test void malformedImportDoesNotWriteAnything() {
        var before = service.getEnvironmentNames();
        for (String json : List.of("not json", "[]", "{}",
            "{\"format\":\"opencgl-http-environment\",\"version\":1,\"name\":\"None\",\"variables\":{}}",
            "{\"format\":\"opencgl-http-environment\",\"version\":1,\"name\":\"bad\",\"variables\":{\"x\":123}}")) {
            assertThrows(IllegalArgumentException.class, () -> service.importEnvironment(json));
            assertEquals(before, service.getEnvironmentNames());
        }
        assertThrows(IllegalArgumentException.class, () -> service.copyEnvironment("None"));
        assertThrows(IllegalArgumentException.class, () -> service.exportEnvironment("missing"));
    }
    @Test void failedVariableInsertRollsBackTheNewEnvironment() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + Base.DB_PATH + "data.db");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TRIGGER reject_transfer BEFORE INSERT ON http_environment_var WHEN NEW.var_key='reject-transfer' BEGIN SELECT RAISE(ABORT,'test failure'); END");
            try {
                var vars = new LinkedHashMap<String, String>();
                vars.put("valid", "first"); vars.put("reject-transfer", "second");
                var repository = new com.opencgl.http.repository.impl.HttpEnvironmentRepositoryImpl();
                assertThrows(IllegalStateException.class, () -> repository.createEnvironment(name + " failed", vars));
                assertFalse(service.getEnvironmentNames().contains(name + " failed"));
                assertTrue(service.getEnvironment(name + " failed").isEmpty());
                assertEquals("你好\n世界😀", service.getEnvironment(name).get("中文"));
            } finally { sql.execute("DROP TRIGGER reject_transfer"); }
        }
    }
}
